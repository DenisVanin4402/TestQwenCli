package com.example.testqwencli.gateway.support;

import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTestSupport {

	protected static final String POSTGRES_SCHEMA = "external_gateway_it";

	@Container
	protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private DataSource dataSource;

	private JdbcTemplate jdbcTemplate;
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	private PostgresGatewayDatabaseCleaner databaseCleaner;

	@DynamicPropertySource
	static void registerPostgresProperties(DynamicPropertyRegistry registry) {
		registry.add("external-gateway.postgres.jdbc-url", POSTGRES::getJdbcUrl);
		registry.add("external-gateway.postgres.username", POSTGRES::getUsername);
		registry.add("external-gateway.postgres.password", POSTGRES::getPassword);
		registry.add("external-gateway.postgres.schema", () -> POSTGRES_SCHEMA);
	}

	protected JdbcTemplate jdbcTemplate() {
		if (jdbcTemplate == null) {
			jdbcTemplate = new JdbcTemplate(dataSource);
		}
		return jdbcTemplate;
	}

	protected NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
		if (namedParameterJdbcTemplate == null) {
			namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
		}
		return namedParameterJdbcTemplate;
	}

	protected void cleanGatewayTables() {
		databaseCleaner().clean();
	}

	protected AsyncTestAwaiter asyncAwaiter() {
		return AsyncTestAwaiter.defaults();
	}

	protected AsyncTestAwaiter asyncAwaiter(Duration timeout) {
		return AsyncTestAwaiter.of(timeout, Duration.ofMillis(50));
	}

	private PostgresGatewayDatabaseCleaner databaseCleaner() {
		if (databaseCleaner == null) {
			databaseCleaner = new PostgresGatewayDatabaseCleaner(jdbcTemplate(), POSTGRES_SCHEMA);
		}
		return databaseCleaner;
	}
}
