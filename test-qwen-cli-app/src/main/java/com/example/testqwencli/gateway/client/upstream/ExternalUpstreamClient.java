package com.example.testqwencli.gateway.client.upstream;

import com.example.testqwencli.gateway.model.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.model.upstream.ExternalUpstreamResponse;

/**
 * Клиент внешнего upstream-сервиса, защищенного общим лимитом слотов.
 */
public interface ExternalUpstreamClient {

	/**
	 * Выполняет upstream-вызов для sync или async сценария.
	 *
	 * @param request внутренний запрос gateway к upstream
	 * @return успешный ответ upstream
	 */
	ExternalUpstreamResponse call(ExternalUpstreamRequest request);
}
