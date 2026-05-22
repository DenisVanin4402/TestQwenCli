package com.example.testqwencli.gateway.callback;

public record CallbackClientResponse(int statusCode) {

	public CallbackClientResponse {
		if (statusCode < 100 || statusCode > 599) {
			throw new IllegalArgumentException("HTTP statusCode должен быть в диапазоне 100..599");
		}
	}

	public boolean successful() {
		return statusCode >= 200 && statusCode < 300;
	}
}
