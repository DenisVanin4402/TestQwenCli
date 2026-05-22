package com.example.testqwencli.gateway.async;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AsyncTask(
		long taskId,
		UUID externalId,
		String clientService,
		AsyncPriority priority,
		AsyncDeliveryMode deliveryMode,
		AsyncTaskStatus status,
		CallbackDeliveryStatus callbackDeliveryStatus,
		Map<String, Object> result,
		TaskError error,
		int attempts,
		int maxAttempts,
		Instant createdAt,
		Instant availableAt,
		Instant startedAt,
		Instant finishedAt,
		String lastError,
		boolean retryable
) {

	public AsyncTask {
		if (taskId < 1) {
			throw new IllegalArgumentException("taskId должен быть положительным");
		}
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(priority, "priority must not be null");
		Objects.requireNonNull(deliveryMode, "deliveryMode must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(callbackDeliveryStatus, "callbackDeliveryStatus must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		if (clientService.isBlank()) {
			throw new IllegalArgumentException("clientService не должен быть пустым");
		}
		if (attempts < 0) {
			throw new IllegalArgumentException("attempts не должен быть отрицательным");
		}
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
		if (result != null) {
			result = AsyncPayloads.copyMap(result);
		}
	}
}
