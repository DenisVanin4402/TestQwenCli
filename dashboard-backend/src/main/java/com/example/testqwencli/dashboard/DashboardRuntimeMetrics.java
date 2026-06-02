package com.example.testqwencli.dashboard;

/**
 * Накопленные runtime-метрики нагрузочного дашборда и ручных dispatch-операций.
 *
 * @param syncSuccess количество успешных sync-вызовов.
 * @param syncNoSlot количество sync-вызовов, отклоненных из-за отсутствия слота.
 * @param syncTimeout количество sync-вызовов, завершившихся таймаутом.
 * @param syncErrors количество sync-вызовов, завершившихся прочими ошибками.
 * @param asyncAccepted количество принятых async-submit запросов.
 * @param asyncRejected количество корректно отклоненных async-submit запросов.
 * @param asyncErrors количество async-submit запросов, завершившихся технической ошибкой.
 * @param asyncDispatchIterations количество запусков async dispatcher-а из дашборда или scheduler-а.
 * @param callbackDispatchIterations количество запусков callback dispatcher-а из дашборда или scheduler-а.
 * @param expiredLeases количество slot lease, которые были освобождены как протухшие.
 * @param activeSyncRequests количество sync-запросов, которые дашборд сейчас выполняет.
 * @param activeAsyncSubmits количество async-submit запросов, которые дашборд сейчас выполняет.
 * @param actualSyncRps фактически измеренный sync RPS за последний интервал.
 * @param actualAsyncRps фактически измеренный async-submit RPS за последний интервал.
 * @param p50LatencyMs медианная длительность sync-вызовов.
 * @param p95LatencyMs 95-й перцентиль длительности sync-вызовов.
 * @param p99LatencyMs 99-й перцентиль длительности sync-вызовов.
 */
public record DashboardRuntimeMetrics(
		long syncSuccess,
		long syncNoSlot,
		long syncTimeout,
		long syncErrors,
		long asyncAccepted,
		long asyncRejected,
		long asyncErrors,
		long asyncDispatchIterations,
		long callbackDispatchIterations,
		long expiredLeases,
		int activeSyncRequests,
		int activeAsyncSubmits,
		long actualSyncRps,
		long actualAsyncRps,
		long p50LatencyMs,
		long p95LatencyMs,
		long p99LatencyMs
) {
}
