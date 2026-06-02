package com.example.testqwencli.gateway.repository;

import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import com.example.testqwencli.gateway.model.callback.CallbackDeliveryRepositoryStats;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт хранения callback-доставок.
 *
 * <p>Репозиторий отвечает за планирование доставки, claim очередной записи,
 * фиксацию успешной/неуспешной попытки и recovery доставок, зависших в
 * {@code DELIVERING} после остановки JVM.</p>
 */
public interface CallbackDeliveryRepository {

	/**
	 * Создает pending-доставку для финальной async-задачи.
	 *
	 * @param task финальная async-задача
	 * @param callbackUrl allow-listed URL сервиса-клиента
	 * @param maxAttempts максимальное число попыток доставки
	 * @param now текущее время gateway
	 * @return созданная доставка
	 */
	CallbackDelivery createPending(AsyncTask task, URI callbackUrl, int maxAttempts, Instant now);

	/**
	 * Создает финальную dead-доставку без HTTP-вызова.
	 *
	 * @param task финальная async-задача
	 * @param message причина невозможности доставки
	 * @param maxAttempts максимальное число попыток доставки
	 * @param now текущее время gateway
	 * @return созданная доставка в статусе {@code DEAD}
	 */
	CallbackDelivery createDead(AsyncTask task, String message, int maxAttempts, Instant now);

	/**
	 * Ищет доставку по id async-задачи.
	 *
	 * @param taskId id async-задачи
	 * @return доставка, если она уже была создана
	 */
	Optional<CallbackDelivery> findByTaskId(long taskId);

	/**
	 * Забирает следующую pending/retry доставку в работу.
	 *
	 * @param now текущее время gateway
	 * @return доставка в статусе {@code DELIVERING}, если backlog не пуст
	 */
	Optional<CallbackDelivery> claimNextPending(Instant now);

	/**
	 * Помечает доставку успешно выполненной.
	 *
	 * @param deliveryId id доставки
	 * @param now текущее время gateway
	 * @return обновленная доставка, если текущий статус позволял завершение
	 */
	Optional<CallbackDelivery> markDelivered(UUID deliveryId, Instant now);

	/**
	 * Фиксирует ошибку доставки и переводит запись в {@code RETRY} или {@code DEAD}.
	 *
	 * @param deliveryId id доставки
	 * @param message текст ошибки
	 * @param backoff задержка перед следующей попыткой
	 * @param now текущее время gateway
	 * @return обновленная доставка, если текущий статус позволял фиксацию ошибки
	 */
	Optional<CallbackDelivery> markRetryOrDead(UUID deliveryId, String message, Duration backoff, Instant now);

	/**
	 * Принудительно переводит доставку в {@code DEAD}.
	 *
	 * @param deliveryId id доставки
	 * @param message причина финального отказа
	 * @param now текущее время gateway
	 * @return обновленная доставка, если она найдена
	 */
	Optional<CallbackDelivery> markDead(UUID deliveryId, String message, Instant now);

	/**
	 * Восстанавливает доставки, которые слишком долго находятся в {@code DELIVERING}.
	 *
	 * @param timedOutBefore граница startedAt, до которой доставка считается зависшей
	 * @param message диагностическое сообщение recovery
	 * @param backoff задержка перед повторной доставкой
	 * @param now текущее время gateway
	 * @return список восстановленных доставок
	 */
	List<CallbackDelivery> recoverTimedOutDeliveries(Instant timedOutBefore, String message, Duration backoff,
			Instant now);

	/**
	 * Собирает статистику доставок для dashboard и health snapshot.
	 *
	 * @param now текущее время gateway
	 * @return счетчики callback-доставок
	 */
	CallbackDeliveryRepositoryStats stats(Instant now);
}
