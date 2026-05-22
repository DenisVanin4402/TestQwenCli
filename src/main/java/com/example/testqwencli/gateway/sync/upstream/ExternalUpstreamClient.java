package com.example.testqwencli.gateway.sync.upstream;

public interface ExternalUpstreamClient {

	/**
	 * Выполняет вызов внешнего сервиса.
	 *
	 * @param request нормализованный запрос к внешнему сервису
	 * @return нормализованный ответ внешнего сервиса
	 */
	ExternalUpstreamResponse call(ExternalUpstreamRequest request);
}
