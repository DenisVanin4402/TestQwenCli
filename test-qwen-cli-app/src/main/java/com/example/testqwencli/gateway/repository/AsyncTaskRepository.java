package com.example.testqwencli.gateway.repository;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResult;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.model.async.AsyncTaskRepositoryStats;
import com.example.testqwencli.gateway.model.async.AsyncTaskUpdateResult;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.SyncRequestTrace;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Порт хранения async-задач и sync trace-записей external gateway.
 *
 * <p>Интерфейс скрывает реализацию очереди: in-memory репозиторий нужен для unit-тестов
 * и локального режима, PostgreSQL репозиторий обеспечивает persistent queue, row-lock claim
 * и идемпотентность по {@code clientService + externalId} для async-режимов.</p>
 */
public interface AsyncTaskRepository {

	/**
	 * Создает async-задачу или возвращает ранее созданную задачу по idempotency-key.
	 *
	 * @param request внешний async-запрос
	 * @param maxAttempts максимальное число попыток upstream
	 * @param now текущее время gateway
	 * @return результат submit, включая idempotency conflict при несовпадении payload/режима/приоритета
	 */
	AsyncSubmitResult submit(ExternalAsyncRequest request, int maxAttempts, Instant now);

	/**
	 * Ищет async-задачу по внутреннему id.
	 *
	 * @param taskId id задачи
	 * @param clientService опциональный фильтр по сервису-клиенту
	 * @return задача, если она существует и проходит фильтр
	 */
	Optional<AsyncTask> findByTaskId(long taskId, Optional<String> clientService);

	/**
	 * Ищет async-задачу по внешнему id.
	 *
	 * @param externalId внешний id операции
	 * @param clientService опциональный фильтр по сервису-клиенту
	 * @return первая подходящая async-задача
	 */
	Optional<AsyncTask> findByExternalId(UUID externalId, Optional<String> clientService);

	/**
	 * Отменяет задачу, если она еще не начата.
	 *
	 * @param taskId id задачи
	 * @param clientService опциональный фильтр по сервису-клиенту
	 * @param now текущее время gateway
	 * @return результат команды cancel
	 */
	AsyncTaskUpdateResult cancel(long taskId, Optional<String> clientService, Instant now);

	/**
	 * Возвращает финальную retryable-задачу в очередь.
	 *
	 * @param taskId id задачи
	 * @param clientService опциональный фильтр по сервису-клиенту
	 * @param now текущее время gateway
	 * @return результат команды manual retry
	 */
	AsyncTaskUpdateResult retry(long taskId, Optional<String> clientService, Instant now);

	/**
	 * Записывает завершенный sync-запрос в общий журнал request queue.
	 *
	 * @param trace финальный trace sync-вызова
	 * @return созданная trace-строка как {@link AsyncTask}
	 */
	AsyncTask recordSyncTrace(SyncRequestTrace trace);

	/**
	 * Возвращает все request trace-строки по внешнему id.
	 *
	 * @param externalId внешний id операции
	 * @param clientService опциональный фильтр по сервису-клиенту
	 * @return список async-задач и sync trace-записей
	 */
	List<AsyncTask> findRequestTracesByExternalId(UUID externalId, Optional<String> clientService);

	/**
	 * Забирает следующую доступную async-задачу в обработку.
	 *
	 * <p>PostgreSQL реализация использует {@code FOR UPDATE SKIP LOCKED};
	 * вызов должен выполняться внутри {@link #executeInProcessingTransaction(Supplier)}
	 * для удержания row-lock до финального обновления задачи.</p>
	 *
	 * @param now текущее время gateway
	 * @return claim задачи с payload, если доступная задача найдена
	 */
	Optional<AsyncTaskClaim> claimNextPending(Instant now);

	/**
	 * Выполняет действие в транзакции обработки async-задачи.
	 *
	 * @param action действие dispatcher-а
	 * @return результат действия
	 * @param <T> тип результата
	 */
	<T> T executeInProcessingTransaction(Supplier<T> action);

	/**
	 * Завершает задачу успешным результатом upstream.
	 *
	 * @param taskId id задачи в состоянии {@code IN_PROGRESS}
	 * @param result результат upstream
	 * @param now текущее время gateway
	 * @return обновленная задача, если состояние позволяло завершение
	 */
	Optional<AsyncTask> complete(long taskId, Map<String, String> result, Instant now);

	/**
	 * Фиксирует transient-ошибку upstream и решает, будет retry или {@code DEAD}.
	 *
	 * @param taskId id задачи в состоянии {@code IN_PROGRESS}
	 * @param message текст ошибки
	 * @param backoff задержка перед следующей попыткой
	 * @param now текущее время gateway
	 * @return обновленная задача, если состояние позволяло обработать ошибку
	 */
	Optional<AsyncTask> failTransient(long taskId, String message, Duration backoff, Instant now);

	/**
	 * Возвращает только что claimed задачу обратно в {@code PENDING}.
	 *
	 * <p>Используется, когда dispatcher забрал строку, но не смог получить async-слот.
	 * Метод должен компенсировать увеличение attempts, сделанное при claim.</p>
	 *
	 * @param taskId id задачи
	 * @param now текущее время gateway
	 * @return обновленная задача, если claim еще активен
	 */
	Optional<AsyncTask> returnClaimToPending(long taskId, Instant now);

	/**
	 * Синхронизирует агрегированный статус callback-доставки в строке request queue.
	 *
	 * @param taskId id async-задачи
	 * @param status новый статус callback-доставки
	 * @param now текущее время gateway
	 * @return обновленная задача, если она найдена
	 */
	Optional<AsyncTask> updateCallbackDeliveryStatus(long taskId, CallbackDeliveryStatus status, Instant now);

	/**
	 * Собирает статистику очереди для dashboard и health snapshot.
	 *
	 * @param now текущее время gateway
	 * @return счетчики активных async-задач
	 */
	AsyncTaskRepositoryStats stats(Instant now);
}
