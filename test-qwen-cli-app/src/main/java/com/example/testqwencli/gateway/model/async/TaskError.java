package com.example.testqwencli.gateway.model.async;

import java.util.Objects;

/**
 * Структурированное описание ошибки задачи.
 *
 * <p>Используется в async task, sync trace и callback payload, чтобы потребитель
 * видел стабильный машинный код ошибки, человекочитаемое сообщение и признак
 * возможности повторить операцию вручную.</p>
 *
 * @param code стабильный код ошибки
 * @param message человекочитаемое описание
 * @param retryable можно ли безопасно предложить manual retry
 */
public record TaskError(
		String code,
		String message,
		boolean retryable
) {

	public TaskError {
		Objects.requireNonNull(code, "code must not be null");
		Objects.requireNonNull(message, "message must not be null");
		if (code.isBlank()) {
			throw new IllegalArgumentException("Код ошибки задачи не должен быть пустым");
		}
		if (message.isBlank()) {
			throw new IllegalArgumentException("Сообщение ошибки задачи не должно быть пустым");
		}
	}
}
