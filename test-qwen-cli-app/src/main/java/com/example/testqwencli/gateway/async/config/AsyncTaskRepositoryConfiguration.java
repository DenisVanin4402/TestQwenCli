package com.example.testqwencli.gateway.async.config;

import com.example.testqwencli.gateway.async.AsyncTaskRepository;
import com.example.testqwencli.gateway.async.memory.MemoryAsyncTaskRepository;
import com.example.testqwencli.gateway.async.postgres.PostgresAsyncTaskRepository;
import com.example.testqwencli.gateway.postgres.ExternalGatewayPostgresProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(ExternalGatewayAsyncProperties.class)
public class AsyncTaskRepositoryConfiguration {

	@Bean
	@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "memory", matchIfMissing = true)
	AsyncTaskRepository memoryAsyncTaskRepository() {
		return new MemoryAsyncTaskRepository();
	}

	@Bean
	@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "postgres")
	AsyncTaskRepository postgresAsyncTaskRepository(
			NamedParameterJdbcTemplate externalGatewayNamedParameterJdbcTemplate,
			TransactionTemplate externalGatewayTransactionTemplate,
			ExternalGatewayPostgresProperties postgresProperties,
			ObjectMapper objectMapper
	) {
		return new PostgresAsyncTaskRepository(externalGatewayNamedParameterJdbcTemplate,
				externalGatewayTransactionTemplate, postgresProperties, objectMapper);
	}
}
