package com.example.testqwencli.gateway.slot.postgres;

import com.example.testqwencli.gateway.postgres.ExternalGatewayPostgresProperties;
import com.example.testqwencli.gateway.postgres.PostgresTableNames;
import com.example.testqwencli.gateway.slot.SlotKind;
import com.example.testqwencli.gateway.slot.SlotLease;
import com.example.testqwencli.gateway.slot.SlotRepository;
import com.example.testqwencli.gateway.slot.config.ExternalGatewaySlotProperties;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PostgresSlotRepository implements SlotRepository {

	private static final RowMapper<SlotLease> SLOT_LEASE_ROW_MAPPER = (rs, rowNum) -> new SlotLease(
			rs.getInt("slot_id"),
			rs.getObject("lease_id", UUID.class),
			rs.getString("owner"),
			SlotKind.valueOf(rs.getString("kind")),
			rs.getTimestamp("expires_at").toInstant(),
			Optional.ofNullable(rs.getString("task_id"))
	);

	private final NamedParameterJdbcTemplate jdbc;
	private final TransactionTemplate transactionTemplate;
	private final PostgresTableNames tables;
	private final int totalSlots;
	private final int targetFreeSyncSlots;
	private final Duration leaseTtl;
	private final Duration syncWaiterTtl;

	public PostgresSlotRepository(
			NamedParameterJdbcTemplate jdbc,
			TransactionTemplate transactionTemplate,
			ExternalGatewayPostgresProperties postgresProperties,
			ExternalGatewaySlotProperties slotProperties
	) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
		this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
		Objects.requireNonNull(slotProperties, "slotProperties must not be null");
		this.tables = new PostgresTableNames(Objects.requireNonNull(postgresProperties,
				"postgresProperties must not be null").schema());
		this.totalSlots = slotProperties.total();
		this.targetFreeSyncSlots = slotProperties.targetFreeSyncSlots();
		this.leaseTtl = slotProperties.leaseTtl();
		this.syncWaiterTtl = slotProperties.syncWaiterTtl();
	}

	@Override
	public Optional<SlotLease> acquireSyncSlot(String owner, Instant now) {
		validateOwner(owner);
		Objects.requireNonNull(now, "now must not be null");
		return executeOptional(() -> acquireAvailableSlot(owner, SlotKind.SYNC, null, now));
	}

	@Override
	public Optional<SlotLease> acquireAsyncSlot(String owner, String taskId, Instant now) {
		validateOwner(owner);
		Objects.requireNonNull(now, "now must not be null");
		return executeOptional(() -> {
			deleteExpiredSyncWaiters(now);
			if (countLiveSyncWaitersInternal(now) > 0) {
				return Optional.empty();
			}

			lockSlotRows();
			long syncBusy = countBusySlotsInternal(SlotKind.SYNC, now);
			long asyncBusy = countBusySlotsInternal(SlotKind.ASYNC, now);
			int asyncAllowed = Math.max(0, totalSlots - (int) syncBusy - targetFreeSyncSlots);
			if (asyncBusy >= asyncAllowed) {
				return Optional.empty();
			}
			return acquireAvailableSlot(owner, SlotKind.ASYNC, normalizeTaskId(taskId), now);
		});
	}

	@Override
	public boolean release(int slotId, UUID leaseId) {
		Objects.requireNonNull(leaseId, "leaseId must not be null");
		String sql = """
				UPDATE %s
				SET lease_id = NULL,
				    owner = NULL,
				    kind = NULL,
				    acquired_at = NULL,
				    expires_at = NULL,
				    task_id = NULL
				WHERE slot_id = :slotId
				  AND lease_id = :leaseId
				""".formatted(tables.slots());
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("slotId", slotId)
				.addValue("leaseId", leaseId);
		return jdbc.update(sql, params) > 0;
	}

	@Override
	public Optional<SlotLease> heartbeat(int slotId, UUID leaseId, Instant now) {
		Objects.requireNonNull(leaseId, "leaseId must not be null");
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				UPDATE %s
				SET expires_at = :expiresAt
				WHERE slot_id = :slotId
				  AND lease_id = :leaseId
				  AND expires_at > :now
				RETURNING slot_id, lease_id, owner, kind, expires_at, task_id
				""".formatted(tables.slots());
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("slotId", slotId)
				.addValue("leaseId", leaseId)
				.addValue("now", timestamp(now))
				.addValue("expiresAt", timestamp(now.plus(leaseTtl)));
		return queryLease(sql, params);
	}

	@Override
	public long countBusySlots(SlotKind kind) {
		Objects.requireNonNull(kind, "kind must not be null");
		String sql = """
				SELECT COUNT(*)
				FROM %s
				WHERE lease_id IS NOT NULL
				  AND kind = :kind
				  AND expires_at > CURRENT_TIMESTAMP
				""".formatted(tables.slots());
		return jdbc.queryForObject(sql, new MapSqlParameterSource("kind", kind.name()), Long.class);
	}

	@Override
	public int reapExpiredLeases(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		String sql = """
				UPDATE %s
				SET lease_id = NULL,
				    owner = NULL,
				    kind = NULL,
				    acquired_at = NULL,
				    expires_at = NULL,
				    task_id = NULL
				WHERE lease_id IS NOT NULL
				  AND expires_at <= :now
				""".formatted(tables.slots());
		return jdbc.update(sql, new MapSqlParameterSource("now", timestamp(now)));
	}

	@Override
	public UUID registerSyncWaiter(String owner, Instant now) {
		validateOwner(owner);
		Objects.requireNonNull(now, "now must not be null");
		return transactionTemplate.execute(status -> {
			deleteExpiredSyncWaiters(now);
			UUID waiterId = UUID.randomUUID();
			String sql = """
					INSERT INTO %s (waiter_id, owner, registered_at, expires_at)
					VALUES (:waiterId, :owner, :registeredAt, :expiresAt)
					""".formatted(tables.syncWaiters());
			MapSqlParameterSource params = new MapSqlParameterSource()
					.addValue("waiterId", waiterId)
					.addValue("owner", owner)
					.addValue("registeredAt", timestamp(now))
					.addValue("expiresAt", timestamp(now.plus(syncWaiterTtl)));
			jdbc.update(sql, params);
			return waiterId;
		});
	}

	@Override
	public boolean removeSyncWaiter(UUID waiterId) {
		Objects.requireNonNull(waiterId, "waiterId must not be null");
		String sql = "DELETE FROM %s WHERE waiter_id = :waiterId".formatted(tables.syncWaiters());
		return jdbc.update(sql, new MapSqlParameterSource("waiterId", waiterId)) > 0;
	}

	@Override
	public long countLiveSyncWaiters(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		return transactionTemplate.execute(status -> {
			deleteExpiredSyncWaiters(now);
			return countLiveSyncWaitersInternal(now);
		});
	}

	private Optional<SlotLease> acquireAvailableSlot(String owner, SlotKind kind, String taskId, Instant now) {
		UUID leaseId = UUID.randomUUID();
		String sql = """
				WITH candidate AS (
				    SELECT slot_id
				    FROM %s
				    WHERE lease_id IS NULL
				       OR expires_at <= :now
				    ORDER BY slot_id
				    FOR UPDATE SKIP LOCKED
				    LIMIT 1
				)
				UPDATE %s slot
				SET lease_id = :leaseId,
				    owner = :owner,
				    kind = :kind,
				    acquired_at = :acquiredAt,
				    expires_at = :expiresAt,
				    task_id = :taskId
				FROM candidate
				WHERE slot.slot_id = candidate.slot_id
				RETURNING slot.slot_id, slot.lease_id, slot.owner, slot.kind, slot.expires_at, slot.task_id
				""".formatted(tables.slots(), tables.slots());
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("leaseId", leaseId)
				.addValue("owner", owner)
				.addValue("kind", kind.name())
				.addValue("taskId", taskId)
				.addValue("now", timestamp(now))
				.addValue("acquiredAt", timestamp(now))
				.addValue("expiresAt", timestamp(now.plus(leaseTtl)));
		return queryLease(sql, params);
	}

	private void lockSlotRows() {
		String sql = "SELECT slot_id FROM %s ORDER BY slot_id FOR UPDATE".formatted(tables.slots());
		jdbc.getJdbcOperations().query(sql, rs -> {
		});
	}

	private long countBusySlotsInternal(SlotKind kind, Instant now) {
		String sql = """
				SELECT COUNT(*)
				FROM %s
				WHERE lease_id IS NOT NULL
				  AND kind = :kind
				  AND expires_at > :now
				""".formatted(tables.slots());
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("kind", kind.name())
				.addValue("now", timestamp(now));
		return jdbc.queryForObject(sql, params, Long.class);
	}

	private long countLiveSyncWaitersInternal(Instant now) {
		String sql = """
				SELECT COUNT(*)
				FROM %s
				WHERE expires_at > :now
				""".formatted(tables.syncWaiters());
		return jdbc.queryForObject(sql, new MapSqlParameterSource("now", timestamp(now)), Long.class);
	}

	private void deleteExpiredSyncWaiters(Instant now) {
		String sql = "DELETE FROM %s WHERE expires_at <= :now".formatted(tables.syncWaiters());
		jdbc.update(sql, new MapSqlParameterSource("now", timestamp(now)));
	}

	private Optional<SlotLease> executeOptional(TransactionalSupplier<Optional<SlotLease>> supplier) {
		Optional<SlotLease> lease = transactionTemplate.execute(status -> supplier.get());
		return lease == null ? Optional.empty() : lease;
	}

	private Optional<SlotLease> queryLease(String sql, MapSqlParameterSource params) {
		List<SlotLease> leases = jdbc.query(sql, params, SLOT_LEASE_ROW_MAPPER);
		return leases.stream().findFirst();
	}

	private static String normalizeTaskId(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return null;
		}
		return taskId;
	}

	private static Timestamp timestamp(Instant instant) {
		return Timestamp.from(instant);
	}

	private static void validateOwner(String owner) {
		Objects.requireNonNull(owner, "owner must not be null");
		if (owner.isBlank()) {
			throw new IllegalArgumentException("Владелец lease не должен быть пустым");
		}
	}

	@FunctionalInterface
	private interface TransactionalSupplier<T> {

		T get();
	}
}
