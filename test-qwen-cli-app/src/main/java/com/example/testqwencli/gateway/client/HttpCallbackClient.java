package com.example.testqwencli.gateway.client;

import com.example.testqwencli.gateway.model.callback.CallbackClientResponse;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import java.net.URI;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class HttpCallbackClient implements CallbackClient {

	private final RestClient restClient;

	HttpCallbackClient(RestClient.Builder restClientBuilder) {
		this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null").build();
	}

	@Override
	public CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId) {
		Objects.requireNonNull(payload, "payload must not be null");
		Objects.requireNonNull(url, "url must not be null");
		RestClient.RequestBodySpec request = restClient.post()
				.uri(url)
				.header("X-Callback-Attempt", Integer.toString(attempt));
		if (requestId != null && !requestId.isBlank()) {
			request.header("X-Request-Id", requestId);
		}
		int statusCode = request.body(payload)
				.retrieve()
				.toBodilessEntity()
				.getStatusCode()
				.value();
		return new CallbackClientResponse(statusCode);
	}
}
