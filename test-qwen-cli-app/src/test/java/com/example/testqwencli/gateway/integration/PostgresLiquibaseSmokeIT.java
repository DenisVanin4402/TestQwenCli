package com.example.testqwencli.gateway.integration;

import com.example.testqwencli.gateway.support.PostgresIntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
		"external-gateway.repository.type=postgres",
		"external-gateway.postgres.liquibase-enabled=true",
		"external-gateway.slots.sync-acquire-wait-mode=polling",
		"external-gateway.slots.lease-reap-interval-ms=600000",
		"external-gateway.async.dispatcher-enabled=false",
		"external-gateway.callback.delivery-enabled=false"
})
class PostgresLiquibaseSmokeIT extends PostgresIntegrationTestSupport {

	private static final List<String> REQUIRED_TABLES = List.of(
			"ext_slots",
			"ext_sync_waiters",
			"ext_request_queue",
			"ext_callback_delivery"
	);
	private static final List<String> REQUIRED_CONSTRAINTS = List.of(
			"pk_ext_slots",
			"pk_ext_sync_waiters",
			"pk_ext_request_queue",
			"pk_ext_callback_delivery",
			"chk_ext_slots_kind",
			"chk_ext_slots_lease_shape",
			"chk_ext_request_queue_priority",
			"chk_ext_request_queue_delivery_mode",
			"chk_ext_request_queue_status",
			"chk_ext_request_queue_callback_status",
			"chk_ext_request_queue_attempts",
			"chk_ext_callback_delivery_status",
			"chk_ext_callback_delivery_attempts",
			"chk_ext_callback_delivery_url",
			"uq_ext_callback_delivery_task",
			"fk_ext_callback_delivery_task"
	);
	private static final List<String> REQUIRED_INDEXES = List.of(
			"idx_ext_slots_busy_kind_expires",
			"idx_ext_sync_waiters_expires_at",
			"idx_ext_request_queue_claim",
			"idx_ext_request_queue_external_id",
			"idx_ext_callback_delivery_claim",
			"uq_ext_request_queue_async_idempotency"
	);

	@AfterEach
	void cleanRuntimeData() {
		cleanGatewayTables();
	}

	@Test
	void postgresModeStartsAndLiquibaseCreatesGatewaySchema() {
		JdbcTemplate jdbcTemplate = jdbcTemplate();

		assertThat(schemaExists(jdbcTemplate)).isTrue();
		assertThat(tableNames(jdbcTemplate)).containsAll(REQUIRED_TABLES);
		assertThat(constraintNames(jdbcTemplate)).containsAll(REQUIRED_CONSTRAINTS);
		assertThat(indexNames(jdbcTemplate)).containsAll(REQUIRED_INDEXES);
		assertThat(slotCount(jdbcTemplate)).isEqualTo(5);
	}

	private boolean schemaExists(JdbcTemplate jdbcTemplate) {
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from information_schema.schemata where schema_name = ?",
				Integer.class,
				POSTGRES_SCHEMA
		);
		return count != null && count == 1;
	}

	private List<String> tableNames(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForList(
				"select table_name from information_schema.tables where table_schema = ?",
				String.class,
				POSTGRES_SCHEMA
		);
	}

	private List<String> constraintNames(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForList("""
				select c.conname
				from pg_constraint c
				join pg_namespace n on n.oid = c.connamespace
				where n.nspname = ?
				""", String.class, POSTGRES_SCHEMA);
	}

	private List<String> indexNames(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.queryForList(
				"select indexname from pg_indexes where schemaname = ?",
				String.class,
				POSTGRES_SCHEMA
		);
	}

	private int slotCount(JdbcTemplate jdbcTemplate) {
		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from " + POSTGRES_SCHEMA + ".ext_slots",
				Integer.class
		);
		return count == null ? 0 : count;
	}
}
