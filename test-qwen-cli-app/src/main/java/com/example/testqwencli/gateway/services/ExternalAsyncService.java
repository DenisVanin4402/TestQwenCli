package com.example.testqwencli.gateway.services;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import java.util.UUID;

/**
 * Application service внешнего async API.
 *
 * <p>Сервис инкапсулирует submit, polling по taskId/externalId, cancel и manual retry,
 * а также переводит repository-level результаты в доменные ошибки HTTP слоя.</p>
 */
public interface ExternalAsyncService {

	/**
	 * Принимает async-запрос и ставит его в очередь.
	 *
	 * @param request тело async-запроса
	 * @param requestId id HTTP-запроса для диагностики ошибок
	 * @return response с id задачи и ссылкой на polling
	 */
	AsyncSubmitResponse submit(ExternalAsyncRequest request, String requestId);

	/**
	 * Возвращает задачу по внутреннему id.
	 *
	 * @param taskId id задачи
	 * @param clientService опциональный client-service фильтр
	 * @param requestId id HTTP-запроса для диагностики ошибок
	 * @return найденная async-задача
	 */
	AsyncTask getByTaskId(long taskId, String clientService, String requestId);

	/**
	 * Возвращает задачу по внешнему id.
	 *
	 * @param externalId внешний id операции
	 * @param clientService опциональный client-service фильтр
	 * @param requestId id HTTP-запроса для диагностики ошибок
	 * @return найденная async-задача
	 */
	AsyncTask getByExternalId(UUID externalId, String clientService, String requestId);

	/**
	 * Отменяет еще не начатую задачу.
	 *
	 * @param taskId id задачи
	 * @param clientService опциональный client-service фильтр
	 * @param requestId id HTTP-запроса для диагностики ошибок
	 * @return обновленная задача
	 */
	AsyncTask cancel(long taskId, String clientService, String requestId);

	/**
	 * Возвращает retryable финальную задачу в очередь.
	 *
	 * @param taskId id задачи
	 * @param clientService опциональный client-service фильтр
	 * @param requestId id HTTP-запроса для диагностики ошибок
	 * @return обновленная задача
	 */
	AsyncTask retry(long taskId, String clientService, String requestId);
}
