package com.example.testqwencli.gateway.slot.config;

import com.example.testqwencli.gateway.postgres.ExternalGatewayPostgresProperties;
import com.example.testqwencli.gateway.slot.SlotReleaseNotificationPublisher;
import com.example.testqwencli.gateway.slot.SlotRepository;
import com.example.testqwencli.gateway.slot.memory.MemorySlotRepository;
import com.example.testqwencli.gateway.slot.postgres.PostgresSlotRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(ExternalGatewaySlotProperties.class)
public class SlotRepositoryConfiguration {

	@Bean
	@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "memory", matchIfMissing = true)
	SlotRepository memorySlotRepository(ExternalGatewaySlotProperties properties) {
		return new MemorySlotRepository(properties);
	}

	@Bean
	@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "postgres")
	SlotRepository postgresSlotRepository(
			NamedParameterJdbcTemplate externalGatewayNamedParameterJdbcTemplate,
			TransactionTemplate externalGatewayTransactionTemplate,
			ExternalGatewayPostgresProperties postgresProperties,
			ExternalGatewaySlotProperties slotProperties,
			ObjectProvider<SlotReleaseNotificationPublisher> notificationPublisher
	) {
		return new PostgresSlotRepository(externalGatewayNamedParameterJdbcTemplate,
				externalGatewayTransactionTemplate, postgresProperties, slotProperties,
				notificationPublisher.getIfAvailable(SlotReleaseNotificationPublisher::noop));
	}
}
