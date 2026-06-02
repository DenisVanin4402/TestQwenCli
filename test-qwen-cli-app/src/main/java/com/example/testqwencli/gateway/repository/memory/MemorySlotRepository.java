package com.example.testqwencli.gateway.repository.memory;

import com.example.testqwencli.gateway.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.model.slot.SlotKind;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.repository.SlotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MemorySlotRepository implements SlotRepository {

	private static final Logger log = LoggerFactory.getLogger(MemorySlotRepository.class);

	private final int totalSlots;
	private final int targetFreeSyncSlots;
	private final Duration leaseTtl;
	private final Duration syncWaiterTtl;
	private final SlotLease[] slots;
	private final Map<UUID, SyncWaiter> syncWaiters = new HashMap<>();

	public MemorySlotRepository(ExternalGatewaySlotProperties properties) {
		Objects.requireNonNull(properties, "properties must not be null");
		this.totalSlots = properties.total();
		this.targetFreeSyncSlots = properties.targetFreeSyncSlots();
		this.leaseTtl = properties.leaseTtl();
		this.syncWaiterTtl = properties.syncWaiterTtl();
		this.slots = new SlotLease[totalSlots];
	}

	@Override
	public synchronized Optional<SlotLease> acquireSyncSlot(String owner, Instant now) {
		validateOwner(owner);
		Objects.requireNonNull(now, "now must not be null");

		return firstFreeSlotId().map(slotId -> {
			SlotLease lease = SlotLease.sync(slotId, owner, now.plus(leaseTtl));
			slots[toIndex(slotId)] = lease;
			log.debug("Захвачен sync-слот: slotId={}, leaseId={}, owner={}", slotId, lease.leaseId(), owner);
			return lease;
		});
	}

	@Override
	public synchronized Optional<SlotLease> acquireAsyncSlot(String owner, String taskId, Instant now) {
		validateOwner(owner);
		Objects.requireNonNull(now, "now must not be null");
		removeExpiredSyncWaiters(now);
		if (countLiveSyncWaitersInternal(now) > 0 || !hasAsyncCapacity()) {
			return Optional.empty();
		}

		return firstFreeSlotId().map(slotId -> {
			SlotLease lease = SlotLease.async(slotId, owner, now.plus(leaseTtl), normalizeTaskId(taskId));
			slots[toIndex(slotId)] = lease;
			log.debug("Захвачен async-слот: slotId={}, leaseId={}, owner={}, taskId={}",
					slotId, lease.leaseId(), owner, lease.taskId().orElse(null));
			return lease;
		});
	}

	@Override
	public synchronized boolean release(int slotId, UUID leaseId) {
		Objects.requireNonNull(leaseId, "leaseId must not be null");
		if (!isValidSlotId(slotId)) {
			return false;
		}
		SlotLease current = slots[toIndex(slotId)];
		if (current == null || !current.leaseId().equals(leaseId)) {
			return false;
		}
		slots[toIndex(slotId)] = null;
		log.debug("Освобожден слот: slotId={}, leaseId={}", slotId, leaseId);
		return true;
	}

	@Override
	public synchronized Optional<SlotLease> heartbeat(int slotId, UUID leaseId, Instant now) {
		Objects.requireNonNull(leaseId, "leaseId must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (!isValidSlotId(slotId)) {
			return Optional.empty();
		}
		SlotLease current = slots[toIndex(slotId)];
		if (current == null || !current.leaseId().equals(leaseId) || !current.expiresAt().isAfter(now)) {
			return Optional.empty();
		}
		SlotLease extended = new SlotLease(slotId, leaseId, current.owner(), current.kind(),
				now.plus(leaseTtl), current.taskId());
		slots[toIndex(slotId)] = extended;
		log.debug("Продлен lease слота: slotId={}, leaseId={}", slotId, leaseId);
		return Optional.of(extended);
	}

	@Override
	public synchronized long countBusySlots(SlotKind kind) {
		Objects.requireNonNull(kind, "kind must not be null");
		return countBusySlotsInternal(kind);
	}

	@Override
	public synchronized int reapExpiredLeases(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		int reaped = 0;
		for (int index = 0; index < slots.length; index++) {
			SlotLease lease = slots[index];
			if (lease != null && !lease.expiresAt().isAfter(now)) {
				slots[index] = null;
				reaped++;
			}
		}
		if (reaped > 0) {
			log.debug("Освобождены истекшие lease слотов: count={}", reaped);
		}
		return reaped;
	}

	@Override
	public synchronized UUID registerSyncWaiter(String owner, Instant now) {
		validateOwner(owner);
		Objects.requireNonNull(now, "now must not be null");
		removeExpiredSyncWaiters(now);

		UUID waiterId = UUID.randomUUID();
		syncWaiters.put(waiterId, new SyncWaiter(waiterId, owner, now.plus(syncWaiterTtl)));
		return waiterId;
	}

	@Override
	public synchronized boolean removeSyncWaiter(UUID waiterId) {
		Objects.requireNonNull(waiterId, "waiterId must not be null");
		return syncWaiters.remove(waiterId) != null;
	}

	@Override
	public synchronized long countLiveSyncWaiters(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		removeExpiredSyncWaiters(now);
		return countLiveSyncWaitersInternal(now);
	}

	private boolean hasAsyncCapacity() {
		long syncBusy = countBusySlotsInternal(SlotKind.SYNC);
		long asyncBusy = countBusySlotsInternal(SlotKind.ASYNC);
		int asyncAllowed = Math.max(0, totalSlots - (int) syncBusy - targetFreeSyncSlots);
		return asyncBusy < asyncAllowed;
	}

	private Optional<Integer> firstFreeSlotId() {
		for (int index = 0; index < slots.length; index++) {
			if (slots[index] == null) {
				return Optional.of(index + 1);
			}
		}
		return Optional.empty();
	}

	private long countBusySlotsInternal(SlotKind kind) {
		return Arrays.stream(slots)
				.filter(Objects::nonNull)
				.filter(lease -> lease.kind() == kind)
				.count();
	}

	private long countLiveSyncWaitersInternal(Instant now) {
		return syncWaiters.values().stream()
				.filter(waiter -> waiter.expiresAt().isAfter(now))
				.count();
	}

	private void removeExpiredSyncWaiters(Instant now) {
		syncWaiters.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
	}

	private Optional<String> normalizeTaskId(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(taskId);
	}

	private static void validateOwner(String owner) {
		Objects.requireNonNull(owner, "owner must not be null");
		if (owner.isBlank()) {
			throw new IllegalArgumentException("Владелец lease не должен быть пустым");
		}
	}

	private boolean isValidSlotId(int slotId) {
		return slotId >= 1 && slotId <= totalSlots;
	}

	private static int toIndex(int slotId) {
		return slotId - 1;
	}

	private record SyncWaiter(UUID waiterId, String owner, Instant expiresAt) {
	}
}
