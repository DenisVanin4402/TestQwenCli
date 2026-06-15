package com.example.testqwencli.gateway.exception;

import org.springframework.http.HttpStatus;

public final class AsyncIdempotencyConflictException extends ExternalGatewayException {

	public AsyncIdempotencyConflictException(String requestId) {
		super(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
				"Задача с таким clientService и externalId уже существует", false, requestId, null);
	}
}
