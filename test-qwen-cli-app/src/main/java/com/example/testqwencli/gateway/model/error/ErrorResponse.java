package com.example.testqwencli.gateway.model.error;

import java.util.Map;

/**
 * Единая модель ошибки HTTP API gateway.
 *
 * @param code стабильный машинный код ошибки
 * @param message человекочитаемое описание
 * @param retryable можно ли клиенту повторить запрос
 * @param requestId id запроса, в котором произошла ошибка
 * @param details дополнительные поля для диагностики
 */
public record ErrorResponse(
		String code,
		String message,
		boolean retryable,
		String requestId,
		Map<String, Object> details
) {
}
