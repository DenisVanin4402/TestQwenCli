package com.example.testqwencli.gateway.services;

/**
 * Сервис отправки callback-доставок во внешние сервисы-клиенты.
 *
 * <p>Dispatcher забирает записи из callback queue, вызывает {@code CallbackClient},
 * переводит доставку в финальный статус или планирует retry, а также восстанавливает
 * доставки, зависшие в {@code DELIVERING} после остановки процесса.</p>
 */
public interface CallbackDeliveryDispatcher {

	/**
	 * Выполняет одну попытку доставки.
	 *
	 * @return {@code true}, если доставка была найдена и обработана; {@code false}, если backlog пуст
	 */
	boolean dispatchOnce();

	/**
	 * Запускает ограниченное число worker-итераций доставки.
	 *
	 * @param maxIterations максимальное число worker-ов/итераций, которое можно начать
	 * @return фактическое число стартованных worker-ов
	 */
	int dispatchBatch(int maxIterations);

	/**
	 * Восстанавливает доставки, которые дольше таймаута находятся в {@code DELIVERING}.
	 *
	 * @return количество восстановленных доставок
	 */
	int recoverTimedOutDeliveries();

	/**
	 * Останавливает внутренний executor dispatcher-а.
	 */
	void shutdown();
}
