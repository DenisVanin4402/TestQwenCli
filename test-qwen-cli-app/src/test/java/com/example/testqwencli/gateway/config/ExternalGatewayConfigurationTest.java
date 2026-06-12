package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.model.slot.enums.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.repository.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.SlotRepository;
import com.example.testqwencli.gateway.repository.memory.MemoryAsyncTaskRepository;
import com.example.testqwencli.gateway.repository.memory.MemoryCallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.memory.MemorySlotRepository;
import com.example.testqwencli.gateway.repository.postgres.PostgresAsyncTaskRepository;
import com.example.testqwencli.gateway.repository.postgres.PostgresCallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotReleaseNotificationListener;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotReleaseNotificationPublisher;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalGatewayConfigurationTest {

	private final ApplicationContextRunner propertiesRunner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesBindingConfiguration.class)
			.withPropertyValues(slotProperties());

	@Test
	void externalGatewayPropertiesUseExpectedDefaults() {
		propertiesRunner.run(context -> {
			ExternalGatewayAsyncProperties async = context.getBean(ExternalGatewayAsyncProperties.class);
			assertThat(async.dispatchIntervalMs()).isEqualTo(Duration.ofMillis(100));
			assertThat(async.maxAttempts()).isEqualTo(3);
			assertThat(async.dispatcherEnabled()).isFalse();
			assertThat(async.dispatchBatchSize()).isEqualTo(32);
			assertThat(async.retryBackoffMs()).isEqualTo(Duration.ofSeconds(1));

			ExternalGatewayCallbackProperties callback = context.getBean(ExternalGatewayCallbackProperties.class);
			assertThat(callback.deliveryEnabled()).isFalse();
			assertThat(callback.maxAttempts()).isEqualTo(3);
			assertThat(callback.deliveryBatchSize()).isEqualTo(10);
			assertThat(callback.retryBackoffMs()).isEqualTo(Duration.ofSeconds(1));
			assertThat(callback.deliveryIntervalMs()).isEqualTo(Duration.ofMillis(100));
			assertThat(callback.deliveryTimeoutMs()).isEqualTo(Duration.ofSeconds(30));
			assertThat(callback.deliveryRecoveryIntervalMs()).isEqualTo(Duration.ofSeconds(1));

			ExternalGatewaySlotProperties slots = context.getBean(ExternalGatewaySlotProperties.class);
			assertThat(slots.syncAcquirePollInterval()).isEqualTo(Duration.ofMillis(10));
			assertThat(slots.syncAcquireWaitMode()).isEqualTo(SyncAcquireWaitMode.POLLING);

			ExternalGatewaySyncProperties sync = context.getBean(ExternalGatewaySyncProperties.class);
			assertThat(sync.waitTimeoutMs()).isEqualTo(Duration.ofMillis(1500));

			ExternalGatewayUpstreamProperties upstream = context.getBean(ExternalGatewayUpstreamProperties.class);
			assertThat(upstream.simulatedDelayMs()).isEqualTo(Duration.ofMillis(25));

			ExternalGatewayPostgresProperties postgres = context.getBean(ExternalGatewayPostgresProperties.class);
			assertThat(postgres.schema()).isEqualTo("external_gateway");
			assertThat(postgres.liquibaseEnabled()).isTrue();
			assertThat(postgres.passwordOrEmpty()).isEmpty();

			ExternalGatewayClientsProperties clients = context.getBean(ExternalGatewayClientsProperties.class);
			assertThat(clients.clients()).isEmpty();
		});
	}

	@Test
	void externalGatewayPropertiesParseDurationEnumAndClientValues() {
		propertiesRunner
				.withPropertyValues(
						"external-gateway.slots.sync-acquire-poll-interval=250ms",
						"external-gateway.slots.sync-acquire-wait-mode=listen_notify",
						"external-gateway.sync.wait-timeout-ms=2s",
						"external-gateway.async.dispatch-interval-ms=15ms",
						"external-gateway.async.retry-backoff-ms=3s",
						"external-gateway.callback.delivery-interval-ms=25ms",
						"external-gateway.callback.delivery-timeout-ms=45s",
						"external-gateway.callback.delivery-recovery-interval-ms=4s",
						"external-gateway.upstream.simulated-delay-ms=75ms",
						"external-gateway.postgres.schema=gateway_test",
						"external-gateway.postgres.liquibase-enabled=false",
						"external-gateway.clients.audit.callback-url=https://callback.example/audit"
				)
				.run(context -> {
					assertThat(context.getBean(ExternalGatewaySlotProperties.class).syncAcquireWaitMode())
							.isEqualTo(SyncAcquireWaitMode.LISTEN_NOTIFY);
					assertThat(context.getBean(ExternalGatewaySlotProperties.class).syncAcquirePollInterval())
							.isEqualTo(Duration.ofMillis(250));
					assertThat(context.getBean(ExternalGatewaySyncProperties.class).waitTimeoutMs())
							.isEqualTo(Duration.ofSeconds(2));
					assertThat(context.getBean(ExternalGatewayAsyncProperties.class).dispatchIntervalMs())
							.isEqualTo(Duration.ofMillis(15));
					assertThat(context.getBean(ExternalGatewayAsyncProperties.class).retryBackoffMs())
							.isEqualTo(Duration.ofSeconds(3));
					assertThat(context.getBean(ExternalGatewayCallbackProperties.class).deliveryIntervalMs())
							.isEqualTo(Duration.ofMillis(25));
					assertThat(context.getBean(ExternalGatewayCallbackProperties.class).deliveryTimeoutMs())
							.isEqualTo(Duration.ofSeconds(45));
					assertThat(context.getBean(ExternalGatewayCallbackProperties.class).deliveryRecoveryIntervalMs())
							.isEqualTo(Duration.ofSeconds(4));
					assertThat(context.getBean(ExternalGatewayUpstreamProperties.class).simulatedDelayMs())
							.isEqualTo(Duration.ofMillis(75));
					assertThat(context.getBean(ExternalGatewayPostgresProperties.class).schema())
							.isEqualTo("gateway_test");
					assertThat(context.getBean(ExternalGatewayPostgresProperties.class).liquibaseEnabled())
							.isFalse();
					assertThat(context.getBean(ExternalGatewayClientsProperties.class).callbackUrl("audit"))
							.hasValueSatisfying(uri -> assertThat(uri).hasToString("https://callback.example/audit"));
				});
	}

	@Test
	void invalidPropertyBindingFailsFast() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("external-gateway.callback.delivery-batch-size", "11");

		assertThatThrownBy(() -> Binder.get(environment)
				.bindOrCreate("external-gateway.callback", ExternalGatewayCallbackProperties.class))
				.rootCause()
				.hasMessageContaining("deliveryBatchSize");
	}

	@Test
	void memoryModeCreatesMemoryRepositoriesWithoutPostgresInfrastructure() {
		new ApplicationContextRunner()
				.withUserConfiguration(MemoryRepositoryConfiguration.class)
				.withPropertyValues(properties("external-gateway.repository.type=memory"))
				.run(context -> {
					assertThat(context).hasSingleBean(SlotRepository.class);
					assertThat(context.getBean(SlotRepository.class)).isInstanceOf(MemorySlotRepository.class);
					assertThat(context).hasSingleBean(AsyncTaskRepository.class);
					assertThat(context.getBean(AsyncTaskRepository.class)).isInstanceOf(MemoryAsyncTaskRepository.class);
					assertThat(context).hasSingleBean(CallbackDeliveryRepository.class);
					assertThat(context.getBean(CallbackDeliveryRepository.class))
							.isInstanceOf(MemoryCallbackDeliveryRepository.class);
					assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
					assertThat(context.getBeansOfType(SpringLiquibase.class)).isEmpty();
					assertThat(context.getBeansOfType(PostgresSlotReleaseNotificationListener.class)).isEmpty();
					assertThat(context.getBeansOfType(PostgresSlotReleaseNotificationPublisher.class)).isEmpty();
				});
	}

	@Test
	void postgresModeCreatesPostgresRepositoriesWhenInfrastructureIsProvided() {
		new ApplicationContextRunner()
				.withUserConfiguration(PostgresRepositoryConfiguration.class)
				.withPropertyValues(properties(
						"external-gateway.repository.type=postgres",
						"external-gateway.postgres.jdbc-url=jdbc:postgresql://localhost:5432/external_gateway",
						"external-gateway.postgres.username=external_gateway"
				))
				.run(context -> {
					assertThat(context).hasSingleBean(SlotRepository.class);
					assertThat(context.getBean(SlotRepository.class)).isInstanceOf(PostgresSlotRepository.class);
					assertThat(context).hasSingleBean(AsyncTaskRepository.class);
					assertThat(context.getBean(AsyncTaskRepository.class)).isInstanceOf(PostgresAsyncTaskRepository.class);
					assertThat(context).hasSingleBean(CallbackDeliveryRepository.class);
					assertThat(context.getBean(CallbackDeliveryRepository.class))
							.isInstanceOf(PostgresCallbackDeliveryRepository.class);
				});
	}

	private static String[] properties(String... additionalProperties) {
		return Stream.concat(Arrays.stream(slotProperties()), Arrays.stream(additionalProperties))
				.toArray(String[]::new);
	}

	private static String[] slotProperties() {
		return new String[] {
				"external-gateway.slots.total=5",
				"external-gateway.slots.target-free-sync-slots=1",
				"external-gateway.slots.lease-ttl=30s",
				"external-gateway.slots.sync-waiter-ttl=5s"
		};
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties({
			ExternalGatewayAsyncProperties.class,
			ExternalGatewayCallbackProperties.class,
			ExternalGatewayClientsProperties.class,
			ExternalGatewayPostgresProperties.class,
			ExternalGatewaySlotProperties.class,
			ExternalGatewaySyncProperties.class,
			ExternalGatewayUpstreamProperties.class
	})
	private static class PropertiesBindingConfiguration {
	}

	@Configuration(proxyBeanMethods = false)
	@Import({
			SlotRepositoryConfiguration.class,
			AsyncTaskRepositoryConfiguration.class,
			CallbackDeliveryRepositoryConfiguration.class,
			ExternalGatewayPostgresConfiguration.class,
			PostgresSlotNotificationConfiguration.class
	})
	private static class MemoryRepositoryConfiguration {
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ExternalGatewayPostgresProperties.class)
	@Import({
			SlotRepositoryConfiguration.class,
			AsyncTaskRepositoryConfiguration.class,
			CallbackDeliveryRepositoryConfiguration.class
	})
	private static class PostgresRepositoryConfiguration {

		@Bean
		DataSource externalGatewayDataSource() {
			return new DriverManagerDataSource("jdbc:postgresql://localhost:5432/external_gateway");
		}

		@Bean
		NamedParameterJdbcTemplate externalGatewayNamedParameterJdbcTemplate(DataSource externalGatewayDataSource) {
			return new NamedParameterJdbcTemplate(externalGatewayDataSource);
		}

		@Bean
		PlatformTransactionManager externalGatewayTransactionManager(DataSource externalGatewayDataSource) {
			return new DataSourceTransactionManager(externalGatewayDataSource);
		}

		@Bean
		TransactionTemplate externalGatewayTransactionTemplate(
				PlatformTransactionManager externalGatewayTransactionManager
		) {
			return new TransactionTemplate(externalGatewayTransactionManager);
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}
}
