package com.example.testqwencli.gateway.services;

/**
 * Сервис фоновой обработки async-очереди.
 *
 * <p>Dispatcher claim-ит задачу, захватывает async-слот, вызывает upstream и фиксирует
 * успешный результат или transient retry/dead. В PostgreSQL row-lock задачи удерживается
 * до финального обновления в транзакции обработки.</p>
 */
public interface ExternalAsyncDispatcher {

	/**
	 * Обрабатывает одну доступную async-задачу.
	 *
	 * @return {@code true}, если задача была найдена и обработана; {@code false}, если работы нет
	 */
	boolean dispatchOnce();

	/**
	 * Запускает ограниченное число dispatcher worker-ов.
	 *
	 * @param maxIterations максимальное число worker-ов/итераций
	 * @return фактическое число стартованных worker-ов
	 */
	int dispatchBatch(int maxIterations);

	/**
	 * Останавливает внутренний executor dispatcher-а.
	 */
	void shutdown();
}
