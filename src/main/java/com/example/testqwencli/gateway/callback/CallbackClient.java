package com.example.testqwencli.gateway.callback;

import java.net.URI;

public interface CallbackClient {

	CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId);
}
