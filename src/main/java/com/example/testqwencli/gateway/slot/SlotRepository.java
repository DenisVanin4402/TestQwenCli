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

	/**
	 * Пытается занять свободный слот для синхронного вызова.
	 *
	 * @param owner владелец lease-записи
	 * @param now текущее время координатора
	 * @return lease слота или пустой результат, если свободного слота нет
	 */
	Optional<SlotLease> acquireSyncSlot(String owner, Instant now);

	/**
	 * Пытается занять свободный слот для асинхронной задачи с учетом политики sync-резерва.
	 *
	 * @param owner владелец lease-записи
	 * @param taskId идентификатор async-задачи
	 * @param now текущее время координатора
	 * @return lease слота или пустой результат, если async-вызов сейчас стартовать нельзя
	 */
	Optional<SlotLease> acquireAsyncSlot(String owner, String taskId, Instant now);

	/**
	 * Освобождает слот только при совпадении идентификатора слота и lease.
	 *
	 * @param slotId идентификатор слота
	 * @param leaseId идентификатор lease-записи
	 * @return {@code true}, если слот был освобожден
	 */
	boolean release(int slotId, UUID leaseId);

	/**
	 * Продлевает lease слота только при совпадении идентификатора слота и lease.
	 *
	 * @param slotId идентификатор слота
	 * @param leaseId идентификатор lease-записи
	 * @param now текущее время координатора
	 * @return обновленный lease или пустой результат, если lease уже неактуален
	 */
	Optional<SlotLease> heartbeat(int slotId, UUID leaseId, Instant now);

	/**
	 * Считает занятые слоты указанного типа.
	 *
	 * @param kind тип слота
	 * @return количество занятых слотов
	 */
	long countBusySlots(SlotKind kind);

	/**
	 * Освобождает lease-записи, срок действия которых истек.
	 *
	 * @param now текущее время координатора
	 * @return количество освобожденных слотов
	 */
	int reapExpiredLeases(Instant now);

	/**
	 * Регистрирует короткоживущую запись ожидающего sync-запроса.
	 *
	 * @param owner владелец ожидающего запроса
	 * @param now текущее время координатора
	 * @return идентификатор записи ожидания
	 */
	UUID registerSyncWaiter(String owner, Instant now);

	/**
	 * Удаляет запись ожидающего sync-запроса.
	 *
	 * @param waiterId идентификатор записи ожидания
	 * @return {@code true}, если запись была удалена
	 */
	boolean removeSyncWaiter(UUID waiterId);

	/**
	 * Считает живые записи ожидающих sync-запросов.
	 *
	 * @param now текущее время координатора
	 * @return количество активных ожидающих sync-запросов
	 */
	long countLiveSyncWaiters(Instant now);
}
