package com.example.testqwencli.gateway.slot;

import java.time.Duration;

@FunctionalInterface
public interface SlotAcquireSleeper {

	/**
	 * Приостанавливает ожидание между попытками занять sync-слот.
	 *
	 * @param duration длительность паузы
	 * @throws InterruptedException если поток прерван во время ожидания
	 */
	void sleep(Duration duration) throws InterruptedException;
}
