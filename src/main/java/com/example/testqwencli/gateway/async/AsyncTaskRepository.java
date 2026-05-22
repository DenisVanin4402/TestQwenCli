package com.example.testqwencli.gateway.async;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт очереди async-задач external gateway.
 */
public interface AsyncTaskRepository {

	/**
	 * Ставит async-задачу в очередь или возвращает существующую задачу по ключу идемпотентности.
	 *
	 * @param request входящий запрос на постановку async-задачи
	 * @param maxAttempts максимальное количество попыток выполнения
	 * @param now текущее время координатора
	 * @return результат постановки задачи
	 */
	AsyncSubmitResult submit(ExternalAsyncRequest request, int maxAttempts, Instant now);

	/**
	 * Ищет async-задачу по внутреннему идентификатору.
	 *
	 * @param taskId идентификатор задачи
	 * @param clientService необязательное ограничение по сервису-клиенту
	 * @return найденная задача или пустой результат
	 */
	Optional<AsyncTask> findByTaskId(long taskId, Optional<String> clientService);

	/**
	 * Ищет async-задачу по клиентскому идентификатору.
	 *
	 * @param externalId идентификатор задачи на стороне сервиса-клиента
	 * @param clientService необязательное ограничение по сервису-клиенту
	 * @return найденная задача или пустой результат
	 */
	Optional<AsyncTask> findByExternalId(UUID externalId, Optional<String> clientService);

	/**
	 * Отменяет задачу, если ее состояние допускает отмену.
	 *
	 * @param taskId идентификатор задачи
	 * @param clientService необязательное ограничение по сервису-клиенту
	 * @param now текущее время координатора
	 * @return результат обновления задачи
	 */
	AsyncTaskUpdateResult cancel(long taskId, Optional<String> clientService, Instant now);

	/**
	 * Возвращает задачу в очередь для ручного повтора, если ее состояние это допускает.
	 *
	 * @param taskId идентификатор задачи
	 * @param clientService необязательное ограничение по сервису-клиенту
	 * @param now текущее время координатора
	 * @return результат обновления задачи
	 */
	AsyncTaskUpdateResult retry(long taskId, Optional<String> clientService, Instant now);

	/**
	 * Захватывает следующую доступную задачу для выполнения.
	 *
	 * @param now текущее время координатора
	 * @return claim задачи или пустой результат, если доступных задач нет
	 */
	Optional<AsyncTaskClaim> claimNextPending(Instant now);

	/**
	 * Завершает задачу успешным результатом.
	 *
	 * @param taskId идентификатор задачи
	 * @param result нормализованный результат внешнего сервиса
	 * @param now текущее время координатора
	 * @return обновленная задача или пустой результат, если задача не найдена
	 */
	Optional<AsyncTask> complete(long taskId, Map<String, String> result, Instant now);

	/**
	 * Возвращает задачу после временной ошибки в очередь либо переводит ее в {@code DEAD}.
	 *
	 * @param taskId идентификатор задачи
	 * @param message диагностическое описание ошибки
	 * @param backoff задержка перед следующей попыткой
	 * @param now текущее время координатора
	 * @return обновленная задача или пустой результат, если задача не найдена
	 */
	Optional<AsyncTask> failTransient(long taskId, String message, Duration backoff, Instant now);

	/**
	 * Возвращает захваченную задачу обратно в очередь без увеличения счетчика попыток.
	 *
	 * @param taskId идентификатор задачи
	 * @param now текущее время координатора
	 * @return обновленная задача или пустой результат, если задача не найдена
	 */
	Optional<AsyncTask> returnClaimToPending(long taskId, Instant now);

	/**
	 * Обновляет статус доставки обратного вызова для async-задачи.
	 *
	 * @param taskId идентификатор задачи
	 * @param status новый статус доставки
	 * @param now текущее время координатора
	 * @return обновленная задача или пустой результат, если задача не найдена
	 */
	Optional<AsyncTask> updateCallbackDeliveryStatus(long taskId, CallbackDeliveryStatus status, Instant now);
}
