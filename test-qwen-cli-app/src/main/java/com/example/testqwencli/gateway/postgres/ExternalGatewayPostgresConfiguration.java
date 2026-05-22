package com.example.testqwencli.gateway.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "postgres")
@EnableConfigurationProperties(ExternalGatewayPostgresProperties.class)
public class ExternalGatewayPostgresConfiguration {

	@Bean(destroyMethod = "close")
	DataSource externalGatewayDataSource(ExternalGatewayPostgresProperties properties) {
		HikariConfig config = new HikariConfig();
		config.setPoolName("external-gateway-postgres");
		config.setJdbcUrl(properties.requiredJdbcUrl());
		config.setUsername(properties.requiredUsername());
		config.setPassword(properties.passwordOrEmpty());
		return new HikariDataSource(config);
	}

	@Bean
	NamedParameterJdbcTemplate externalGatewayNamedParameterJdbcTemplate(
			@Qualifier("externalGatewayDataSource") DataSource dataSource
	) {
		return new NamedParameterJdbcTemplate(dataSource);
	}

	@Bean
	PlatformTransactionManager externalGatewayTransactionManager(
			@Qualifier("externalGatewayDataSource") DataSource dataSource
	) {
		return new DataSourceTransactionManager(dataSource);
	}

	@Bean
	TransactionTemplate externalGatewayTransactionTemplate(
			@Qualifier("externalGatewayTransactionManager") PlatformTransactionManager transactionManager
	) {
		return new TransactionTemplate(transactionManager);
	}

	@Bean
	@ConditionalOnProperty(
			name = "external-gateway.postgres.liquibase-enabled",
			havingValue = "true",
			matchIfMissing = true
	)
	SpringLiquibase externalGatewayLiquibase(
			@Qualifier("externalGatewayDataSource") DataSource dataSource,
			ExternalGatewayPostgresProperties properties
	) {
		SpringLiquibase liquibase = new SpringLiquibase();
		liquibase.setDataSource(dataSource);
		liquibase.setChangeLog("classpath:db/changelog/external-gateway/db.changelog-master.yaml");
		liquibase.setChangeLogParameters(Map.of("externalGatewaySchema", properties.schema()));
		return liquibase;
	}
}
