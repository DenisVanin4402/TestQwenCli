package com.example.testqwencli.gateway.services;

import com.example.testqwencli.gateway.model.sync.ExternalSyncHeaders;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import com.example.testqwencli.gateway.model.sync.ExternalSyncResponse;

/**
 * Application service синхронного gateway API.
 */
public interface ExternalSyncService {

	/**
	 * Выполняет sync-вызов: получает слот, вызывает upstream и возвращает результат клиенту.
	 *
	 * <p>Независимо от исхода сервис пытается оставить trace-запись в общем журнале
	 * {@code ext_request_queue}.</p>
	 *
	 * @param request тело sync-запроса
	 * @param headers технические заголовки запроса
	 * @return успешный sync-ответ
	 */
	ExternalSyncResponse sync(ExternalSyncRequest request, ExternalSyncHeaders headers);
}
