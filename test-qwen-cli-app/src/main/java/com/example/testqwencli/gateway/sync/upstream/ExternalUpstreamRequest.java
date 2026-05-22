package com.example.testqwencli.gateway.sync.upstream;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
