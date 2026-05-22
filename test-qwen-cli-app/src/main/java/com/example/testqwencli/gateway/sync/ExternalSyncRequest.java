package com.example.testqwencli.gateway.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ExternalSyncRequest(
		@NotNull(message = "externalId обязателен") UUID externalId,
		@NotBlank(message = "clientService обязателен")
		@Size(min = 2, max = 80, message = "clientService должен содержать от 2 до 80 символов")
		String clientService,
		@NotNull(message = "payload обязателен") Map<String, Object> payload
) {

	public ExternalSyncRequest {
		if (payload != null) {
			payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
		}
	}
}
