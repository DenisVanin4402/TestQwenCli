package com.example.testqwencli.gateway.model.slot.enums;

/**
 * Стратегия ожидания освобождения sync-слота.
 */
public enum SyncAcquireWaitMode {

	/**
	 * Повторная попытка через фиксированный короткий sleep.
	 */
	POLLING,
	/**
	 * Ожидание PostgreSQL {@code LISTEN/NOTIFY} с fallback timeout.
	 */
	LISTEN_NOTIFY
}
