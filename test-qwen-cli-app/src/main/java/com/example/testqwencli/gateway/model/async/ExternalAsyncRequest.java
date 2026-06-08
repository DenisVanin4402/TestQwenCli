package com.example.testqwencli.gateway.model.async;

import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * Входной HTTP-запрос на постановку async-задачи.
 *
 * <p>{@code externalId + clientService} образуют idempotency-key для async-режимов.
 * Если {@code deliveryMode} не указан, используется {@link AsyncDeliveryMode#CALLBACK}.
 * Режим {@link AsyncDeliveryMode#SYNC} запрещен для внешнего API и применяется только
 * для внутренних журнальных trace-записей.</p>
 *
 * @param externalId внешний id операции, заданный клиентом
 * @param clientService имя сервиса-клиента
 * @param priority приоритет обработки
 * @param deliveryMode способ получения результата
 * @param payload JSON payload для upstream
 */
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
