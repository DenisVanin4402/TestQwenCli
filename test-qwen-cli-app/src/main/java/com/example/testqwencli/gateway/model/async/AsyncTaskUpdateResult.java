package com.example.testqwencli.gateway.model.async;

import java.util.Objects;

/**
 * Результат командного изменения async-задачи: cancel или manual retry.
 *
 * @param status итог команды на уровне application-service
 * @param task актуальная задача, если она найдена
 * @param message пояснение для conflict-сценария
 */
public record AsyncTaskUpdateResult(
		AsyncTaskUpdateStatus status,
		AsyncTask task,
		String message
) {

	public AsyncTaskUpdateResult {
		Objects.requireNonNull(status, "status must not be null");
	}

	/**
	 * Команда успешно изменила задачу.
	 *
	 * @param task обновленная задача
	 * @return результат со статусом {@link AsyncTaskUpdateStatus#UPDATED}
	 */
	public static AsyncTaskUpdateResult updated(AsyncTask task) {
		Objects.requireNonNull(task, "task must not be null");
		return new AsyncTaskUpdateResult(AsyncTaskUpdateStatus.UPDATED, task, null);
	}

	/**
	 * Задача не найдена с учетом client-service фильтра.
	 *
	 * @return результат со статусом {@link AsyncTaskUpdateStatus#NOT_FOUND}
	 */
	public static AsyncTaskUpdateResult notFound() {
		return new AsyncTaskUpdateResult(AsyncTaskUpdateStatus.NOT_FOUND, null, null);
	}

	/**
	 * Команда запрещена текущим состоянием задачи.
	 *
	 * @param task найденная задача
	 * @param message человекочитаемая причина отказа
	 * @return результат со статусом {@link AsyncTaskUpdateStatus#CONFLICT}
	 */
	public static AsyncTaskUpdateResult conflict(AsyncTask task, String message) {
		Objects.requireNonNull(task, "task must not be null");
		Objects.requireNonNull(message, "message must not be null");
		return new AsyncTaskUpdateResult(AsyncTaskUpdateStatus.CONFLICT, task, message);
	}
}
