package com.example.testqwencli.gateway.model.callback;

import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.TaskError;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Тело callback-события, отправляемого сервису-клиенту после финала async-задачи.
 *
 * <p>Для успешной задачи содержит {@link #result()}, для неуспешной - {@link #error()}.
 * {@code eventId} используется как idempotency-key самой доставки.</p>
 *
 * @param eventId уникальный id callback-события
 * @param taskId id async-задачи
 * @param externalId внешний id исходного запроса
 * @param clientService имя сервиса-клиента
 * @param status финальный статус async-задачи
 * @param result результат для {@link AsyncTaskStatus#DONE}
 * @param error ошибка для неуспешных финальных статусов
 * @param finishedAt момент финального завершения задачи
 */
public record CallbackPayload(
		UUID eventId,
		long taskId,
		UUID externalId,
		String clientService,
		AsyncTaskStatus status,
		Map<String, String> result,
		TaskError error,
		Instant finishedAt
) {

	public CallbackPayload {
		Objects.requireNonNull(eventId, "eventId must not be null");
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(finishedAt, "finishedAt must not be null");
		if (taskId < 1) {
			throw new IllegalArgumentException("taskId должен быть положительным");
		}
		if (clientService.isBlank()) {
			throw new IllegalArgumentException("clientService не должен быть пустым");
		}
		if (status == AsyncTaskStatus.DONE) {
			Objects.requireNonNull(result, "result must not be null for DONE callback");
			if (error != null) {
				throw new IllegalArgumentException("error должен быть null для DONE callback");
			}
			result = copyResult(result);
		}
		else {
			if (result != null) {
				throw new IllegalArgumentException("result должен быть null для неуспешного callback");
			}
			Objects.requireNonNull(error, "error must not be null for failed callback");
		}
	}

	/**
	 * Создает callback payload из финальной async-задачи.
	 *
	 * @param eventId id нового callback-события
	 * @param task финальная async-задача
	 * @return payload для доставки
	 * @throws IllegalArgumentException если задача еще не финальная
	 */
	public static CallbackPayload fromTask(UUID eventId, AsyncTask task) {
		Objects.requireNonNull(task, "task must not be null");
		if (!isFinal(task.status())) {
			throw new IllegalArgumentException("Callback можно создать только для финальной async-задачи");
		}
		if (task.finishedAt() == null) {
			throw new IllegalArgumentException("Финальная async-задача должна иметь finishedAt");
		}
		return new CallbackPayload(eventId, task.taskId(), task.externalId(), task.clientService(), task.status(),
				resultFor(task), errorFor(task), task.finishedAt());
	}

	/**
	 * Возвращает тот же payload с новым id события.
	 *
	 * <p>Используется при создании новой доставки без изменения бизнес-содержимого callback.</p>
	 *
	 * @param eventId новый id callback-события
	 * @return копия payload с замененным {@code eventId}
	 */
	public CallbackPayload withEventId(UUID eventId) {
		return new CallbackPayload(eventId, taskId, externalId, clientService, status, result, error, finishedAt);
	}

	private static Map<String, String> resultFor(AsyncTask task) {
		if (task.status() != AsyncTaskStatus.DONE) {
			return null;
		}
		if (task.result() == null) {
			throw new IllegalArgumentException("DONE async-задача должна иметь result для callback");
		}
		LinkedHashMap<String, String> resultMap = new LinkedHashMap<>();
		task.result().forEach((key, value) -> resultMap.put(key, Objects.toString(value, null)));
		return resultMap;
	}

	private static TaskError errorFor(AsyncTask task) {
		if (task.status() == AsyncTaskStatus.DONE) {
			return null;
		}
		if (task.error() != null) {
			return task.error();
		}
		return new TaskError("TASK_FINISHED_WITHOUT_ERROR_DETAILS",
				"Финальная async-задача завершилась без структурированной ошибки", false);
	}

	private static Map<String, String> copyResult(Map<String, String> source) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}

	private static boolean isFinal(AsyncTaskStatus status) {
		return status == AsyncTaskStatus.DONE
				|| status == AsyncTaskStatus.FAILED
				|| status == AsyncTaskStatus.DEAD
				|| status == AsyncTaskStatus.CANCELLED;
	}
}
