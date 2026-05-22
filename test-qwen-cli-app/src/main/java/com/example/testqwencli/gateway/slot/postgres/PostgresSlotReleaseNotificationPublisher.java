package com.example.testqwencli.gateway.slot.postgres;

import com.example.testqwencli.gateway.slot.SlotReleaseNotificationPublisher;
import com.example.testqwencli.gateway.slot.SyncSlotReleaseNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

public final class PostgresSlotReleaseNotificationPublisher implements SlotReleaseNotificationPublisher {

	private static final Logger log = LoggerFactory.getLogger(PostgresSlotReleaseNotificationPublisher.class);

	private final NamedParameterJdbcTemplate jdbc;
	private final SyncSlotReleaseNotifier localNotifier;

	public PostgresSlotReleaseNotificationPublisher(
			NamedParameterJdbcTemplate jdbc,
			SyncSlotReleaseNotifier localNotifier
	) {
		this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
		this.localNotifier = Objects.requireNonNull(localNotifier, "localNotifier must not be null");
	}

	@Override
	public void publishSlotReleased() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					publishNow();
				}
			});
			return;
		}
		publishNow();
	}

	private void publishNow() {
		try {
			jdbc.getJdbcOperations().execute("NOTIFY " + PostgresSlotNotificationChannels.SLOT_RELEASED);
		}
		catch (DataAccessException exception) {
			log.warn("Не удалось отправить PostgreSQL NOTIFY об освобождении sync-слота", exception);
		}
		finally {
			localNotifier.notifySlotReleased();
		}
	}
}
