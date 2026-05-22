package com.example.testqwencli.dashboard;

import com.example.testqwencli.gateway.async.AsyncTaskRepository;
import com.example.testqwencli.gateway.async.AsyncTaskRepositoryStats;
import com.example.testqwencli.gateway.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.async.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.callback.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.callback.CallbackDeliveryRepositoryStats;
import com.example.testqwencli.gateway.slot.SlotKind;
import com.example.testqwencli.gateway.slot.SlotRepository;
import com.example.testqwencli.gateway.slot.config.ExternalGatewaySlotProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
class GatewayDashboardHealthProvider implements DashboardHealthProvider {

	private final SlotRepository slotRepository;
	private final AsyncTaskRepository asyncTaskRepository;
	private final CallbackDeliveryRepository callbackDeliveryRepository;
	private final ExternalGatewaySlotProperties slotProperties;
	private final Clock clock;
	private final String repositoryMode;
	private final boolean asyncDispatcherEnabled;
	private final boolean callbackDispatcherEnabled;

	GatewayDashboardHealthProvider(
			SlotRepository slotRepository,
			AsyncTaskRepository asyncTaskRepository,
			CallbackDeliveryRepository callbackDeliveryRepository,
			ExternalGatewaySlotProperties slotProperties,
			Clock clock,
			@Value("${external-gateway.repository.type:memory}") String repositoryMode,
			@Value("${external-gateway.async.dispatcher-enabled:false}") boolean asyncDispatcherEnabled,
			@Value("${external-gateway.callback.delivery-enabled:false}") boolean callbackDispatcherEnabled
	) {
		this.slotRepository = Objects.requireNonNull(slotRepository, "slotRepository must not be null");
		this.asyncTaskRepository = Objects.requireNonNull(asyncTaskRepository, "asyncTaskRepository must not be null");
		this.callbackDeliveryRepository = Objects.requireNonNull(callbackDeliveryRepository,
				"callbackDeliveryRepository must not be null");
		this.slotProperties = Objects.requireNonNull(slotProperties, "slotProperties must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
		this.repositoryMode = repositoryMode;
		this.asyncDispatcherEnabled = asyncDispatcherEnabled;
		this.callbackDispatcherEnabled = callbackDispatcherEnabled;
	}

	@Override
	public DashboardHealthSnapshot snapshot() {
		Instant now = clock.instant();
		long syncBusy = slotRepository.countBusySlots(SlotKind.SYNC);
		long asyncBusy = slotRepository.countBusySlots(SlotKind.ASYNC);
		long liveWaiters = slotRepository.countLiveSyncWaiters(now);
		long free = Math.max(0, slotProperties.total() - syncBusy - asyncBusy);
		long asyncAllowed = Math.max(0, slotProperties.total() - syncBusy - slotProperties.targetFreeSyncSlots());
		AsyncTaskRepositoryStats asyncStats = asyncTaskRepository.stats(now);
		CallbackDeliveryRepositoryStats callbackStats = callbackDeliveryRepository.stats(now);
		DashboardHealthSnapshot.CallbackDeliveryHealth callbackHealth = callbackHealth(callbackStats, now);

		return new DashboardHealthSnapshot(
				repositoryMode,
				new DashboardHealthSnapshot.SlotPoolHealth(
						slotProperties.total(),
						syncBusy,
						asyncBusy,
						free,
						slotProperties.targetFreeSyncSlots(),
						asyncAllowed,
						liveWaiters
				),
				asyncHealth(asyncStats, callbackHealth.backlog(), now),
				callbackHealth,
				new DashboardHealthSnapshot.DispatcherHealth(asyncDispatcherEnabled, callbackDispatcherEnabled)
		);
	}

	private DashboardHealthSnapshot.AsyncQueueHealth asyncHealth(
			AsyncTaskRepositoryStats stats,
			long callbackBacklog,
			Instant now
	) {
		long pending = stats.count(AsyncTaskStatus.PENDING);
		long inProgress = stats.count(AsyncTaskStatus.IN_PROGRESS);
		long done = stats.count(AsyncTaskStatus.DONE);
		long dead = stats.count(AsyncTaskStatus.DEAD);
		long cancelled = stats.count(AsyncTaskStatus.CANCELLED);
		return new DashboardHealthSnapshot.AsyncQueueHealth(
				statusCounts(stats.statusCounts()),
				pending,
				inProgress,
				done,
				dead,
				cancelled,
				stats.retryCount(),
				pending + inProgress + callbackBacklog,
				ageSeconds(stats.oldestActiveCreatedAt(), now)
		);
	}

	private DashboardHealthSnapshot.CallbackDeliveryHealth callbackHealth(
			CallbackDeliveryRepositoryStats stats,
			Instant now
	) {
		long pending = stats.count(CallbackDeliveryStatus.PENDING);
		long delivering = stats.count(CallbackDeliveryStatus.DELIVERING);
		long retry = stats.count(CallbackDeliveryStatus.RETRY);
		long dead = stats.count(CallbackDeliveryStatus.DEAD);
		return new DashboardHealthSnapshot.CallbackDeliveryHealth(
				statusCounts(stats.statusCounts()),
				pending + delivering + retry,
				retry,
				dead,
				ageSeconds(stats.oldestBacklogCreatedAt(), now)
		);
	}

	private static Map<String, Long> statusCounts(Map<? extends Enum<?>, Long> source) {
		Map<String, Long> result = new LinkedHashMap<>();
		source.forEach((status, count) -> result.put(status.name(), count));
		return result;
	}

	private static long ageSeconds(Instant startedAt, Instant now) {
		if (startedAt == null) {
			return 0;
		}
		return Math.max(0, Duration.between(startedAt, now).toSeconds());
	}
}
