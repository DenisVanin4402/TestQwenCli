package com.example.testqwencli.gateway.async;

import java.util.List;
import java.util.Objects;

public record AsyncSubmitResult(
		AsyncSubmitResultType type,
		AsyncTask task,
		long existingTaskId,
		List<String> conflictingFields,
		boolean alreadyExisted
) {

	public AsyncSubmitResult {
		Objects.requireNonNull(type, "type must not be null");
		conflictingFields = conflictingFields == null ? List.of() : List.copyOf(conflictingFields);
	}

	public static AsyncSubmitResult submitted(AsyncTask task, boolean alreadyExisted) {
		Objects.requireNonNull(task, "task must not be null");
		return new AsyncSubmitResult(AsyncSubmitResultType.SUBMITTED, task, task.taskId(), List.of(), alreadyExisted);
	}

	public static AsyncSubmitResult idempotencyConflict(long existingTaskId, List<String> conflictingFields) {
		if (existingTaskId < 1) {
			throw new IllegalArgumentException("existingTaskId должен быть положительным");
		}
		return new AsyncSubmitResult(AsyncSubmitResultType.IDEMPOTENCY_CONFLICT, null, existingTaskId,
				conflictingFields, true);
	}
}
