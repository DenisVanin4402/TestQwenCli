package com.example.testqwencli.dashboard.enums;

/**
 * Категория результата sync-вызова, по которой дашборд обновляет счетчики и latency-метрики.
 */
public enum DashboardCallStatus {
	/**
	 * Gateway успешно обработал sync-запрос и вернул ответ upstream.
	 */
	SUCCESS,

	/**
	 * Gateway отказал sync-запросу из-за отсутствия свободного слота.
	 */
	NO_SLOT,

	/**
	 * Sync-запрос превысил допустимое время ожидания.
	 */
	TIMEOUT,

	/**
	 * Запрос завершился иной ошибкой: исключение клиента, неожиданный HTTP-ответ или сбой gateway.
	 */
	ERROR
}
