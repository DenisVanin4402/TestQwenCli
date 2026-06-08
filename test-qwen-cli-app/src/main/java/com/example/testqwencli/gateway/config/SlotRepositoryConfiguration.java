package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.repository.memory.MemorySlotRepository;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotRepository;
import com.example.testqwencli.gateway.repository.SlotRepository;
import com.example.testqwencli.gateway.services.SlotReleaseNotificationPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

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
			@Qualifier("externalGatewayTransactionManager") PlatformTransactionManager externalGatewayTransactionManager,
			ExternalGatewayPostgresProperties postgresProperties,
			ExternalGatewaySlotProperties slotProperties,
			ObjectProvider<SlotReleaseNotificationPublisher> notificationPublisher
	) {
		return new PostgresSlotRepository(externalGatewayNamedParameterJdbcTemplate,
				externalGatewayTransactionManager, postgresProperties, slotProperties,
				notificationPublisher.getIfAvailable(SlotReleaseNotificationPublisher::noop));
	}
}
