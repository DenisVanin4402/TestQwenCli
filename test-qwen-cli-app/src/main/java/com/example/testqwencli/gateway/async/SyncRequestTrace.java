package com.example.testqwencli.gateway.async;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record SyncRequestTrace(
		UUID externalId,
		String clientService,
		Map<String, Object> payload,
		AsyncTaskStatus status,
		Map<String, Object> result,
		TaskError error,
		int attempts,
		Instant startedAt,
		Instant finishedAt,
		String lastError
) {

	public SyncRequestTrace {
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(startedAt, "startedAt must not be null");
		Objects.requireNonNull(finishedAt, "finishedAt must not be null");
		if (clientService.isBlank()) {
			throw new IllegalArgumentException("clientService не должен быть пустым");
		}
		if (status != AsyncTaskStatus.DONE && status != AsyncTaskStatus.FAILED) {
			throw new IllegalArgumentException("Sync trace поддерживает только DONE или FAILED");
		}
		if (status == AsyncTaskStatus.DONE && result == null) {
			throw new IllegalArgumentException("DONE sync trace должен содержать result");
		}
		if (status == AsyncTaskStatus.FAILED && error == null) {
			throw new IllegalArgumentException("FAILED sync trace должен содержать error");
		}
		if (attempts < 0) {
			throw new IllegalArgumentException("attempts не должен быть отрицательным");
		}
		if (finishedAt.isBefore(startedAt)) {
			throw new IllegalArgumentException("finishedAt не должен быть раньше startedAt");
		}
		payload = AsyncPayloads.copyMap(payload);
		if (result != null) {
			result = AsyncPayloads.copyMap(result);
		}
	}
}
