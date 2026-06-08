package com.example.testqwencli.gateway.model.async.enums;

/**
 * Состояние обработки задачи из {@code ext_request_queue}.
 */
public enum AsyncTaskStatus {
	/**
	 * Задача ожидает первой обработки или retry после {@code availableAt}.
	 */
	PENDING,
	/**
	 * Dispatcher забрал задачу и выполняет upstream-вызов.
	 */
	IN_PROGRESS,
	/**
	 * Upstream успешно завершился, результат записан в {@code result}.
	 */
	DONE,
	/**
	 * Финальная бизнес-ошибка без автоматического retry.
	 */
	FAILED,
	/**
	 * Попытки исчерпаны после transient-ошибок.
	 */
	DEAD,
	/**
	 * Клиент отменил задачу до начала обработки.
	 */
	CANCELLED
}
