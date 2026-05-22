package com.example.testqwencli.gateway.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

@ConfigurationProperties(prefix = "external-gateway.postgres")
public record ExternalGatewayPostgresProperties(
		String jdbcUrl,
		String username,
		String password,
		@DefaultValue("external_gateway") String schema,
		@DefaultValue("true") boolean liquibaseEnabled
) {

	public ExternalGatewayPostgresProperties {
		if (schema == null || schema.isBlank()) {
			schema = "external_gateway";
		}
		PostgresTableNames.validateIdentifier(schema);
	}

	public String requiredJdbcUrl() {
		if (jdbcUrl == null || jdbcUrl.isBlank()) {
			throw new IllegalStateException("external-gateway.postgres.jdbc-url обязателен для postgres mode");
		}
		return jdbcUrl;
	}

	public String requiredUsername() {
		if (username == null || username.isBlank()) {
			throw new IllegalStateException("external-gateway.postgres.username обязателен для postgres mode");
		}
		return username;
	}

	public String passwordOrEmpty() {
		return Objects.toString(password, "");
	}
}
