package com.example.testqwencli.gateway.support;

import com.example.testqwencli.gateway.repository.postgres.PostgresTableNames;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresGatewayDatabaseCleaner {

	private final JdbcTemplate jdbcTemplate;
	private final PostgresTableNames tables;

	public PostgresGatewayDatabaseCleaner(JdbcTemplate jdbcTemplate, String schema) {
		this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
		this.tables = new PostgresTableNames(schema);
	}

	public void clean() {
		jdbcTemplate.execute("""
				TRUNCATE TABLE %s, %s, %s RESTART IDENTITY
				""".formatted(tables.callbackDelivery(), tables.requestQueue(), tables.syncWaiters()));
		jdbcTemplate.update("""
				UPDATE %s
				SET lease_id = NULL,
				    owner = NULL,
				    kind = NULL,
				    acquired_at = NULL,
				    expires_at = NULL,
				    task_id = NULL
				""".formatted(tables.slots()));
	}
}
