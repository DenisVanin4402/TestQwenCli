package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.repository.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.memory.MemoryCallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.postgres.PostgresCallbackDeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
@EnableConfigurationProperties({
		ExternalGatewayCallbackProperties.class,
		ExternalGatewayClientsProperties.class
})
public class CallbackDeliveryRepositoryConfiguration {

	@Bean
	@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "memory", matchIfMissing = true)
	CallbackDeliveryRepository memoryCallbackDeliveryRepository() {
		return new MemoryCallbackDeliveryRepository();
	}

	@Bean
	@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "postgres")
	CallbackDeliveryRepository postgresCallbackDeliveryRepository(
			NamedParameterJdbcTemplate externalGatewayNamedParameterJdbcTemplate,
			ExternalGatewayPostgresProperties postgresProperties,
			ObjectMapper objectMapper
	) {
		return new PostgresCallbackDeliveryRepository(externalGatewayNamedParameterJdbcTemplate,
				postgresProperties, objectMapper);
	}
}
