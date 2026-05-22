package com.example.testqwencli.gateway.async;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record AsyncTaskRepositoryStats(
		Map<AsyncTaskStatus, Long> statusCounts,
		long retryCount,
		Instant oldestActiveCreatedAt
) {

	public AsyncTaskRepositoryStats {
		Objects.requireNonNull(statusCounts, "statusCounts must not be null");
		EnumMap<AsyncTaskStatus, Long> copy = new EnumMap<>(AsyncTaskStatus.class);
		for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
			copy.put(status, statusCounts.getOrDefault(status, 0L));
		}
		statusCounts = Map.copyOf(copy);
	}

	public long count(AsyncTaskStatus status) {
		return statusCounts.getOrDefault(status, 0L);
	}
}
