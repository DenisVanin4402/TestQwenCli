package com.example.testqwencli.dashboard;

/**
 * Категория результата async-submit операции, по которой дашборд обновляет счетчики нагрузки.
 */
public enum DashboardSubmitStatus {
	/**
	 * Gateway принял запрос в async-очередь или вернул уже существующую задачу по идемпотентности.
	 */
	ACCEPTED,

	/**
	 * Gateway корректно отклонил submit, например из-за конфликта идемпотентности или валидации.
	 */
	REJECTED,

	/**
	 * Submit завершился технической ошибкой клиента, gateway или сети.
	 */
	ERROR
}
