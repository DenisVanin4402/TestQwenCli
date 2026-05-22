package com.example.testqwencli.gateway.async.error;

import com.example.testqwencli.gateway.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.sync.error.ExternalGatewayException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public final class AsyncTaskStateConflictException extends ExternalGatewayException {

	public AsyncTaskStateConflictException(String requestId, long taskId, AsyncTaskStatus currentStatus,
			String message) {
		super(HttpStatus.CONFLICT, "TASK_STATE_CONFLICT", message, false, requestId,
				Map.of("taskId", taskId, "currentStatus", currentStatus.name()));
	}
}
