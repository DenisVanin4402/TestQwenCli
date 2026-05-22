package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.async.CallbackDeliveryStatus;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

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

	public long count(CallbackDeliveryStatus status) {
		return statusCounts.getOrDefault(status, 0L);
	}
}
