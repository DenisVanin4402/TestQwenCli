package com.example.testqwencli.gateway.model.slot;

/**
 * Тип занятости слота внешнего gateway.
 */
public enum SlotKind {
	/**
	 * Слот занят синхронным запросом, который ждет ответ в рамках HTTP-вызова клиента.
	 */
	SYNC,
	/**
	 * Слот занят async dispatcher-ом для фонового upstream-вызова.
	 */
	ASYNC
}
