package com.example.testqwencli.gateway.repository.postgres;

import com.example.testqwencli.gateway.config.ExternalGatewayPostgresProperties;
import com.example.testqwencli.gateway.model.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.AsyncPriority;
import com.example.testqwencli.gateway.model.async.AsyncSubmitResult;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.model.async.AsyncTaskRepositoryStats;
import com.example.testqwencli.gateway.model.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.AsyncTaskUpdateResult;
import com.example.testqwencli.gateway.model.async.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.SyncRequestTrace;
import com.example.testqwencli.gateway.model.async.TaskError;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.function.Supplier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class PostgresAsyncTaskRepository implements AsyncTaskRepository {

	private static final String ASYNC_DELIVERY_MODE_FILTER = "delivery_mode IN ('CALLBACK', 'POLLING')";

	private final NamedParameterJdbcTemplate jdbc;
	private final TransactionTemplate transactionTemplate;
	private final PostgresTableNames tables;
	private final PostgresJsonMapper jsonMapper;

	public PostgresAsyncTaskRepository(
			NamedParameterJdbcTemplate jdbc,
			TransactionTemplate transactionTemplate,
			ExternalGatewayPostgresProperties postgresProperties,
			ObjectMapper objectMapper
	) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
		this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
		this.tables = new PostgresTableNames(Objects.requireNonNull(postgresProperties,
				"postgresProperties must not be null").schema());
		this.jsonMapper = new PostgresJsonMapper(objectMapper);
	}

	@Override
	public AsyncSubmitResult submit(ExternalAsyncRequest request, int maxAttempts, Instant now) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(request.payload(), "request payload must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}

		return transactionTemplate.execute(status -> {
			Optional<StoredAsyncTask> inserted = insertTask(request, maxAttempts, now);
			if (inserted.isPresent()) {
				return AsyncSubmitResult.submitted(inserted.orElseThrow().task(), false);
			}

			StoredAsyncTask existing = findStoredByIdempotencyKey(request.clientService(), request.externalId())
					.orElseThrow();
			List<String> conflictingFields = conflictingFields(existing, request);
			if (!conflictingFields.isEmpty()) {
				return AsyncSubmitResult.idempotencyConflict(existing.task().taskId(), conflictingFields);
			}
			return AsyncSubmitResult.submitted(existing.task(), true);
		});
	}

	@Override
	public Optional<AsyncTask> findByTaskId(long taskId, Optional<String> clientService) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		return findStoredByTaskId(taskId, clientService, false).map(StoredAsyncTask::task);
	}

	@Override
	public Optional<AsyncTask> findByExternalId(UUID externalId, Optional<String> clientService) {
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		MapSqlParameterSource params = new MapSqlParameterSource("externalId", externalId);
		String sql = baseSelect() + "\nWHERE external_id = :externalId"
				+ "\n  AND " + ASYNC_DELIVERY_MODE_FILTER;
		if (clientService.isPresent()) {
			sql += "\n  AND client_service = :clientService";
			params.addValue("clientService", clientService.orElseThrow());
		}
		sql += "\nORDER BY id ASC\nLIMIT 1";
		return queryStored(sql, params).stream().findFirst().map(StoredAsyncTask::task);
	}

	@Override
	public AsyncTaskUpdateResult cancel(long taskId, Optional<String> clientService, Instant now) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(now, "now must not be null");
		return transactionTemplate.execute(status -> {
			Optional<StoredAsyncTask> current = findStoredByTaskId(taskId, clientService, true);
			if (current.isEmpty()) {
				return AsyncTaskUpdateResult.notFound();
			}
			AsyncTask task = current.orElseThrow().task();
			if (task.status() == AsyncTaskStatus.CANCELLED) {
				return AsyncTaskUpdateResult.updated(task);
			}
			if (task.status() != AsyncTaskStatus.PENDING) {
				return AsyncTaskUpdateResult.conflict(task,
						"Async-задачу нельзя отменить в статусе " + task.status());
			}
			return AsyncTaskUpdateResult.updated(cancelLockedTask(taskId, now).orElseThrow());
		});
	}

	@Override
	public AsyncTaskUpdateResult retry(long taskId, Optional<String> clientService, Instant now) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(now, "now must not be null");
		return transactionTemplate.execute(status -> {
			Optional<StoredAsyncTask> current = findStoredByTaskId(taskId, clientService, true);
			if (current.isEmpty()) {
				return AsyncTaskUpdateResult.notFound();
			}
			AsyncTask task = current.orElseThrow().task();
			if ((task.status() == AsyncTaskStatus.FAILED || task.status() == AsyncTaskStatus.DEAD)
					&& task.retryable()) {
				return AsyncTaskUpdateResult.updated(retryLockedTask(taskId, now).orElseThrow());
			}
			return AsyncTaskUpdateResult.conflict(task,
					"Async-задачу нельзя вернуть в очередь в статусе " + task.status());
		});
	}

	@Override
	public AsyncTask recordSyncTrace(SyncRequestTrace trace) {
		Objects.requireNonNull(trace, "trace must not be null");
		String sql = """
				INSERT INTO %s (
				    external_id,
				    client_service,
				    priority,
				    priority_weight,
				    delivery_mode,
				    status,
				    callback_delivery_status,
				    payload,
				    result,
				    error,
				    attempts,
				    max_attempts,
				    created_at,
				    available_at,
				    started_at,
				    finished_at,
				    updated_at,
				    last_error,
				    retryable
				)
				VALUES (
				    :externalId,
				    :clientService,
				    'HIGH',
				    :priorityWeight,
				    'SYNC',
				    :status,
				    'NOT_REQUIRED',
				    CAST(:payload AS jsonb),
				    CAST(:result AS jsonb),
				    CAST(:error AS jsonb),
				    :attempts,
				    1,
				    :startedAt,
				    :startedAt,
				    :startedAt,
				    :finishedAt,
				    :finishedAt,
				    :lastError,
				    FALSE
				)
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns(null));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("externalId", trace.externalId())
				.addValue("clientService", trace.clientService())
				.addValue("priorityWeight", AsyncPriority.HIGH.weight())
				.addValue("status", trace.status().name())
				.addValue("payload", jsonMapper.write(trace.payload()))
				.addValue("result", jsonMapper.write(trace.result()))
				.addValue("error", jsonMapper.write(trace.error()))
				.addValue("attempts", trace.attempts())
				.addValue("startedAt", timestamp(trace.startedAt()))
				.addValue("finishedAt", timestamp(trace.finishedAt()))
				.addValue("lastError", trace.lastError());
		return queryStored(sql, params).stream().findFirst().orElseThrow().task();
	}

	@Override
	public List<AsyncTask> findRequestTracesByExternalId(UUID externalId, Optional<String> clientService) {
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		MapSqlParameterSource params = new MapSqlParameterSource("externalId", externalId);
		String sql = baseSelect() + "\nWHERE external_id = :externalId";
		if (clientService.isPresent()) {
			sql += "\n  AND client_service = :clientService";
			params.addValue("clientService", clientService.orElseThrow());
		}
		sql += "\nORDER BY id ASC";
		return queryStored(sql, params).stream().map(StoredAsyncTask::task).toList();
	}

	@Override
	public Optional<AsyncTaskClaim> claimNextPending(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				WITH candidate AS (
				    SELECT id
				    FROM %s
				    WHERE status = 'PENDING'
				      AND %s
				      AND available_at <= :now
				    ORDER BY priority_weight DESC,
				        available_at ASC,
				        id ASC
				    FOR UPDATE SKIP LOCKED
				    LIMIT 1
				)
				UPDATE %s task
				SET status = 'IN_PROGRESS',
				    attempts = task.attempts + 1,
				    started_at = :now,
				    updated_at = :now,
				    error = NULL,
				    result = NULL,
				    retryable = FALSE
				FROM candidate
				WHERE task.id = candidate.id
				RETURNING %s
				""".formatted(tables.requestQueue(), ASYNC_DELIVERY_MODE_FILTER, tables.requestQueue(),
				returningColumns("task"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("now", timestamp(now));
		return queryClaim(sql, params).stream().findFirst();
	}

	@Override
	public <T> T executeInProcessingTransaction(Supplier<T> action) {
		Objects.requireNonNull(action, "action must not be null");
		return transactionTemplate.execute(status -> action.get());
	}

	@Override
	public Optional<AsyncTask> complete(long taskId, Map<String, String> result, Instant now) {
		Objects.requireNonNull(result, "result must not be null");
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				UPDATE %s task
				SET status = 'DONE',
				    result = CAST(:result AS jsonb),
				    error = NULL,
				    finished_at = :now,
				    updated_at = :now,
				    last_error = NULL,
				    retryable = FALSE
				WHERE id = :taskId
				  AND status = 'IN_PROGRESS'
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns("task"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("taskId", taskId)
				.addValue("result", jsonMapper.write(result))
				.addValue("now", timestamp(now));
		return queryTask(sql, params);
	}

	@Override
	public Optional<AsyncTask> failTransient(long taskId, String message, Duration backoff, Instant now) {
		Objects.requireNonNull(backoff, "backoff must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (backoff.isNegative()) {
			throw new IllegalArgumentException("Backoff не должен быть отрицательным");
		}

		String normalizedMessage = normalizeErrorMessage(message);
		TaskError taskError = new TaskError("UPSTREAM_TRANSIENT_FAILURE", normalizedMessage, true);
		String sql = """
				UPDATE %s task
				SET status = CASE WHEN task.attempts < task.max_attempts THEN 'PENDING' ELSE 'DEAD' END,
				    callback_delivery_status = CASE
				        WHEN task.attempts < task.max_attempts AND task.delivery_mode = 'POLLING' THEN 'NOT_REQUIRED'
				        WHEN task.attempts < task.max_attempts THEN 'PENDING'
				        ELSE task.callback_delivery_status
				    END,
				    result = NULL,
				    error = CASE WHEN task.attempts < task.max_attempts THEN NULL ELSE CAST(:error AS jsonb) END,
				    available_at = CASE WHEN task.attempts < task.max_attempts THEN CAST(:availableAt AS timestamp with time zone) ELSE task.available_at END,
				    started_at = CASE WHEN task.attempts < task.max_attempts THEN NULL ELSE task.started_at END,
				    finished_at = CASE WHEN task.attempts < task.max_attempts THEN NULL ELSE CAST(:now AS timestamp with time zone) END,
				    updated_at = :now,
				    last_error = :message,
				    retryable = CASE WHEN task.attempts < task.max_attempts THEN FALSE ELSE TRUE END
				WHERE id = :taskId
				  AND status = 'IN_PROGRESS'
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns("task"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("taskId", taskId)
				.addValue("message", normalizedMessage)
				.addValue("error", jsonMapper.write(taskError))
				.addValue("availableAt", timestamp(now.plus(backoff)))
				.addValue("now", timestamp(now));
		return queryTask(sql, params);
	}

	@Override
	public Optional<AsyncTask> returnClaimToPending(long taskId, Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				UPDATE %s task
				SET status = 'PENDING',
				    callback_delivery_status = CASE
				        WHEN task.delivery_mode = 'POLLING' THEN 'NOT_REQUIRED'
				        ELSE 'PENDING'
				    END,
				    attempts = GREATEST(0, task.attempts - 1),
				    result = NULL,
				    error = NULL,
				    available_at = :now,
				    started_at = NULL,
				    finished_at = NULL,
				    updated_at = :now,
				    retryable = FALSE
				WHERE id = :taskId
				  AND status = 'IN_PROGRESS'
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns("task"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("taskId", taskId)
				.addValue("now", timestamp(now));
		return queryTask(sql, params);
	}

	@Override
	public Optional<AsyncTask> updateCallbackDeliveryStatus(long taskId, CallbackDeliveryStatus status, Instant now) {
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				UPDATE %s task
				SET callback_delivery_status = :callbackDeliveryStatus,
				    updated_at = :now
				WHERE id = :taskId
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns("task"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("taskId", taskId)
				.addValue("callbackDeliveryStatus", status.name())
				.addValue("now", timestamp(now));
		return queryTask(sql, params);
	}

	@Override
	public AsyncTaskRepositoryStats stats(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		EnumMap<AsyncTaskStatus, Long> statusCounts = new EnumMap<>(AsyncTaskStatus.class);
		for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
			statusCounts.put(status, 0L);
		}
		String statusSql = """
				SELECT status, COUNT(*) AS count
				FROM %s
				WHERE %s
				GROUP BY status
				""".formatted(tables.requestQueue(), ASYNC_DELIVERY_MODE_FILTER);
		jdbc.query(statusSql, new MapSqlParameterSource(), rs -> {
			statusCounts.put(AsyncTaskStatus.valueOf(rs.getString("status")), rs.getLong("count"));
		});
		String retrySql = """
				SELECT COUNT(*)
				FROM %s
				WHERE status = 'PENDING'
				  AND %s
				  AND attempts > 0
				""".formatted(tables.requestQueue(), ASYNC_DELIVERY_MODE_FILTER);
		Long retryCount = jdbc.queryForObject(retrySql, new MapSqlParameterSource(), Long.class);
		String oldestSql = """
				SELECT MIN(created_at)
				FROM %s
				WHERE status IN ('PENDING', 'IN_PROGRESS')
				  AND %s
				""".formatted(tables.requestQueue(), ASYNC_DELIVERY_MODE_FILTER);
		Timestamp oldest = jdbc.queryForObject(oldestSql, new MapSqlParameterSource(), Timestamp.class);
		return new AsyncTaskRepositoryStats(statusCounts, retryCount == null ? 0 : retryCount,
				oldest == null ? null : oldest.toInstant());
	}

	private Optional<StoredAsyncTask> insertTask(ExternalAsyncRequest request, int maxAttempts, Instant now) {
		String sql = """
				INSERT INTO %s (
				    external_id,
				    client_service,
				    priority,
				    priority_weight,
				    delivery_mode,
				    status,
				    callback_delivery_status,
				    payload,
				    attempts,
				    max_attempts,
				    created_at,
				    available_at,
				    updated_at,
				    retryable
				)
				VALUES (
				    :externalId,
				    :clientService,
				    :priority,
				    :priorityWeight,
				    :deliveryMode,
				    'PENDING',
				    :callbackDeliveryStatus,
				    CAST(:payload AS jsonb),
				    0,
				    :maxAttempts,
				    :now,
				    :now,
				    :now,
				    FALSE
				)
				ON CONFLICT (client_service, external_id)
				WHERE delivery_mode IN ('CALLBACK', 'POLLING')
				DO NOTHING
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns(null));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("externalId", request.externalId())
				.addValue("clientService", request.clientService())
				.addValue("priority", request.priority().name())
				.addValue("priorityWeight", request.priority().weight())
				.addValue("deliveryMode", request.deliveryMode().name())
				.addValue("callbackDeliveryStatus", callbackStatus(request.deliveryMode()).name())
				.addValue("payload", jsonMapper.write(request.payload()))
				.addValue("maxAttempts", maxAttempts)
				.addValue("now", timestamp(now));
		return queryStored(sql, params).stream().findFirst();
	}

	private Optional<StoredAsyncTask> findStoredByIdempotencyKey(String clientService, UUID externalId) {
		String sql = baseSelect() + """

				WHERE client_service = :clientService
				  AND external_id = :externalId
				  AND %s
				""".formatted(ASYNC_DELIVERY_MODE_FILTER);
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("clientService", clientService)
				.addValue("externalId", externalId);
		return queryStored(sql, params).stream().findFirst();
	}

	private Optional<StoredAsyncTask> findStoredByTaskId(long taskId, Optional<String> clientService,
			boolean forUpdate) {
		MapSqlParameterSource params = new MapSqlParameterSource("taskId", taskId);
		String sql = baseSelect() + "\nWHERE id = :taskId"
				+ "\n  AND " + ASYNC_DELIVERY_MODE_FILTER;
		if (clientService.isPresent()) {
			sql += "\n  AND client_service = :clientService";
			params.addValue("clientService", clientService.orElseThrow());
		}
		if (forUpdate) {
			sql += "\nFOR UPDATE";
		}
		return queryStored(sql, params).stream().findFirst();
	}

	private Optional<AsyncTask> cancelLockedTask(long taskId, Instant now) {
		TaskError cancelError = new TaskError("TASK_CANCELLED", "Задача отменена до начала выполнения", false);
		String sql = """
				UPDATE %s task
				SET status = 'CANCELLED',
				    result = NULL,
				    error = CAST(:error AS jsonb),
				    finished_at = :now,
				    updated_at = :now,
				    last_error = :message,
				    retryable = FALSE
				WHERE id = :taskId
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns("task"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("taskId", taskId)
				.addValue("error", jsonMapper.write(cancelError))
				.addValue("message", cancelError.message())
				.addValue("now", timestamp(now));
		return queryTask(sql, params);
	}

	private Optional<AsyncTask> retryLockedTask(long taskId, Instant now) {
		String sql = """
				UPDATE %s task
				SET status = 'PENDING',
				    callback_delivery_status = CASE
				        WHEN task.delivery_mode = 'POLLING' THEN 'NOT_REQUIRED'
				        ELSE 'PENDING'
				    END,
				    result = NULL,
				    error = NULL,
				    attempts = 0,
				    available_at = :now,
				    started_at = NULL,
				    finished_at = NULL,
				    updated_at = :now,
				    last_error = NULL,
				    retryable = FALSE
				WHERE id = :taskId
				RETURNING %s
				""".formatted(tables.requestQueue(), returningColumns("task"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("taskId", taskId)
				.addValue("now", timestamp(now));
		return queryTask(sql, params);
	}

	private Optional<AsyncTask> queryTask(String sql, MapSqlParameterSource params) {
		return queryStored(sql, params).stream().findFirst().map(StoredAsyncTask::task);
	}

	private List<AsyncTaskClaim> queryClaim(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, (rs, rowNum) -> new AsyncTaskClaim(toTask(rs),
				jsonMapper.readMap(rs.getString("payload_json"))));
	}

	private List<StoredAsyncTask> queryStored(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, (rs, rowNum) -> new StoredAsyncTask(toTask(rs),
				jsonMapper.readMap(rs.getString("payload_json"))));
	}

	private AsyncTask toTask(ResultSet rs) throws SQLException {
		return new AsyncTask(
				rs.getLong("id"),
				rs.getObject("external_id", UUID.class),
				rs.getString("client_service"),
				AsyncPriority.valueOf(rs.getString("priority")),
				AsyncDeliveryMode.valueOf(rs.getString("delivery_mode")),
				AsyncTaskStatus.valueOf(rs.getString("status")),
				CallbackDeliveryStatus.valueOf(rs.getString("callback_delivery_status")),
				jsonMapper.readMap(rs.getString("result_json")),
				jsonMapper.read(rs.getString("error_json"), TaskError.class),
				rs.getInt("attempts"),
				rs.getInt("max_attempts"),
				instant(rs, "created_at"),
				instant(rs, "available_at"),
				instant(rs, "started_at"),
				instant(rs, "finished_at"),
				rs.getString("last_error"),
				rs.getBoolean("retryable")
		);
	}

	private String baseSelect() {
		return "SELECT " + returningColumns(null) + "\nFROM " + tables.requestQueue();
	}

	private static String returningColumns(String alias) {
		String prefix = alias == null ? "" : alias + ".";
		return """
				%1$sid,
				%1$sexternal_id,
				%1$sclient_service,
				%1$spriority,
				%1$spriority_weight,
				%1$sdelivery_mode,
				%1$sstatus,
				%1$scallback_delivery_status,
				%1$spayload::text AS payload_json,
				%1$sresult::text AS result_json,
				%1$serror::text AS error_json,
				%1$sattempts,
				%1$smax_attempts,
				%1$screated_at,
				%1$savailable_at,
				%1$sstarted_at,
				%1$sfinished_at,
				%1$supdated_at,
				%1$slast_error,
				%1$sretryable
				""".formatted(prefix).strip();
	}

	private static List<String> conflictingFields(StoredAsyncTask existing, ExternalAsyncRequest request) {
		ArrayList<String> fields = new ArrayList<>();
		if (!Objects.equals(existing.payload(), request.payload())) {
			fields.add("payload");
		}
		if (existing.task().priority() != request.priority()) {
			fields.add("priority");
		}
		if (existing.task().deliveryMode() != request.deliveryMode()) {
			fields.add("deliveryMode");
		}
		return fields;
	}

	private static CallbackDeliveryStatus callbackStatus(AsyncDeliveryMode deliveryMode) {
		if (deliveryMode != AsyncDeliveryMode.CALLBACK) {
			return CallbackDeliveryStatus.NOT_REQUIRED;
		}
		return CallbackDeliveryStatus.PENDING;
	}

	private static String normalizeErrorMessage(String message) {
		if (message == null || message.isBlank()) {
			return "Временная ошибка upstream";
		}
		return message;
	}

	private static Timestamp timestamp(Instant instant) {
		return Timestamp.from(instant);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private record StoredAsyncTask(AsyncTask task, Map<String, Object> payload) {
	}
}
