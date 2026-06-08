package com.example.testqwencli.gateway.model.async.enums;

/**
 * Способ, которым клиент получает результат async-задачи.
 *
 * <p>Значение хранится в {@code ext_request_queue.delivery_mode} и влияет на
 * создание callback-доставки, idempotency-key и выбор задач dispatcher-ом.</p>
 */
public enum AsyncDeliveryMode {
	/**
	 * После финального статуса gateway должен запланировать HTTP callback.
	 */
	CALLBACK,
	/**
	 * Клиент сам забирает результат через polling endpoint, callback не нужен.
	 */
	POLLING,
	/**
	 * Внутренний режим для журнальных строк синхронных запросов.
	 *
	 * <p>Такие строки не участвуют в async claim и не создаются внешним async API.</p>
	 */
	SYNC
}
