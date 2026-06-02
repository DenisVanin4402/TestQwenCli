package com.example.testqwencli.gateway.services;

import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import java.util.Optional;

/**
 * Сервис планирования callback-доставки для финальной async-задачи.
 */
public interface CallbackDeliveryPlanner {

	/**
	 * Создает callback delivery, если задача требует callback и достигла финального статуса.
	 *
	 * <p>Для polling/sync задач callback не создается. Если для clientService не задан
	 * allow-listed callback URL, планировщик может создать {@code DEAD}-доставку для диагностики.</p>
	 *
	 * @param task финальная async-задача
	 * @return созданная доставка или {@link Optional#empty()}, если callback не требуется
	 */
	Optional<CallbackDelivery> planForFinalTask(AsyncTask task);
}
