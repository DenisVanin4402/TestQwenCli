package com.example.testqwencli.gateway.model.callback;

import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-модель строки {@code ext_callback_delivery}.
 *
 * <p>Описывает отдельную попытку доставить финальный результат async-задачи
 * в сервис-клиент. Запись проходит состояния {@code PENDING/RETRY -> DELIVERING}
 * и затем {@code DELIVERED} либо {@code DEAD}.</p>
 *
 * @param deliveryId id доставки
 * @param payload тело callback-события
 * @param callbackUrl allow-listed URL сервиса-клиента
 * @param status текущее состояние доставки
 * @param attempt номер текущей/последней попытки
 * @param maxAttempts максимальное число попыток перед {@link CallbackDeliveryStatus#DEAD}
 * @param createdAt момент создания доставки
 * @param availableAt момент, когда доставка доступна dispatcher-у
 * @param startedAt момент последнего перехода в {@link CallbackDeliveryStatus#DELIVERING}
 * @param completedAt момент финального завершения
 * @param lastError текст последней ошибки доставки
 */
public record CallbackDelivery(
		UUID deliveryId,
		CallbackPayload payload,
		URI callbackUrl,
		CallbackDeliveryStatus status,
		int attempt,
		int maxAttempts,
		Instant createdAt,
		Instant availableAt,
		Instant startedAt,
		Instant completedAt,
		String lastError
) {

	public CallbackDelivery {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(availableAt, "availableAt must not be null");
		if (status != CallbackDeliveryStatus.DEAD) {
			Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
		}
		if (callbackUrl != null && !callbackUrl.isAbsolute()) {
			throw new IllegalArgumentException("callbackUrl должен быть абсолютным URI");
		}
		if (attempt < 0) {
			throw new IllegalArgumentException("attempt не должен быть отрицательным");
		}
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
	}

	/**
	 * @return id async-задачи из callback payload
	 */
	public long taskId() {
		return payload.taskId();
	}

	/**
	 * @return имя сервиса-клиента из callback payload
	 */
	public String clientService() {
		return payload.clientService();
	}
}
