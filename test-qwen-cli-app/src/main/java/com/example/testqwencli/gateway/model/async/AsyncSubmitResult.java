package com.example.testqwencli.gateway.model.async;

import java.util.List;
import java.util.Objects;

/**
 * Внутренний результат операции submit в репозитории async-задач.
 *
 * <p>В отличие от HTTP response, эта модель различает успешное принятие задачи
 * и конфликт идемпотентности. При конфликте {@link #task()} отсутствует, а
 * {@link #conflictingFields()} содержит список полей запроса, которые отличаются
 * от уже сохраненной задачи.</p>
 *
 * @param type тип результата submit
 * @param task созданная или найденная задача для успешного результата
 * @param existingTaskId id существующей задачи, полезен для conflict response
 * @param conflictingFields поля, нарушившие идемпотентность
 * @param alreadyExisted {@code true}, если задача уже существовала до submit
 */
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

	/**
	 * Успешный submit: новая задача создана либо безопасно переиспользована.
	 *
	 * @param task созданная или существующая задача
	 * @param alreadyExisted {@code true}, если задача была найдена по idempotency-key
	 * @return результат типа {@link AsyncSubmitResultType#SUBMITTED}
	 */
	public static AsyncSubmitResult submitted(AsyncTask task, boolean alreadyExisted) {
		Objects.requireNonNull(task, "task must not be null");
		return new AsyncSubmitResult(AsyncSubmitResultType.SUBMITTED, task, task.taskId(), List.of(), alreadyExisted);
	}

	/**
	 * Конфликт идемпотентности: ключ совпал, но содержимое запроса отличается.
	 *
	 * @param existingTaskId id уже существующей задачи
	 * @param conflictingFields список отличающихся полей
	 * @return результат типа {@link AsyncSubmitResultType#IDEMPOTENCY_CONFLICT}
	 */
	public static AsyncSubmitResult idempotencyConflict(long existingTaskId, List<String> conflictingFields) {
		if (existingTaskId < 1) {
			throw new IllegalArgumentException("existingTaskId должен быть положительным");
		}
		return new AsyncSubmitResult(AsyncSubmitResultType.IDEMPOTENCY_CONFLICT, null, existingTaskId,
				conflictingFields, true);
	}
}
