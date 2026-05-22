package com.example.testqwencli.dashboard;

import java.util.Map;

public record DashboardHealthSnapshot(
		String repositoryMode,
		SlotPoolHealth slots,
		AsyncQueueHealth asyncQueue,
		CallbackDeliveryHealth callbacks,
		DispatcherHealth dispatchers
) {

	public record SlotPoolHealth(
			int total,
			long syncBusy,
			long asyncBusy,
			long free,
			int syncReserve,
			long asyncAllowed,
			long liveSyncWaiters
	) {
	}

	public record AsyncQueueHealth(
			Map<String, Long> byStatus,
			long pending,
			long inProgress,
			long done,
			long dead,
			long cancelled,
			long retry,
			long withoutAnswer,
			long oldestActiveAgeSeconds
	) {
	}

	public record CallbackDeliveryHealth(
			Map<String, Long> byStatus,
			long backlog,
			long retry,
			long dead,
			long oldestBacklogAgeSeconds
	) {
	}

	public record DispatcherHealth(
			boolean asyncDispatcherEnabled,
			boolean callbackDispatcherEnabled
	) {
	}
}
