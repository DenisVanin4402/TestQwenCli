package com.example.testqwencli.gateway.slot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт координатора lease-слотов.
 *
 * <p>Реализация должна удерживать слот lease-записью, а не долгой транзакцией.
 * Release и heartbeat обязаны проверять пару {@code slotId + leaseId}, чтобы
 * старый поток не мог изменить уже переиспользованный слот.</p>
 */
public interface SlotRepository {

	Optional<SlotLease> acquireSyncSlot(String owner, Instant now);

	Optional<SlotLease> acquireAsyncSlot(String owner, String taskId, Instant now);

	boolean release(int slotId, UUID leaseId);

	Optional<SlotLease> heartbeat(int slotId, UUID leaseId, Instant now);

	long countBusySlots(SlotKind kind);

	int reapExpiredLeases(Instant now);

	UUID registerSyncWaiter(String owner, Instant now);

	boolean removeSyncWaiter(UUID waiterId);

	long countLiveSyncWaiters(Instant now);
}
