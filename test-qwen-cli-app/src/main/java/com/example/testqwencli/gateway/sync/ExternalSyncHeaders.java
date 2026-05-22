package com.example.testqwencli.gateway.sync;

public record ExternalSyncHeaders(String requestId, String idempotencyKey) {

	public ExternalSyncHeaders {
		requestId = normalize(requestId);
		idempotencyKey = normalize(idempotencyKey);
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
