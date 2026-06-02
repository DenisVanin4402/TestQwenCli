package com.example.testqwencli.gateway.model.async;

/**
 * Тип результата submit-операции в async repository.
 */
public enum AsyncSubmitResultType {
	/**
	 * Задача принята: создана новая запись или возвращена идемпотентно существующая.
	 */
	SUBMITTED,
	/**
	 * Запрос использует уже занятый idempotency-key с отличающимся содержимым.
	 */
	IDEMPOTENCY_CONFLICT
}
