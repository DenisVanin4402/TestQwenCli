package com.example.testqwencli.gateway.slot;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SlotLease(
		int slotId,
		UUID leaseId,
		String owner,
		SlotKind kind,
		Instant expiresAt,
		Optional<String> taskId
) {

	public SlotLease {
		if (slotId < 1) {
			throw new IllegalArgumentException("Идентификатор слота должен быть положительным");
		}
		Objects.requireNonNull(leaseId, "leaseId must not be null");
		Objects.requireNonNull(owner, "owner must not be null");
		Objects.requireNonNull(kind, "kind must not be null");
		Objects.requireNonNull(expiresAt, "expiresAt must not be null");
		taskId = taskId == null ? Optional.empty() : taskId;
		if (owner.isBlank()) {
			throw new IllegalArgumentException("Владелец lease не должен быть пустым");
		}
		taskId.ifPresent(value -> {
			if (value.isBlank()) {
				throw new IllegalArgumentException("Идентификатор async-задачи не должен быть пустым");
			}
		});
	}

	public static SlotLease sync(int slotId, String owner, Instant expiresAt) {
		return new SlotLease(slotId, UUID.randomUUID(), owner, SlotKind.SYNC, expiresAt, Optional.empty());
	}

	public static SlotLease async(int slotId, String owner, Instant expiresAt, Optional<String> taskId) {
		return new SlotLease(slotId, UUID.randomUUID(), owner, SlotKind.ASYNC, expiresAt, taskId);
	}
}
