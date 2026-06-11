package com.example.testqwencli.gateway.repository;

import com.example.testqwencli.gateway.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.model.slot.enums.SlotKind;
import com.example.testqwencli.gateway.model.slot.enums.SyncAcquireWaitMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public interface SlotRepositoryContract {

	int TOTAL_SLOTS = 5;
	int TARGET_FREE_SYNC_SLOTS = 1;
	Duration LEASE_TTL = Duration.ofSeconds(30);
	Duration SYNC_WAITER_TTL = Duration.ofSeconds(5);

	SlotRepository repository();

	static ExternalGatewaySlotProperties contractSlotProperties() {
		return new ExternalGatewaySlotProperties(TOTAL_SLOTS, TARGET_FREE_SYNC_SLOTS, LEASE_TTL, SYNC_WAITER_TTL,
				Duration.ofMillis(10), SyncAcquireWaitMode.POLLING);
	}

	@Test
	default void acquireSyncSlotAllowsMaximumConfiguredSlots() {
		Instant now = currentInstant();

		List<SlotLease> leases = IntStream.range(0, TOTAL_SLOTS)
				.mapToObj(index -> repository().acquireSyncSlot("sync-owner-" + index, now).orElseThrow())
				.toList();

		assertThat(leases).extracting(SlotLease::slotId).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
		assertThat(leases).allSatisfy(lease -> {
			assertThat(lease.kind()).isEqualTo(SlotKind.SYNC);
			assertThat(lease.taskId()).isEmpty();
			assertThat(lease.expiresAt()).isAfter(now);
		});
		assertThat(repository().acquireSyncSlot("sync-owner-over-limit", now)).isEmpty();
		assertThat(repository().countBusySlots(SlotKind.SYNC)).isEqualTo(TOTAL_SLOTS);
	}

	@Test
	default void acquireAsyncSlotKeepsTargetFreeSyncSlotsWhenSyncBusyIsZero() {
		Instant now = currentInstant();

		List<SlotLease> leases = IntStream.range(0, TOTAL_SLOTS - TARGET_FREE_SYNC_SLOTS)
				.mapToObj(index -> repository().acquireAsyncSlot("async-owner", "task-" + index, now)
						.orElseThrow())
				.toList();

		assertThat(leases).hasSize(4);
		assertThat(leases).allSatisfy(lease -> {
			assertThat(lease.kind()).isEqualTo(SlotKind.ASYNC);
			assertThat(lease.taskId()).isPresent();
		});
		assertThat(repository().acquireAsyncSlot("async-owner", "task-over-limit", now)).isEmpty();
		assertThat(repository().countBusySlots(SlotKind.ASYNC)).isEqualTo(4);
	}

	@Test
	default void acquireAsyncSlotUsesDynamicSyncReserveWhenSyncBusyIsTwo() {
		Instant now = currentInstant();

		assertThat(repository().acquireSyncSlot("sync-owner-1", now)).isPresent();
		assertThat(repository().acquireSyncSlot("sync-owner-2", now)).isPresent();

		assertThat(repository().acquireAsyncSlot("async-owner", "task-1", now)).isPresent();
		assertThat(repository().acquireAsyncSlot("async-owner", "task-2", now)).isPresent();
		assertThat(repository().acquireAsyncSlot("async-owner", "task-3", now)).isEmpty();
		assertThat(repository().countBusySlots(SlotKind.ASYNC)).isEqualTo(2);
	}

	@Test
	default void acquireAsyncSlotIsBlockedByLiveSyncWaiter() {
		Instant now = currentInstant();
		UUID waiterId = repository().registerSyncWaiter("sync-owner", now);

		assertThat(repository().countLiveSyncWaiters(now.plusSeconds(1))).isEqualTo(1);
		assertThat(repository().acquireAsyncSlot("async-owner", "task-1", now.plusSeconds(1))).isEmpty();

		assertThat(repository().removeSyncWaiter(waiterId)).isTrue();
		assertThat(repository().acquireAsyncSlot("async-owner", "task-1", now.plusSeconds(1))).isPresent();
	}

	@Test
	default void expiredSyncWaiterDoesNotBlockAsyncSlot() {
		Instant now = currentInstant();

		repository().registerSyncWaiter("sync-owner", now);

		assertThat(repository().countLiveSyncWaiters(now.plus(SYNC_WAITER_TTL))).isZero();
		assertThat(repository().acquireAsyncSlot("async-owner", "task-1", now.plus(SYNC_WAITER_TTL))).isPresent();
	}

	@Test
	default void releaseAndHeartbeatRequireCurrentLeaseId() {
		Instant now = currentInstant();
		SlotLease firstLease = repository().acquireSyncSlot("sync-owner-1", now).orElseThrow();
		assertThat(repository().release(firstLease.slotId(), firstLease.leaseId())).isTrue();

		SlotLease reusedLease = repository().acquireSyncSlot("sync-owner-2", now.plusSeconds(1)).orElseThrow();
		assertThat(reusedLease.slotId()).isEqualTo(firstLease.slotId());
		assertThat(reusedLease.leaseId()).isNotEqualTo(firstLease.leaseId());

		assertThat(repository().release(firstLease.slotId(), firstLease.leaseId())).isFalse();
		assertThat(repository().heartbeat(firstLease.slotId(), firstLease.leaseId(), now.plusSeconds(2))).isEmpty();
		assertThat(repository().countBusySlots(SlotKind.SYNC)).isEqualTo(1);
		assertThat(repository().release(reusedLease.slotId(), reusedLease.leaseId())).isTrue();
		assertThat(repository().countBusySlots(SlotKind.SYNC)).isZero();
	}

	@Test
	default void heartbeatExtendsCurrentLease() {
		Instant now = currentInstant();
		SlotLease lease = repository().acquireSyncSlot("sync-owner", now).orElseThrow();

		Optional<SlotLease> heartbeat = repository().heartbeat(lease.slotId(), lease.leaseId(), now.plusSeconds(5));

		assertThat(heartbeat).isPresent();
		assertThat(heartbeat.orElseThrow()).satisfies(updated -> {
			assertThat(updated.slotId()).isEqualTo(lease.slotId());
			assertThat(updated.leaseId()).isEqualTo(lease.leaseId());
			assertThat(updated.owner()).isEqualTo(lease.owner());
			assertThat(updated.kind()).isEqualTo(lease.kind());
			assertThat(updated.expiresAt()).isAfter(lease.expiresAt());
		});
		assertThat(repository().heartbeat(lease.slotId(), UUID.randomUUID(), now.plusSeconds(6))).isEmpty();
	}

	@Test
	default void reapExpiredLeasesFreesSlots() {
		Instant now = currentInstant();
		IntStream.range(0, TOTAL_SLOTS)
				.mapToObj(index -> repository().acquireSyncSlot("sync-owner-" + index, now))
				.forEach(slot -> assertThat(slot).isPresent());

		assertThat(repository().acquireSyncSlot("sync-owner-over-limit", now.plusSeconds(29))).isEmpty();
		assertThat(repository().reapExpiredLeases(now.plusSeconds(29))).isZero();
		assertThat(repository().acquireSyncSlot("sync-owner-over-limit", now.plusSeconds(29))).isEmpty();

		assertThat(repository().reapExpiredLeases(now.plus(LEASE_TTL))).isEqualTo(TOTAL_SLOTS);
		assertThat(repository().acquireSyncSlot("sync-owner-after-reap", now.plus(LEASE_TTL))).isPresent();
	}

	@Test
	default void concurrentSyncAcquireDoesNotIssueSameSlotTwice() throws Exception {
		int contenders = TOTAL_SLOTS * 3;
		ExecutorService executor = Executors.newFixedThreadPool(contenders);
		CountDownLatch ready = new CountDownLatch(contenders);
		CountDownLatch start = new CountDownLatch(1);

		try {
			List<Future<Optional<SlotLease>>> futures = IntStream.range(0, contenders)
					.mapToObj(index -> executor.submit(() -> {
						ready.countDown();
						if (!start.await(5, TimeUnit.SECONDS)) {
							throw new AssertionError("Конкурентный старт захвата слотов не был открыт за 5 секунд");
						}
						return repository().acquireSyncSlot("sync-concurrent-" + index, currentInstant());
					}))
					.toList();

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<SlotLease> leases = collectPresentLeases(futures);
			assertThat(leases).hasSize(TOTAL_SLOTS);
			assertThat(leases).extracting(SlotLease::slotId).doesNotHaveDuplicates();
			assertThat(leases).extracting(SlotLease::leaseId).doesNotHaveDuplicates();
			assertThat(repository().countBusySlots(SlotKind.SYNC)).isEqualTo(TOTAL_SLOTS);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static Instant currentInstant() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}

	private static List<SlotLease> collectPresentLeases(List<Future<Optional<SlotLease>>> futures)
			throws Exception {
		return futures.stream()
				.map(future -> futureLease(future).orElse(null))
				.filter(lease -> lease != null)
				.toList();
	}

	private static Optional<SlotLease> futureLease(Future<Optional<SlotLease>> future) {
		try {
			return future.get(10, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError("Конкурентный захват слота не завершился за 10 секунд", exception);
		}
	}
}
