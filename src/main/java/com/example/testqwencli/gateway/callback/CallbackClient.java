package com.example.testqwencli.gateway.callback;

import java.net.URI;

public interface CallbackClient {

	/**
	 * Отправляет HTTP-обратный вызов в сервис-клиент.
	 *
	 * @param payload тело обратного вызова
	 * @param url адрес сервиса-клиента
	 * @param attempt номер попытки доставки
	 * @param requestId идентификатор корреляции доставки
	 * @return ответ сервиса-клиента
	 */
	CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId);
}
