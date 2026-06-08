package com.example.testqwencli.gateway.model.async;

import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP-ответ на постановку async-задачи в очередь.
 *
 * <p>Модель возвращает публичный идентификатор задачи, ссылку на polling endpoint
 * и признак idempotent replay, когда запрос с тем же {@code clientService/externalId}
 * уже был принят ранее.</p>
 *
 * @param taskId внутренний id задачи в {@code ext_request_queue}
 * @param externalId внешний id запроса, переданный клиентом
 * @param status текущий статус задачи после submit
 * @param deliveryMode способ получения результата
 * @param statusUrl относительный URL для polling по {@code taskId}
 * @param alreadyExisted {@code true}, если submit вернул уже существующую задачу
 */
public record AsyncSubmitResponse(
		long taskId,
		UUID externalId,
		AsyncTaskStatus status,
		AsyncDeliveryMode deliveryMode,
		String statusUrl,
		boolean alreadyExisted
) {

	public AsyncSubmitResponse {
		if (taskId < 1) {
			throw new IllegalArgumentException("taskId должен быть положительным");
		}
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(deliveryMode, "deliveryMode must not be null");
		Objects.requireNonNull(statusUrl, "statusUrl must not be null");
		if (statusUrl.isBlank()) {
			throw new IllegalArgumentException("statusUrl не должен быть пустым");
		}
	}

	/**
	 * Строит API-ответ из доменной задачи.
	 *
	 * @param task сохраненная async-задача
	 * @param alreadyExisted признак idempotent replay
	 * @return response для клиента async API
	 */
	public static AsyncSubmitResponse from(AsyncTask task, boolean alreadyExisted) {
		return new AsyncSubmitResponse(task.taskId(), task.externalId(), task.status(), task.deliveryMode(),
				"/v1/external/async/" + task.taskId(), alreadyExisted);
	}
}
