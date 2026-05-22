package com.example.testqwencli.gateway.sync;

import java.util.Map;
import java.util.UUID;

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
