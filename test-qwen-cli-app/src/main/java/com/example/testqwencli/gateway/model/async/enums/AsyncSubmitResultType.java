package com.example.testqwencli.gateway.model.async.enums;

/**
 * Тип результата submit-операции в async repository.
 */
public enum AsyncSubmitResultType {
	/**
	 * Задача принята: создана новая запись.
	 */
	SUBMITTED,
	/**
	 * DB/memory guard отклонил duplicate по clientService + externalId до подключения @Idempotent.
	 */
	DUPLICATE_REJECTED
}
