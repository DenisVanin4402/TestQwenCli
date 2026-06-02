package com.example.testqwencli.gateway.model.async;

import java.util.Map;
import java.util.Objects;

/**
 * Результат успешного claim async-задачи dispatcher-ом.
 *
 * <p>Содержит текущую read-модель задачи и immutable-копию исходного payload,
 * который нужен для вызова upstream. В PostgreSQL claim может существовать только
 * внутри транзакции обработки и откатиться при падении процесса.</p>
 *
 * @param task задача, переведенная в обработку
 * @param payload исходный JSON payload для upstream
 */
public record AsyncTaskClaim(
		AsyncTask task,
		Map<String, Object> payload
) {

	public AsyncTaskClaim {
		Objects.requireNonNull(task, "task must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		payload = AsyncPayloads.copyMap(payload);
	}
}
