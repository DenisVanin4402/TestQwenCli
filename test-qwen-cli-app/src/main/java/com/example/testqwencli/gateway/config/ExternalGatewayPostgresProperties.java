package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.repository.postgres.PostgresTableNames;
import java.util.Objects;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация PostgreSQL-хранилища external gateway.
 *
 * @param jdbcUrl JDBC URL отдельной gateway БД
 * @param username пользователь PostgreSQL
 * @param password пароль PostgreSQL, может быть пустым
 * @param schema схема, в которой лежат таблицы gateway
 * @param liquibaseEnabled включает применение Liquibase changelog на старте
 */
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

	/**
	 * Возвращает обязательный JDBC URL или бросает ошибку конфигурации.
	 *
	 * @return непустой JDBC URL
	 */
	public String requiredJdbcUrl() {
		if (jdbcUrl == null || jdbcUrl.isBlank()) {
			throw new IllegalStateException("external-gateway.postgres.jdbc-url обязателен для postgres mode");
		}
		return jdbcUrl;
	}

	/**
	 * Возвращает обязательное имя пользователя или бросает ошибку конфигурации.
	 *
	 * @return непустой username
	 */
	public String requiredUsername() {
		if (username == null || username.isBlank()) {
			throw new IllegalStateException("external-gateway.postgres.username обязателен для postgres mode");
		}
		return username;
	}

	/**
	 * @return пароль или пустая строка, если пароль не задан
	 */
	public String passwordOrEmpty() {
		return Objects.toString(password, "");
	}
}
