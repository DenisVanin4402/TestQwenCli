package com.example.testqwencli.dashboard;

/**
 * Хранилище текущих настроек симуляции, которыми UI управляет во время нагрузочного теста.
 */
public interface DashboardSimulationSettingsStore {

	/**
	 * Возвращает активные настройки задержек, ошибок и размера ответов.
	 *
	 * @return текущая конфигурация симуляции.
	 */
	DashboardSimulationSettings current();

	/**
	 * Заменяет активные настройки симуляции.
	 *
	 * @param settings новые настройки, прошедшие валидацию на уровне controller-а.
	 * @return сохраненные настройки.
	 */
	DashboardSimulationSettings update(DashboardSimulationSettings settings);
}
