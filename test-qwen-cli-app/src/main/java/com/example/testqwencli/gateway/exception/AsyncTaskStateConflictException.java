package com.example.testqwencli.gateway.exception;

import com.example.testqwencli.gateway.exception.ExternalGatewayException;
import com.example.testqwencli.gateway.model.async.AsyncTaskStatus;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class AsyncTaskStateConflictException extends ExternalGatewayException {

	public AsyncTaskStateConflictException(String requestId, long taskId, AsyncTaskStatus currentStatus,
			String message) {
		super(HttpStatus.CONFLICT, "TASK_STATE_CONFLICT", message, false, requestId,
				Map.of("taskId", taskId, "currentStatus", currentStatus.name()));
	}
}
