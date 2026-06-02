package com.example.testqwencli.gateway.services;

/**
 * Публикатор события об освобождении слота.
 *
 * <p>PostgreSQL реализация отправляет {@code NOTIFY}, in-memory или fallback режим
 * может использовать локальное уведомление ожидающих sync-запросов.</p>
 */
@FunctionalInterface
public interface SlotReleaseNotificationPublisher {

	/**
	 * Публикует сигнал, что один или несколько слотов могли освободиться.
	 */
	void publishSlotReleased();

	/**
	 * @return публикатор, который ничего не делает
	 */
	static SlotReleaseNotificationPublisher noop() {
		return () -> {
		};
	}
}
