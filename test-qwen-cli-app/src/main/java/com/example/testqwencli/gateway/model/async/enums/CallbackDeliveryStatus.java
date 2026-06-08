package com.example.testqwencli.gateway.model.async.enums;

/**
 * Состояние callback-доставки, связанной с async-задачей.
 *
 * <p>Значение хранится как в {@code ext_request_queue.callback_delivery_status},
 * так и в {@code ext_callback_delivery.status}. Для polling и sync trace используется
 * {@link #NOT_REQUIRED}.</p>
 */
public enum CallbackDeliveryStatus {
	/**
	 * Callback для задачи не требуется.
	 */
	NOT_REQUIRED,
	/**
	 * Доставка ожидает отправки.
	 */
	PENDING,
	/**
	 * Dispatcher забрал доставку и выполняет HTTP-вызов.
	 */
	DELIVERING,
	/**
	 * Callback endpoint успешно принял доставку.
	 */
	DELIVERED,
	/**
	 * Предыдущая попытка не удалась, следующая доступна после backoff.
	 */
	RETRY,
	/**
	 * Доставка невозможна или попытки исчерпаны.
	 */
	DEAD
}
