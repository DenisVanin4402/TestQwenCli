package com.example.testqwencli.gateway.model.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Входной HTTP-запрос синхронного gateway API.
 *
 * <p>Запрос пытается получить sync-слот, вызывает upstream и возвращает результат
 * в рамках того же HTTP-ответа. В отличие от async API, sync-запросы не используют
 * idempotency-key на уровне очереди, но оставляют trace-запись в {@code ext_request_queue}.</p>
 *
 * @param externalId внешний id операции, заданный клиентом
 * @param clientService имя сервиса-клиента
 * @param payload JSON payload для upstream
 */
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
