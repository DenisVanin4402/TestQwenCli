package com.example.testqwencli.gateway.async;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record ExternalAsyncRequest(
		@NotNull(message = "externalId обязателен") UUID externalId,
		@NotBlank(message = "clientService обязателен")
		@Size(min = 2, max = 80, message = "clientService должен содержать от 2 до 80 символов")
		String clientService,
		@NotNull(message = "priority обязателен") AsyncPriority priority,
		AsyncDeliveryMode deliveryMode,
		@NotNull(message = "payload обязателен") Map<String, Object> payload
) {

	public ExternalAsyncRequest {
		if (deliveryMode == null) {
			deliveryMode = AsyncDeliveryMode.CALLBACK;
		}
		if (deliveryMode == AsyncDeliveryMode.SYNC) {
			throw new IllegalArgumentException("deliveryMode=SYNC используется только для внутренних trace-записей");
		}
		if (payload != null) {
			payload = AsyncPayloads.copyMap(payload);
		}
	}
}
