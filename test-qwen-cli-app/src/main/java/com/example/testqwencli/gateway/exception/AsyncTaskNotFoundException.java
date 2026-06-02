package com.example.testqwencli.gateway.exception;

import com.example.testqwencli.gateway.exception.ExternalGatewayException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class AsyncTaskNotFoundException extends ExternalGatewayException {

	public AsyncTaskNotFoundException(String requestId, long taskId) {
		super(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Async-задача не найдена", false, requestId,
				Map.of("taskId", taskId));
	}

	public AsyncTaskNotFoundException(String requestId, UUID externalId, String clientService) {
		super(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Async-задача не найдена", false, requestId,
				details(externalId, clientService));
	}

	private static Map<String, Object> details(UUID externalId, String clientService) {
		LinkedHashMap<String, Object> details = new LinkedHashMap<>();
		details.put("externalId", externalId.toString());
		if (clientService != null) {
			details.put("clientService", clientService);
		}
		return Map.copyOf(details);
	}
}
