package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.config.PostgresSlotNotificationConfiguration;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotReleaseNotificationListener;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotReleaseNotificationPublisher;
import com.example.testqwencli.gateway.services.impl.LocalSyncSlotReleaseNotifier;
import com.example.testqwencli.gateway.services.SyncSlotReleaseNotifier;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresSlotNotificationConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(PostgresSlotNotificationConfiguration.class);

	@Test
	void pollingPostgresModeDoesNotCreateNotificationBeans() {
		contextRunner
				.withPropertyValues("external-gateway.repository.type=postgres")
				.run(context -> {
					assertThat(context.getBeansOfType(PostgresSlotReleaseNotificationPublisher.class)).isEmpty();
					assertThat(context.getBeansOfType(PostgresSlotReleaseNotificationListener.class)).isEmpty();
				});
	}

	@Test
	void listenNotifyPostgresModeCreatesNotificationBeansWithLowercasePropertyValue() {
		contextRunner
				.withBean("externalGatewayNamedParameterJdbcTemplate", NamedParameterJdbcTemplate.class,
						() -> mock(NamedParameterJdbcTemplate.class))
				.withBean("externalGatewayDataSource", DataSource.class, this::unavailableDataSource)
				.withBean(SyncSlotReleaseNotifier.class, LocalSyncSlotReleaseNotifier::new)
				.withPropertyValues(
						"external-gateway.repository.type=postgres",
						"external-gateway.slots.sync-acquire-wait-mode=listen_notify"
				)
				.run(context -> {
					assertThat(context).hasSingleBean(PostgresSlotReleaseNotificationPublisher.class);
					assertThat(context).hasSingleBean(PostgresSlotReleaseNotificationListener.class);
				});
	}

	private DataSource unavailableDataSource() {
		try {
			DataSource dataSource = mock(DataSource.class);
			when(dataSource.getConnection()).thenThrow(new SQLException("PostgreSQL недоступен в unit-тесте"));
			return dataSource;
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
