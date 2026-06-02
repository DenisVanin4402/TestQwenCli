package com.example.testqwencli.gateway.model.sync;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP-ответ синхронного gateway API после upstream-вызова.
 *
 * @param externalId внешний id исходной операции
 * @param status статус sync-вызова на уровне gateway
 * @param result результат upstream
 * @param upstreamStatus HTTP-статус/код upstream-адаптера
 * @param durationMs длительность обработки в миллисекундах
 * @param upstreamTraceId id трассировки upstream, если он был возвращен
 */
public record ExternalSyncResponse(
		UUID externalId,
		ExternalSyncStatus status,
		Map<String, String> result,
		int upstreamStatus,
		long durationMs,
		String upstreamTraceId
) {

	public ExternalSyncResponse {
		if (result == null) {
			throw new IllegalArgumentException("Результат sync-вызова не должен быть null");
		}
		if (durationMs < 0) {
			throw new IllegalArgumentException("Длительность sync-вызова не должна быть отрицательной");
		}
		result = Map.copyOf(result);
	}
}
