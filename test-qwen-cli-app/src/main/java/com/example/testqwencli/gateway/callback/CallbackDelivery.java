package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.async.CallbackDeliveryStatus;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CallbackDelivery(
		UUID deliveryId,
		CallbackPayload payload,
		URI callbackUrl,
		CallbackDeliveryStatus status,
		int attempt,
		int maxAttempts,
		Instant createdAt,
		Instant availableAt,
		Instant startedAt,
		Instant completedAt,
		String lastError
) {

	public CallbackDelivery {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(availableAt, "availableAt must not be null");
		if (status != CallbackDeliveryStatus.DEAD) {
			Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
		}
		if (callbackUrl != null && !callbackUrl.isAbsolute()) {
			throw new IllegalArgumentException("callbackUrl должен быть абсолютным URI");
		}
		if (attempt < 0) {
			throw new IllegalArgumentException("attempt не должен быть отрицательным");
		}
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
	}

	public long taskId() {
		return payload.taskId();
	}

	public String clientService() {
		return payload.clientService();
	}
}
