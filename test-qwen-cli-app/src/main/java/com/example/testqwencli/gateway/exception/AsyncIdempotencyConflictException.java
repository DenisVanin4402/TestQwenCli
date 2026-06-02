package com.example.testqwencli.gateway.exception;

import com.example.testqwencli.gateway.exception.ExternalGatewayException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class AsyncIdempotencyConflictException extends ExternalGatewayException {

	public AsyncIdempotencyConflictException(String requestId, long existingTaskId, List<String> conflictingFields) {
		super(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
				"Задача с таким clientService и externalId уже существует с другими параметрами", false, requestId,
				Map.of("existingTaskId", existingTaskId, "conflictingFields", List.copyOf(conflictingFields)));
	}
}
