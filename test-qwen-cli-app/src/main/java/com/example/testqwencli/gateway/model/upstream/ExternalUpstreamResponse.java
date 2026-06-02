package com.example.testqwencli.gateway.model.upstream;

import java.util.Map;

/**
 * Внутренний ответ upstream adapter.
 *
 * @param result результат upstream в виде строковой map для стабильной сериализации
 * @param upstreamStatus статус upstream-вызова
 * @param upstreamTraceId id трассировки upstream, если доступен
 */
public record ExternalUpstreamResponse(
		Map<String, String> result,
		int upstreamStatus,
		String upstreamTraceId
) {

	public ExternalUpstreamResponse {
		if (result == null) {
			throw new IllegalArgumentException("Результат upstream не должен быть null");
		}
		result = Map.copyOf(result);
	}
}
