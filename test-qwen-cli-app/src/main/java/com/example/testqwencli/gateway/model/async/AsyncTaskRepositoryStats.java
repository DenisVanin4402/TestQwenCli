package com.example.testqwencli.gateway.model.async;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Снимок статистики async-очереди для dashboard/health.
 *
 * @param statusCounts количество задач по каждому {@link AsyncTaskStatus}
 * @param retryCount количество задач, которые уже имели попытки и снова ждут обработки
 * @param oldestActiveCreatedAt время создания самой старой активной задачи
 */
public record AsyncTaskRepositoryStats(
		Map<AsyncTaskStatus, Long> statusCounts,
		long retryCount,
		Instant oldestActiveCreatedAt
) {

	public AsyncTaskRepositoryStats {
		Objects.requireNonNull(statusCounts, "statusCounts must not be null");
		EnumMap<AsyncTaskStatus, Long> copy = new EnumMap<>(AsyncTaskStatus.class);
		for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
			copy.put(status, statusCounts.getOrDefault(status, 0L));
		}
		statusCounts = Map.copyOf(copy);
	}

	/**
	 * Возвращает количество задач в указанном статусе.
	 *
	 * @param status статус async-задачи
	 * @return счетчик по статусу или {@code 0}, если статус отсутствует в map
	 */
	public long count(AsyncTaskStatus status) {
		return statusCounts.getOrDefault(status, 0L);
	}
}
