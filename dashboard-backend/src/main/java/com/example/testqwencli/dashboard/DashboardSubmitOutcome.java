package com.example.testqwencli.dashboard;

import com.example.testqwencli.dashboard.enums.DashboardSubmitStatus;
/**
 * Результат одного async-submit вызова gateway из нагрузочного дашборда.
 *
 * @param status нормализованный статус submit-операции.
 * @param durationMs полная длительность submit-вызова в миллисекундах.
 * @param code технический код ответа или ошибки; используется для диагностики отклонений.
 * @param taskId идентификатор созданной или переиспользованной async-задачи; {@code null}, если submit не принят.
 */
public record DashboardSubmitOutcome(
		DashboardSubmitStatus status,
		long durationMs,
		String code,
		Long taskId
) {
}
