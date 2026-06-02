package com.example.testqwencli.gateway.model.upstream;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Внутренний запрос к upstream adapter внешнего сервиса.
 *
 * @param externalId внешний id операции
 * @param clientService имя сервиса-клиента
 * @param payload JSON payload для upstream
 * @param requestId id запроса для трассировки
 * @param idempotencyKey idempotency-key, который нужно передать upstream
 */
public record ExternalUpstreamRequest(
		UUID externalId,
		String clientService,
		Map<String, Object> payload,
		String requestId,
		String idempotencyKey
) {

	public ExternalUpstreamRequest {
		if (payload != null) {
			payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
		}
	}
}
