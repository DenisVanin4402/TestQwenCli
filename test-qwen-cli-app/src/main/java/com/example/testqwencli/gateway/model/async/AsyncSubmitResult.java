package com.example.testqwencli.gateway.model.async;

import com.example.testqwencli.gateway.model.async.enums.AsyncSubmitResultType;
import java.util.Objects;

/**
 * Внутренний результат операции submit в репозитории async-задач.
 *
 * <p>CR003-T001 удаляет ручной request-level idempotency алгоритм из repository layer.
 * До подключения {@code @Idempotent} repository либо создает новую задачу, либо сообщает,
 * что нижний DB/memory guard отклонил duplicate key.</p>
 *
 * @param type тип результата submit
 * @param task созданная задача для успешного результата
 */
public record AsyncSubmitResult(
		AsyncSubmitResultType type,
		AsyncTask task
) {

	public AsyncSubmitResult {
		Objects.requireNonNull(type, "type must not be null");
		if (type == AsyncSubmitResultType.SUBMITTED) {
			Objects.requireNonNull(task, "task must not be null for submitted result");
		}
		if (type == AsyncSubmitResultType.DUPLICATE_REJECTED && task != null) {
			throw new IllegalArgumentException("Duplicate rejection не должен содержать task");
		}
	}

	/**
	 * Успешный submit: новая задача создана.
	 *
	 * @param task созданная задача
	 * @return результат типа {@link AsyncSubmitResultType#SUBMITTED}
	 */
	public static AsyncSubmitResult submitted(AsyncTask task) {
		Objects.requireNonNull(task, "task must not be null");
		return new AsyncSubmitResult(AsyncSubmitResultType.SUBMITTED, task);
	}

	/**
	 * Duplicate key отклонен нижним DB/memory guard.
	 *
	 * @return результат типа {@link AsyncSubmitResultType#DUPLICATE_REJECTED}
	 */
	public static AsyncSubmitResult duplicateRejected() {
		return new AsyncSubmitResult(AsyncSubmitResultType.DUPLICATE_REJECTED, null);
	}
}
