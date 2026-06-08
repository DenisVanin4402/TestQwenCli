package com.example.testqwencli.gateway.model.slot;

import com.example.testqwencli.gateway.model.slot.enums.SlotKind;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lease на один из ограниченных слотов внешнего gateway.
 *
 * <p>Слот считается занятым, пока существует совпадающая пара {@code slotId + leaseId}
 * и {@link #expiresAt()} находится в будущем. {@code leaseId} защищает от ситуации,
 * когда старый поток пытается освободить уже переиспользованный слот.</p>
 *
 * @param slotId номер слота
 * @param leaseId уникальный id владения слотом
 * @param owner человекочитаемый владелец для диагностики
 * @param kind тип нагрузки, занявшей слот
 * @param expiresAt время истечения lease
 * @param taskId id async-задачи, если слот занят async-обработкой
 */
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

	/**
	 * Создает sync lease для in-memory реализации.
	 *
	 * @param slotId номер слота
	 * @param owner владелец слота
	 * @param expiresAt время истечения lease
	 * @return sync lease с новым {@code leaseId}
	 */
	public static SlotLease sync(int slotId, String owner, Instant expiresAt) {
		return new SlotLease(slotId, UUID.randomUUID(), owner, SlotKind.SYNC, expiresAt, Optional.empty());
	}

	/**
	 * Создает async lease для in-memory реализации.
	 *
	 * @param slotId номер слота
	 * @param owner владелец слота
	 * @param expiresAt время истечения lease
	 * @param taskId id async-задачи, если известен
	 * @return async lease с новым {@code leaseId}
	 */
	public static SlotLease async(int slotId, String owner, Instant expiresAt, Optional<String> taskId) {
		return new SlotLease(slotId, UUID.randomUUID(), owner, SlotKind.ASYNC, expiresAt, taskId);
	}
}
