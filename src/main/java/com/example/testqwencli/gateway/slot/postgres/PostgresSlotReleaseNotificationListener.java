package com.example.testqwencli.gateway.slot.postgres;

import com.example.testqwencli.gateway.slot.SyncSlotReleaseNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PostgresSlotReleaseNotificationListener implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(PostgresSlotReleaseNotificationListener.class);
	private static final Duration NOTIFICATION_WAIT_TIMEOUT = Duration.ofSeconds(1);
	private static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(1);

	private final DataSource dataSource;
	private final SyncSlotReleaseNotifier notifier;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private volatile Thread listenerThread;

	public PostgresSlotReleaseNotificationListener(DataSource dataSource, SyncSlotReleaseNotifier notifier) {
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
		this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
	}

	@Override
	public void start() {
		if (!running.compareAndSet(false, true)) {
			return;
		}
		Thread thread = new Thread(this::runLoop, "external-gateway-slot-release-listener");
		thread.setDaemon(true);
		listenerThread = thread;
		thread.start();
	}

	@Override
	public void stop() {
		if (running.compareAndSet(true, false)) {
			Thread thread = listenerThread;
			if (thread != null) {
				thread.interrupt();
			}
		}
	}

	@Override
	public void stop(Runnable callback) {
		stop();
		callback.run();
	}

	@Override
	public boolean isRunning() {
		return running.get();
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}

	private void runLoop() {
		while (running.get()) {
			try {
				listenUntilStopped();
			}
			catch (Exception exception) {
				if (running.get()) {
					log.warn("PostgreSQL LISTEN/NOTIFY для sync-слотов временно недоступен", exception);
					sleepBeforeReconnect();
				}
			}
		}
	}

	private void listenUntilStopped() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(true);
			listen(connection);
			PgNotificationReader notificationReader = PgNotificationReader.from(connection);
			log.info("PostgreSQL LISTEN активирован для канала {}",
					PostgresSlotNotificationChannels.SLOT_RELEASED);
			while (running.get()) {
				if (notificationReader.waitForSlotRelease(NOTIFICATION_WAIT_TIMEOUT)) {
					notifier.notifySlotReleased();
				}
			}
			unlisten(connection);
		}
	}

	private static void listen(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("LISTEN " + PostgresSlotNotificationChannels.SLOT_RELEASED);
		}
	}

	private static void unlisten(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("UNLISTEN " + PostgresSlotNotificationChannels.SLOT_RELEASED);
		}
	}

	private void sleepBeforeReconnect() {
		try {
			Thread.sleep(RECONNECT_BACKOFF.toMillis());
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class PgNotificationReader {

		private final Object pgConnection;
		private final Method getNotifications;
		private final Method getName;

		private PgNotificationReader(Object pgConnection, Method getNotifications, Method getName) {
			this.pgConnection = pgConnection;
			this.getNotifications = getNotifications;
			this.getName = getName;
		}

		private static PgNotificationReader from(Connection connection) throws SQLException {
			try {
				Class<?> pgConnectionClass = Class.forName("org.postgresql.PGConnection");
				Class<?> pgNotificationClass = Class.forName("org.postgresql.PGNotification");
				Object pgConnection = connection.unwrap(pgConnectionClass);
				Method getNotifications = pgConnectionClass.getMethod("getNotifications", int.class);
				Method getName = pgNotificationClass.getMethod("getName");
				return new PgNotificationReader(pgConnection, getNotifications, getName);
			}
			catch (ClassNotFoundException | NoSuchMethodException exception) {
				throw new IllegalStateException("PostgreSQL JDBC driver не поддерживает LISTEN/NOTIFY", exception);
			}
		}

		private boolean waitForSlotRelease(Duration timeout) throws SQLException {
			Object notifications = invokeGetNotifications(timeout);
			if (notifications == null) {
				return false;
			}
			int count = Array.getLength(notifications);
			for (int index = 0; index < count; index++) {
				Object notification = Array.get(notifications, index);
				if (PostgresSlotNotificationChannels.SLOT_RELEASED.equals(notificationName(notification))) {
					return true;
				}
			}
			return false;
		}

		private Object invokeGetNotifications(Duration timeout) throws SQLException {
			try {
				return getNotifications.invoke(pgConnection, toTimeoutMillis(timeout));
			}
			catch (IllegalAccessException exception) {
				throw new IllegalStateException("Не удалось прочитать PostgreSQL notifications", exception);
			}
			catch (InvocationTargetException exception) {
				Throwable cause = exception.getCause();
				if (cause instanceof SQLException sqlException) {
					throw sqlException;
				}
				throw new IllegalStateException("Не удалось прочитать PostgreSQL notifications", cause);
			}
		}

		private String notificationName(Object notification) {
			try {
				return (String) getName.invoke(notification);
			}
			catch (IllegalAccessException exception) {
				throw new IllegalStateException("Не удалось прочитать имя PostgreSQL notification", exception);
			}
			catch (InvocationTargetException exception) {
				throw new IllegalStateException("Не удалось прочитать имя PostgreSQL notification",
						exception.getCause());
			}
		}

		private static int toTimeoutMillis(Duration timeout) {
			long millis = timeout.toMillis();
			if (millis < 1) {
				return 1;
			}
			if (millis > Integer.MAX_VALUE) {
				return Integer.MAX_VALUE;
			}
			return (int) millis;
		}
	}
}
