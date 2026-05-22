package com.example.testqwencli.gateway.callback.postgres;

import com.example.testqwencli.gateway.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.async.AsyncTask;
import com.example.testqwencli.gateway.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.async.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.callback.CallbackDelivery;
import com.example.testqwencli.gateway.callback.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.callback.CallbackDeliveryRepositoryStats;
import com.example.testqwencli.gateway.callback.CallbackPayload;
import com.example.testqwencli.gateway.postgres.ExternalGatewayPostgresProperties;
import com.example.testqwencli.gateway.postgres.PostgresJsonMapper;
import com.example.testqwencli.gateway.postgres.PostgresTableNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PostgresCallbackDeliveryRepository implements CallbackDeliveryRepository {

	private final NamedParameterJdbcTemplate jdbc;
	private final PostgresTableNames tables;
	private final PostgresJsonMapper jsonMapper;

	public PostgresCallbackDeliveryRepository(
			NamedParameterJdbcTemplate jdbc,
			ExternalGatewayPostgresProperties postgresProperties,
			ObjectMapper objectMapper
	) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
		this.tables = new PostgresTableNames(Objects.requireNonNull(postgresProperties,
				"postgresProperties must not be null").schema());
		this.jsonMapper = new PostgresJsonMapper(objectMapper);
	}

	@Override
	public CallbackDelivery createPending(AsyncTask task, URI callbackUrl, int maxAttempts, Instant now) {
		Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
		validateFinalCallbackTask(task);
		validateMaxAttempts(maxAttempts);
		Objects.requireNonNull(now, "now must not be null");
		return upsertDelivery(task, CallbackDeliveryStatus.PENDING, callbackUrl, null, maxAttempts, now)
				.orElseThrow();
	}

	@Override
	public CallbackDelivery createDead(AsyncTask task, String message, int maxAttempts, Instant now) {
		validateFinalCallbackTask(task);
		validateMaxAttempts(maxAttempts);
		Objects.requireNonNull(now, "now must not be null");
		return upsertDelivery(task, CallbackDeliveryStatus.DEAD, null, normalizeErrorMessage(message), maxAttempts,
				now).orElseThrow();
	}

	@Override
	public Optional<CallbackDelivery> findByTaskId(long taskId) {
		String sql = baseSelect() + "\nWHERE task_id = :taskId";
		return queryDelivery(sql, new MapSqlParameterSource("taskId", taskId));
	}

	@Override
	public Optional<CallbackDelivery> claimNextPending(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				WITH candidate AS (
				    SELECT delivery_id
				    FROM %s
				    WHERE status IN ('PENDING', 'RETRY')
				      AND available_at <= :now
				    ORDER BY available_at ASC, created_at ASC, delivery_id ASC
				    FOR UPDATE SKIP LOCKED
				    LIMIT 1
				)
				UPDATE %s delivery
				SET status = 'DELIVERING',
				    attempts = delivery.attempts + 1,
				    started_at = :now,
				    completed_at = NULL,
				    payload = jsonb_set(delivery.payload, '{eventId}', to_jsonb(CAST(:eventId AS text)), true)
				FROM candidate
				WHERE delivery.delivery_id = candidate.delivery_id
				RETURNING %s
				""".formatted(tables.callbackDelivery(), tables.callbackDelivery(), returningColumns("delivery"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("now", timestamp(now))
				.addValue("eventId", UUID.randomUUID().toString());
		return queryDelivery(sql, params);
	}

	@Override
	public Optional<CallbackDelivery> markDelivered(UUID deliveryId, Instant now) {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				UPDATE %s delivery
				SET status = 'DELIVERED',
				    completed_at = :now,
				    last_error = NULL
				WHERE delivery_id = :deliveryId
				  AND status = 'DELIVERING'
				RETURNING %s
				""".formatted(tables.callbackDelivery(), returningColumns("delivery"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("deliveryId", deliveryId)
				.addValue("now", timestamp(now));
		return queryDelivery(sql, params);
	}

	@Override
	public Optional<CallbackDelivery> markRetryOrDead(UUID deliveryId, String message, Duration backoff,
			Instant now) {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(backoff, "backoff must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (backoff.isNegative()) {
			throw new IllegalArgumentException("Backoff retry callback-доставки не должен быть отрицательным");
		}
		String sql = """
				UPDATE %s delivery
				SET status = CASE WHEN delivery.attempts >= delivery.max_attempts THEN 'DEAD' ELSE 'RETRY' END,
				    available_at = CASE
				        WHEN delivery.attempts >= delivery.max_attempts THEN delivery.available_at
				        ELSE :availableAt
				    END,
				    completed_at = CASE
				        WHEN delivery.attempts >= delivery.max_attempts THEN :now
				        ELSE NULL
				    END,
				    last_error = :message
				WHERE delivery_id = :deliveryId
				  AND status = 'DELIVERING'
				RETURNING %s
				""".formatted(tables.callbackDelivery(), returningColumns("delivery"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("deliveryId", deliveryId)
				.addValue("message", normalizeErrorMessage(message))
				.addValue("availableAt", timestamp(now.plus(backoff)))
				.addValue("now", timestamp(now));
		return queryDelivery(sql, params);
	}

	@Override
	public Optional<CallbackDelivery> markDead(UUID deliveryId, String message, Instant now) {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				UPDATE %s delivery
				SET status = 'DEAD',
				    completed_at = :now,
				    last_error = :message
				WHERE delivery_id = :deliveryId
				  AND status <> 'DELIVERED'
				RETURNING %s
				""".formatted(tables.callbackDelivery(), returningColumns("delivery"));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("deliveryId", deliveryId)
				.addValue("message", normalizeErrorMessage(message))
				.addValue("now", timestamp(now));
		return queryDelivery(sql, params);
	}

	@Override
	public CallbackDeliveryRepositoryStats stats(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		EnumMap<CallbackDeliveryStatus, Long> statusCounts = new EnumMap<>(CallbackDeliveryStatus.class);
		for (CallbackDeliveryStatus status : CallbackDeliveryStatus.values()) {
			statusCounts.put(status, 0L);
		}
		String statusSql = """
				SELECT status, COUNT(*) AS count
				FROM %s
				GROUP BY status
				""".formatted(tables.callbackDelivery());
		jdbc.query(statusSql, new MapSqlParameterSource(), rs -> {
			statusCounts.put(CallbackDeliveryStatus.valueOf(rs.getString("status")), rs.getLong("count"));
		});
		String oldestSql = """
				SELECT MIN(created_at)
				FROM %s
				WHERE status IN ('PENDING', 'DELIVERING', 'RETRY')
				""".formatted(tables.callbackDelivery());
		Timestamp oldest = jdbc.queryForObject(oldestSql, new MapSqlParameterSource(), Timestamp.class);
		return new CallbackDeliveryRepositoryStats(statusCounts, oldest == null ? null : oldest.toInstant());
	}

	private Optional<CallbackDelivery> upsertDelivery(AsyncTask task, CallbackDeliveryStatus status, URI callbackUrl,
			String message, int maxAttempts, Instant now) {
		CallbackPayload payload = CallbackPayload.fromTask(UUID.randomUUID(), task);
		UUID deliveryId = UUID.randomUUID();
		String sql = """
				INSERT INTO %s (
				    delivery_id,
				    task_id,
				    callback_url,
				    status,
				    payload,
				    attempts,
				    max_attempts,
				    created_at,
				    available_at,
				    completed_at,
				    last_error
				)
				VALUES (
				    :deliveryId,
				    :taskId,
				    :callbackUrl,
				    :status,
				    CAST(:payload AS jsonb),
				    0,
				    :maxAttempts,
				    :now,
				    :now,
				    :completedAt,
				    :lastError
				)
				ON CONFLICT (task_id) DO UPDATE
				SET delivery_id = EXCLUDED.delivery_id,
				    callback_url = EXCLUDED.callback_url,
				    status = EXCLUDED.status,
				    payload = EXCLUDED.payload,
				    attempts = EXCLUDED.attempts,
				    max_attempts = EXCLUDED.max_attempts,
				    created_at = EXCLUDED.created_at,
				    available_at = EXCLUDED.available_at,
				    started_at = NULL,
				    completed_at = EXCLUDED.completed_at,
				    last_error = EXCLUDED.last_error
				RETURNING %s
				""".formatted(tables.callbackDelivery(), returningColumns(null));
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("deliveryId", deliveryId)
				.addValue("taskId", task.taskId())
				.addValue("callbackUrl", callbackUrl == null ? null : callbackUrl.toString())
				.addValue("status", status.name())
				.addValue("payload", jsonMapper.write(payload))
				.addValue("maxAttempts", maxAttempts)
				.addValue("now", timestamp(now))
				.addValue("completedAt", status == CallbackDeliveryStatus.DEAD ? timestamp(now) : null)
				.addValue("lastError", message);
		return queryDelivery(sql, params);
	}

	private Optional<CallbackDelivery> queryDelivery(String sql, MapSqlParameterSource params) {
		List<CallbackDelivery> deliveries = jdbc.query(sql, params, (rs, rowNum) -> toDelivery(rs));
		return deliveries.stream().findFirst();
	}

	private CallbackDelivery toDelivery(ResultSet rs) throws SQLException {
		String callbackUrl = rs.getString("callback_url");
		return new CallbackDelivery(
				rs.getObject("delivery_id", UUID.class),
				jsonMapper.read(rs.getString("payload_json"), CallbackPayload.class),
				callbackUrl == null ? null : URI.create(callbackUrl),
				CallbackDeliveryStatus.valueOf(rs.getString("status")),
				rs.getInt("attempts"),
				rs.getInt("max_attempts"),
				instant(rs, "created_at"),
				instant(rs, "available_at"),
				instant(rs, "started_at"),
				instant(rs, "completed_at"),
				rs.getString("last_error")
		);
	}

	private String baseSelect() {
		return "SELECT " + returningColumns(null) + "\nFROM " + tables.callbackDelivery();
	}

	private static String returningColumns(String alias) {
		String prefix = alias == null ? "" : alias + ".";
		return """
				%1$sdelivery_id,
				%1$stask_id,
				%1$scallback_url,
				%1$sstatus,
				%1$spayload::text AS payload_json,
				%1$sattempts,
				%1$smax_attempts,
				%1$screated_at,
				%1$savailable_at,
				%1$sstarted_at,
				%1$scompleted_at,
				%1$slast_error
				""".formatted(prefix).strip();
	}

	private static void validateFinalCallbackTask(AsyncTask task) {
		Objects.requireNonNull(task, "task must not be null");
		if (task.deliveryMode() != AsyncDeliveryMode.CALLBACK) {
			throw new IllegalArgumentException("Callback delivery создается только для deliveryMode=CALLBACK");
		}
		if (!isFinal(task.status())) {
			throw new IllegalArgumentException("Callback delivery создается только для финальной async-задачи");
		}
	}

	private static void validateMaxAttempts(int maxAttempts) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
	}

	private static boolean isFinal(AsyncTaskStatus status) {
		return status == AsyncTaskStatus.DONE
				|| status == AsyncTaskStatus.FAILED
				|| status == AsyncTaskStatus.DEAD
				|| status == AsyncTaskStatus.CANCELLED;
	}

	private static String normalizeErrorMessage(String message) {
		if (message == null || message.isBlank()) {
			return "Callback-доставка завершилась ошибкой";
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
}
