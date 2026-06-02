package com.example.testqwencli.gateway.services;

import java.time.Duration;

/**
 * Локальный сигнализатор освобождения sync-слотов.
 *
 * <p>Используется как быстрый in-process канал для потоков, которые ждут слот,
 * и как fallback поверх PostgreSQL {@code LISTEN/NOTIFY}.</p>
 */
public interface SyncSlotReleaseNotifier {

	/**
	 * Возвращает текущую версию сигнала.
	 *
	 * @return монотонно растущая версия уведомлений
	 */
	long currentSignalVersion();

	/**
	 * Ждет следующего сигнала или истечения fallback timeout.
	 *
	 * @param observedSignalVersion версия, которую поток уже видел
	 * @param fallbackTimeout максимальное время ожидания без сигнала
	 * @return новая версия сигнала либо прежняя версия после timeout
	 * @throws InterruptedException если ожидание было прервано
	 */
	long awaitNextSignal(long observedSignalVersion, Duration fallbackTimeout) throws InterruptedException;

	/**
	 * Уведомляет ожидающие потоки, что слот мог освободиться.
	 */
	void notifySlotReleased();
}
