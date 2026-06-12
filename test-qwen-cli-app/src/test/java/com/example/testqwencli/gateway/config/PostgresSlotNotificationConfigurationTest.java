package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.config.PostgresSlotNotificationConfiguration;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotReleaseNotificationListener;
import com.example.testqwencli.gateway.repository.postgres.PostgresSlotReleaseNotificationPublisher;
import com.example.testqwencli.gateway.services.impl.LocalSyncSlotReleaseNotifier;
import com.example.testqwencli.gateway.services.SyncSlotReleaseNotifier;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresSlotNotificationConfigurationTest {

	private static final String SLOT_RELEASED_CHANNEL = "external_gateway_slot_released";

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
						() -> new NamedParameterJdbcTemplate(silentDataSource()))
				.withBean("externalGatewayDataSource", DataSource.class, this::silentDataSource)
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

	private DataSource silentDataSource() {
		return new SilentNotificationDataSource();
	}

	private static final class SilentNotificationDataSource implements DataSource {

		@Override
		public Connection getConnection() {
			return connection();
		}

		@Override
		public Connection getConnection(String username, String password) {
			return connection();
		}

		@Override
		public java.io.PrintWriter getLogWriter() {
			return null;
		}

		@Override
		public void setLogWriter(java.io.PrintWriter out) {
		}

		@Override
		public void setLoginTimeout(int seconds) {
		}

		@Override
		public int getLoginTimeout() {
			return 0;
		}

		@Override
		public java.util.logging.Logger getParentLogger() {
			return java.util.logging.Logger.getGlobal();
		}

		@Override
		public <T> T unwrap(Class<T> iface) throws SQLException {
			throw new SQLException("DataSource не поддерживает unwrap в тесте");
		}

		@Override
		public boolean isWrapperFor(Class<?> iface) {
			return false;
		}

		private static Connection connection() {
			PGConnection pgConnection = pgConnection();
			return (Connection) Proxy.newProxyInstance(
					Connection.class.getClassLoader(),
					new Class<?>[] {Connection.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "setAutoCommit" -> null;
						case "createStatement" -> statement();
						case "unwrap" -> {
							Class<?> type = (Class<?>) args[0];
							if (type == PGConnection.class) {
								yield pgConnection;
							}
							throw new SQLException("Connection не поддерживает unwrap " + type.getName());
						}
						case "isWrapperFor" -> args[0] == PGConnection.class;
						case "close" -> null;
						case "isClosed" -> false;
						case "toString" -> "silent-notification-connection";
						default -> throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		private static PGConnection pgConnection() {
			return (PGConnection) Proxy.newProxyInstance(
					PGConnection.class.getClassLoader(),
					new Class<?>[] {PGConnection.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "getNotifications" -> {
							sleepBriefly();
							yield new PGNotification[0];
						}
						case "toString" -> "silent-pg-connection";
						default -> throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		private static Statement statement() {
			return (Statement) Proxy.newProxyInstance(
					Statement.class.getClassLoader(),
					new Class<?>[] {Statement.class},
					(proxy, method, args) -> switch (method.getName()) {
						case "execute" -> ((String) args[0]).contains(SLOT_RELEASED_CHANNEL);
						case "close" -> null;
						case "toString" -> "silent-notification-statement";
						default -> throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		private static void sleepBriefly() {
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
