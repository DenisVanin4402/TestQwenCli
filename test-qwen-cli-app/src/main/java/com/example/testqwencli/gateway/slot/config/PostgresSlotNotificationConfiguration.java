package com.example.testqwencli.gateway.slot.config;

import com.example.testqwencli.gateway.slot.SyncSlotReleaseNotifier;
import com.example.testqwencli.gateway.slot.postgres.PostgresSlotReleaseNotificationListener;
import com.example.testqwencli.gateway.slot.postgres.PostgresSlotReleaseNotificationPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "external-gateway.repository.type", havingValue = "postgres")
public class PostgresSlotNotificationConfiguration {

	@Bean
	@ConditionalOnExpression("'${external-gateway.slots.sync-acquire-wait-mode:POLLING}'.equalsIgnoreCase('LISTEN_NOTIFY')")
	PostgresSlotReleaseNotificationPublisher postgresSlotReleaseNotificationPublisher(
			@Qualifier("externalGatewayNamedParameterJdbcTemplate")
			NamedParameterJdbcTemplate externalGatewayNamedParameterJdbcTemplate,
			SyncSlotReleaseNotifier notifier
	) {
		return new PostgresSlotReleaseNotificationPublisher(externalGatewayNamedParameterJdbcTemplate, notifier);
	}

	@Bean
	@ConditionalOnExpression("'${external-gateway.slots.sync-acquire-wait-mode:POLLING}'.equalsIgnoreCase('LISTEN_NOTIFY')")
	PostgresSlotReleaseNotificationListener postgresSlotReleaseNotificationListener(
			@Qualifier("externalGatewayDataSource") DataSource dataSource,
			SyncSlotReleaseNotifier notifier
	) {
		return new PostgresSlotReleaseNotificationListener(dataSource, notifier);
	}
}
