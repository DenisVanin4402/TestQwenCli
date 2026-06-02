package com.example.testqwencli.dashboard;

/**
 * Поставщик технического health-снимка gateway для dashboard backend.
 */
public interface DashboardHealthProvider {

	/**
	 * Собирает актуальное состояние слотов, очередей и dispatcher-ов.
	 *
	 * @return snapshot, пригодный для отображения в UI и API дашборда.
	 */
	DashboardHealthSnapshot snapshot();
}
