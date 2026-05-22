package com.example.testqwencli.gateway.async;

import java.util.Objects;

public record AsyncTaskUpdateResult(
		AsyncTaskUpdateStatus status,
		AsyncTask task,
		String message
) {

	public AsyncTaskUpdateResult {
		Objects.requireNonNull(status, "status must not be null");
	}

	public static AsyncTaskUpdateResult updated(AsyncTask task) {
		Objects.requireNonNull(task, "task must not be null");
		return new AsyncTaskUpdateResult(AsyncTaskUpdateStatus.UPDATED, task, null);
	}

	public static AsyncTaskUpdateResult notFound() {
		return new AsyncTaskUpdateResult(AsyncTaskUpdateStatus.NOT_FOUND, null, null);
	}

	public static AsyncTaskUpdateResult conflict(AsyncTask task, String message) {
		Objects.requireNonNull(task, "task must not be null");
		Objects.requireNonNull(message, "message must not be null");
		return new AsyncTaskUpdateResult(AsyncTaskUpdateStatus.CONFLICT, task, message);
	}
}
