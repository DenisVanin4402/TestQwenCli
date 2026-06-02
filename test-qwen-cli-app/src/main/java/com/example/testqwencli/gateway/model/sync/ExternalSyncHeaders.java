package com.example.testqwencli.gateway.model.sync;

/**
 * Технические заголовки sync-запроса.
 *
 * @param requestId id входящего HTTP-запроса для трассировки и ошибок
 * @param idempotencyKey внешний idempotency-key, прокидываемый в upstream
 */
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
