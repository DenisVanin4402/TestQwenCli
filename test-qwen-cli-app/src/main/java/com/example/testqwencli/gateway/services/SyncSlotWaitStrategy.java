package com.example.testqwencli.gateway.services;

import java.time.Duration;

/**
 * Стратегия ожидания перед повторной попыткой получить sync-слот.
 */
public interface SyncSlotWaitStrategy {

	/**
	 * Возвращает текущую версию наблюдаемого сигнала.
	 *
	 * @return версия сигнала освобождения слота
	 */
	long currentSignalVersion();

	/**
	 * Ждет до следующей попытки захвата sync-слота.
	 *
	 * @param observedSignalVersion версия сигнала, уже увиденная caller-ом
	 * @param fallbackTimeout максимальное время ожидания
	 * @return версия сигнала после ожидания
	 * @throws InterruptedException если ожидание было прервано
	 */
	long waitBeforeRetry(long observedSignalVersion, Duration fallbackTimeout) throws InterruptedException;
}
