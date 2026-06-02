package com.example.testqwencli.gateway.model.async;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-модель строки {@code ext_request_queue}.
 *
 * <p>Описывает как реальные async-задачи, так и журнальные sync trace-строки.
 * Для async-режимов жизненный цикл идет через {@code PENDING -> IN_PROGRESS}
 * и затем в финальный статус. Для {@link AsyncDeliveryMode#SYNC} запись создается
 * уже финальной и не участвует в dispatcher claim.</p>
 *
 * @param taskId внутренний id задачи/trace в {@code ext_request_queue}
 * @param externalId внешний id операции клиента
 * @param clientService имя сервиса-клиента, участвующее в idempotency-key
 * @param priority приоритет обработки async-задачи
 * @param deliveryMode способ получения результата или trace-режим
 * @param status состояние обработки upstream
 * @param callbackDeliveryStatus состояние связанной callback-доставки
 * @param result JSON-результат upstream для успешных задач
 * @param error структурированная ошибка для финальных неуспешных задач
 * @param attempts число попыток upstream-обработки
 * @param maxAttempts максимальное число попыток до {@link AsyncTaskStatus#DEAD}
 * @param createdAt момент создания строки
 * @param availableAt момент, начиная с которого задача доступна для claim
 * @param startedAt момент последнего старта обработки, если он зафиксирован
 * @param finishedAt момент финального завершения
 * @param lastError текст последней transient-ошибки
 * @param retryable можно ли вручную вернуть финальную задачу в очередь
 */
public record AsyncTask(
		long taskId,
		UUID externalId,
		String clientService,
		AsyncPriority priority,
		AsyncDeliveryMode deliveryMode,
		AsyncTaskStatus status,
		CallbackDeliveryStatus callbackDeliveryStatus,
		Map<String, Object> result,
		TaskError error,
		int attempts,
		int maxAttempts,
		Instant createdAt,
		Instant availableAt,
		Instant startedAt,
		Instant finishedAt,
		String lastError,
		boolean retryable
) {

	public AsyncTask {
		if (taskId < 1) {
			throw new IllegalArgumentException("taskId должен быть положительным");
		}
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(priority, "priority must not be null");
		Objects.requireNonNull(deliveryMode, "deliveryMode must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(callbackDeliveryStatus, "callbackDeliveryStatus must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		if (clientService.isBlank()) {
			throw new IllegalArgumentException("clientService не должен быть пустым");
		}
		if (attempts < 0) {
			throw new IllegalArgumentException("attempts не должен быть отрицательным");
		}
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
		if (result != null) {
			result = AsyncPayloads.copyMap(result);
		}
	}
}
