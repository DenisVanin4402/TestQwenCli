package com.example.testqwencli.gateway.repository;

import com.example.testqwencli.gateway.model.slot.SlotKind;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт координатора lease-слотов.
 *
 * <p>Реализация должна удерживать слот lease-записью, а не долгой транзакцией
 * на строке слота. Release и heartbeat обязаны проверять пару {@code slotId + leaseId},
 * чтобы старый поток не мог изменить уже переиспользованный слот.</p>
 */
public interface SlotRepository {

	/**
	 * Пытается захватить слот для sync-запроса.
	 *
	 * @param owner владелец lease для диагностики
	 * @param now текущее время gateway
	 * @return lease, если слот доступен
	 */
	Optional<SlotLease> acquireSyncSlot(String owner, Instant now);

	/**
	 * Пытается захватить слот для async-задачи без ожидания.
	 *
	 * @param owner владелец lease для диагностики
	 * @param taskId id async-задачи, который будет записан в слот
	 * @param now текущее время gateway
	 * @return lease, если async-допуск разрешен и слот доступен
	 */
	Optional<SlotLease> acquireAsyncSlot(String owner, String taskId, Instant now);

	/**
	 * Освобождает слот при совпадении lease id.
	 *
	 * @param slotId номер слота
	 * @param leaseId id владения слотом
	 * @return {@code true}, если слот был освобожден
	 */
	boolean release(int slotId, UUID leaseId);

	/**
	 * Продлевает lease слота при совпадении lease id.
	 *
	 * @param slotId номер слота
	 * @param leaseId id владения слотом
	 * @param now текущее время gateway
	 * @return обновленный lease, если слот еще принадлежит владельцу
	 */
	Optional<SlotLease> heartbeat(int slotId, UUID leaseId, Instant now);

	/**
	 * Считает занятые и неистекшие слоты указанного типа.
	 *
	 * @param kind тип занятости слота
	 * @return количество активных lease
	 */
	long countBusySlots(SlotKind kind);

	/**
	 * Освобождает истекшие lease.
	 *
	 * @param now текущее время gateway
	 * @return количество освобожденных слотов
	 */
	int reapExpiredLeases(Instant now);

	/**
	 * Регистрирует sync-запрос, ожидающий свободный слот.
	 *
	 * @param owner владелец ожидания
	 * @param now текущее время gateway
	 * @return id записи ожидания
	 */
	UUID registerSyncWaiter(String owner, Instant now);

	/**
	 * Удаляет запись sync-ожидания.
	 *
	 * @param waiterId id ожидания
	 * @return {@code true}, если запись была удалена
	 */
	boolean removeSyncWaiter(UUID waiterId);

	/**
	 * Считает живых sync-ожидателей.
	 *
	 * @param now текущее время gateway
	 * @return количество ожидателей, чей TTL еще не истек
	 */
	long countLiveSyncWaiters(Instant now);
}
