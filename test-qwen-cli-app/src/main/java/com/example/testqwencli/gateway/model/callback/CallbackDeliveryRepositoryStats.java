package com.example.testqwencli.gateway.model.callback;

import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Снимок статистики callback-доставок для dashboard/health.
 *
 * @param statusCounts количество доставок по каждому статусу
 * @param oldestBacklogCreatedAt время создания самой старой недоставленной записи
 */
public record CallbackDeliveryRepositoryStats(
		Map<CallbackDeliveryStatus, Long> statusCounts,
		Instant oldestBacklogCreatedAt
) {

	public CallbackDeliveryRepositoryStats {
		Objects.requireNonNull(statusCounts, "statusCounts must not be null");
		EnumMap<CallbackDeliveryStatus, Long> copy = new EnumMap<>(CallbackDeliveryStatus.class);
		for (CallbackDeliveryStatus status : CallbackDeliveryStatus.values()) {
			copy.put(status, statusCounts.getOrDefault(status, 0L));
		}
		statusCounts = Map.copyOf(copy);
	}

	/**
	 * Возвращает количество доставок в указанном статусе.
	 *
	 * @param status статус callback-доставки
	 * @return счетчик по статусу или {@code 0}, если статус отсутствует в map
	 */
	public long count(CallbackDeliveryStatus status) {
		return statusCounts.getOrDefault(status, 0L);
	}
}
