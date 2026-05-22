package com.example.testqwencli.gateway.sync.upstream;

import java.util.Map;

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
