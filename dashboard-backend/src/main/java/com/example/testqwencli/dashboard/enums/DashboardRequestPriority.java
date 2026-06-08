package com.example.testqwencli.dashboard.enums;

/**
 * Приоритет тестового запроса, который дашборд транслирует в async-приоритет gateway.
 */
public enum DashboardRequestPriority {
	/**
	 * Высокий приоритет: задача должна выбираться dispatcher-ом раньше обычных low-задач.
	 */
	HIGH,

	/**
	 * Обычный приоритет: задача обрабатывается после доступных high-задач.
	 */
	LOW
}
