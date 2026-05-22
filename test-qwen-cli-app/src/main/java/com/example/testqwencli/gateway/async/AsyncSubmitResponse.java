package com.example.testqwencli.gateway.async;

import java.util.Objects;
import java.util.UUID;

public record AsyncSubmitResponse(
		long taskId,
		UUID externalId,
		AsyncTaskStatus status,
		AsyncDeliveryMode deliveryMode,
		String statusUrl,
		boolean alreadyExisted
) {

	public AsyncSubmitResponse {
		if (taskId < 1) {
			throw new IllegalArgumentException("taskId должен быть положительным");
		}
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(deliveryMode, "deliveryMode must not be null");
		Objects.requireNonNull(statusUrl, "statusUrl must not be null");
		if (statusUrl.isBlank()) {
			throw new IllegalArgumentException("statusUrl не должен быть пустым");
		}
	}

	public static AsyncSubmitResponse from(AsyncTask task, boolean alreadyExisted) {
		return new AsyncSubmitResponse(task.taskId(), task.externalId(), task.status(), task.deliveryMode(),
				"/v1/external/async/" + task.taskId(), alreadyExisted);
	}
}
