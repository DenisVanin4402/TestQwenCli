package com.example.testqwencli.gateway.repository.memory;

import com.example.testqwencli.gateway.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.model.slot.SlotKind;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.model.slot.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.repository.memory.MemorySlotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySlotRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");

	private final MemorySlotRepository repository = new MemorySlotRepository(
			new ExternalGatewaySlotProperties(5, 1, Duration.ofSeconds(30), Duration.ofSeconds(5),
					Duration.ofMillis(10), SyncAcquireWaitMode.POLLING));

	@Test
	void acquireSyncSlotAllowsMaximumFiveSlots() {
		IntStream.range(0, 5)
				.mapToObj(index -> repository.acquireSyncSlot("sync-owner-" + index, NOW))
				.forEach(slot -> assertThat(slot).isPresent());

		assertThat(repository.acquireSyncSlot("sync-owner-5", NOW)).isEmpty();
		assertThat(repository.countBusySlots(SlotKind.SYNC)).isEqualTo(5);
	}

	@Test
	void acquireAsyncSlotKeepsOneSlotFreeWhenSyncBusyIsZero() {
		IntStream.range(0, 4)
				.mapToObj(index -> repository.acquireAsyncSlot("async-owner", "task-" + index, NOW))
				.forEach(slot -> assertThat(slot).isPresent());

		assertThat(repository.acquireAsyncSlot("async-owner", "task-4", NOW)).isEmpty();
		assertThat(repository.countBusySlots(SlotKind.ASYNC)).isEqualTo(4);
	}

	@Test
	void acquireAsyncSlotUsesDynamicSyncReserveWhenSyncBusyIsTwo() {
		assertThat(repository.acquireSyncSlot("sync-owner-1", NOW)).isPresent();
		assertThat(repository.acquireSyncSlot("sync-owner-2", NOW)).isPresent();

		assertThat(repository.acquireAsyncSlot("async-owner", "task-1", NOW)).isPresent();
		assertThat(repository.acquireAsyncSlot("async-owner", "task-2", NOW)).isPresent();
		assertThat(repository.acquireAsyncSlot("async-owner", "task-3", NOW)).isEmpty();
		assertThat(repository.countBusySlots(SlotKind.ASYNC)).isEqualTo(2);
	}

	@Test
	void acquireAsyncSlotIsBlockedByLiveSyncWaiter() {
		UUID waiterId = repository.registerSyncWaiter("sync-owner", NOW);

		assertThat(repository.countLiveSyncWaiters(NOW.plusSeconds(1))).isEqualTo(1);
		assertThat(repository.acquireAsyncSlot("async-owner", "task-1", NOW.plusSeconds(1))).isEmpty();

		assertThat(repository.removeSyncWaiter(waiterId)).isTrue();
		assertThat(repository.acquireAsyncSlot("async-owner", "task-1", NOW.plusSeconds(1))).isPresent();
	}

	@Test
	void oldLeaseIdCannotReleaseOrHeartbeatReusedSlot() {
		SlotLease firstLease = repository.acquireSyncSlot("sync-owner-1", NOW).orElseThrow();
		assertThat(repository.release(firstLease.slotId(), firstLease.leaseId())).isTrue();

		SlotLease reusedLease = repository.acquireSyncSlot("sync-owner-2", NOW.plusSeconds(1)).orElseThrow();
		assertThat(reusedLease.slotId()).isEqualTo(firstLease.slotId());
		assertThat(reusedLease.leaseId()).isNotEqualTo(firstLease.leaseId());

		assertThat(repository.release(firstLease.slotId(), firstLease.leaseId())).isFalse();
		assertThat(repository.heartbeat(firstLease.slotId(), firstLease.leaseId(), NOW.plusSeconds(2))).isEmpty();
		assertThat(repository.countBusySlots(SlotKind.SYNC)).isEqualTo(1);
		assertThat(repository.release(reusedLease.slotId(), reusedLease.leaseId())).isTrue();
	}

	@Test
	void reapExpiredLeasesFreesSlots() {
		IntStream.range(0, 5)
				.mapToObj(index -> repository.acquireSyncSlot("sync-owner-" + index, NOW))
				.forEach(slot -> assertThat(slot).isPresent());

		assertThat(repository.acquireSyncSlot("sync-owner-5", NOW.plusSeconds(29))).isEmpty();
		assertThat(repository.reapExpiredLeases(NOW.plusSeconds(29))).isZero();
		assertThat(repository.acquireSyncSlot("sync-owner-5", NOW.plusSeconds(29))).isEmpty();

		assertThat(repository.reapExpiredLeases(NOW.plusSeconds(30))).isEqualTo(5);
		Optional<SlotLease> newLease = repository.acquireSyncSlot("sync-owner-5", NOW.plusSeconds(30));
		assertThat(newLease).isPresent();
	}
}
