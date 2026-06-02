package com.example.testqwencli.gateway.client;

import com.example.testqwencli.gateway.model.callback.CallbackClientResponse;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import java.net.URI;

/**
 * Клиент доставки callback-события во внешний сервис-клиент.
 */
public interface CallbackClient {

	/**
	 * Отправляет callback payload на заданный URL.
	 *
	 * @param payload тело callback-события
	 * @param url allow-listed URL получателя
	 * @param attempt номер попытки доставки
	 * @param requestId id запроса/события для трассировки доставки
	 * @return HTTP-результат callback endpoint
	 */
	CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId);
}
