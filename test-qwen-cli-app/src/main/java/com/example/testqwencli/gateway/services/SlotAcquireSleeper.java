package com.example.testqwencli.gateway.services;

import java.time.Duration;

/**
 * Абстракция ожидания между попытками получить sync-слот.
 *
 * <p>Выделена для тестируемости polling strategy: unit-тест может заменить
 * реальный {@link Thread#sleep(long)} контролируемой реализацией.</p>
 */
@FunctionalInterface
public interface SlotAcquireSleeper {

	/**
	 * Приостанавливает текущий поток на указанную длительность.
	 *
	 * @param duration длительность ожидания
	 * @throws InterruptedException если ожидание было прервано
	 */
	void sleep(Duration duration) throws InterruptedException;
}
