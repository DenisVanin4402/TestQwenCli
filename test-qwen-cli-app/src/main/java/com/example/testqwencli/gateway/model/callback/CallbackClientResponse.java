package com.example.testqwencli.gateway.model.callback;

/**
 * Ответ HTTP callback endpoint после попытки доставки.
 *
 * @param statusCode HTTP-код ответа внешнего сервиса-получателя callback
 */
public record CallbackClientResponse(int statusCode) {

	public CallbackClientResponse {
		if (statusCode < 100 || statusCode > 599) {
			throw new IllegalArgumentException("HTTP statusCode должен быть в диапазоне 100..599");
		}
	}

	/**
	 * @return {@code true}, если callback endpoint вернул любой 2xx статус
	 */
	public boolean successful() {
		return statusCode >= 200 && statusCode < 300;
	}
}
