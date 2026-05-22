package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.async.AsyncTask;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CallbackDeliveryRepository {

	/**
	 * Создает ожидающую доставку обратного вызова для финальной async-задачи.
	 *
	 * @param task финальная async-задача
	 * @param callbackUrl адрес сервиса-клиента
	 * @param maxAttempts максимальное количество попыток доставки
	 * @param now текущее время координатора
	 * @return созданная доставка
	 */
	CallbackDelivery createPending(AsyncTask task, URI callbackUrl, int maxAttempts, Instant now);

	/**
	 * Создает доставку в финальном статусе {@code DEAD}, когда отправка невозможна.
	 *
	 * @param task финальная async-задача
	 * @param message причина невозможности доставки
	 * @param maxAttempts максимальное количество попыток доставки
	 * @param now текущее время координатора
	 * @return созданная доставка
	 */
	CallbackDelivery createDead(AsyncTask task, String message, int maxAttempts, Instant now);

	/**
	 * Ищет доставку обратного вызова по идентификатору async-задачи.
	 *
	 * @param taskId идентификатор async-задачи
	 * @return найденная доставка или пустой результат
	 */
	Optional<CallbackDelivery> findByTaskId(long taskId);

	/**
	 * Захватывает следующую доставку, которую можно отправить сейчас.
	 *
	 * @param now текущее время координатора
	 * @return доставка для отправки или пустой результат
	 */
	Optional<CallbackDelivery> claimNextPending(Instant now);

	/**
	 * Помечает доставку успешно завершенной.
	 *
	 * @param deliveryId идентификатор доставки
	 * @param now текущее время координатора
	 * @return обновленная доставка или пустой результат, если доставка не найдена
	 */
	Optional<CallbackDelivery> markDelivered(UUID deliveryId, Instant now);

	/**
	 * Планирует повтор доставки или переводит доставку в {@code DEAD}, если попытки исчерпаны.
	 *
	 * @param deliveryId идентификатор доставки
	 * @param message диагностическое описание ошибки
	 * @param backoff задержка перед следующей попыткой
	 * @param now текущее время координатора
	 * @return обновленная доставка или пустой результат, если доставка не найдена
	 */
	Optional<CallbackDelivery> markRetryOrDead(UUID deliveryId, String message, Duration backoff, Instant now);

	/**
	 * Принудительно переводит доставку в {@code DEAD}.
	 *
	 * @param deliveryId идентификатор доставки
	 * @param message причина финального отказа
	 * @param now текущее время координатора
	 * @return обновленная доставка или пустой результат, если доставка не найдена
	 */
	Optional<CallbackDelivery> markDead(UUID deliveryId, String message, Instant now);
}
