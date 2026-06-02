package com.example.testqwencli.gateway.services;

import com.example.testqwencli.gateway.model.slot.SlotLease;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service управления лимитом одновременных upstream-вызовов.
 */
public interface SlotManager {

	/**
	 * Получает sync-слот с ожиданием до заданного timeout.
	 *
	 * @param owner владелец lease для диагностики
	 * @param timeout максимальное время ожидания свободного sync-слота
	 * @return lease, если слот был получен до timeout
	 */
	Optional<SlotLease> acquireSyncSlot(String owner, Duration timeout);

	/**
	 * Пытается получить async-слот без ожидания.
	 *
	 * @param owner владелец lease для диагностики
	 * @param taskId id async-задачи
	 * @return lease, если async-запуск разрешен сейчас
	 */
	Optional<SlotLease> tryAcquireAsyncSlot(String owner, String taskId);

	/**
	 * Освобождает слот при совпадении lease id.
	 *
	 * @param slotId номер слота
	 * @param leaseId id владения слотом
	 * @return {@code true}, если слот был освобожден
	 */
	boolean release(int slotId, UUID leaseId);

	/**
	 * Продлевает lease слота.
	 *
	 * @param slotId номер слота
	 * @param leaseId id владения слотом
	 * @return обновленный lease, если слот все еще принадлежит владельцу
	 */
	Optional<SlotLease> heartbeat(int slotId, UUID leaseId);

	/**
	 * Очищает истекшие lease.
	 *
	 * @return количество освобожденных слотов
	 */
	int reapExpiredLeases();
}
