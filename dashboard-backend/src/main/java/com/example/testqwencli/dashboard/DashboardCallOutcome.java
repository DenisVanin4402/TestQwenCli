package com.example.testqwencli.dashboard;

/**
 * Результат одного синхронного вызова gateway из нагрузочного дашборда.
 *
 * @param status нормализованный статус вызова для подсчета метрик.
 * @param durationMs полная длительность вызова в миллисекундах.
 * @param code технический код ответа или ошибки; например код отказа gateway, HTTP-статус или краткая причина
 * исключения.
 */
public record DashboardCallOutcome(
		DashboardCallStatus status,
		long durationMs,
		String code
) {
}
