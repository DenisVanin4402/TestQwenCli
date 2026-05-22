package com.example.testqwencli.gateway.sync.error;

import java.util.Map;

public record ErrorResponse(
		String code,
		String message,
		boolean retryable,
		String requestId,
		Map<String, Object> details
) {
}
