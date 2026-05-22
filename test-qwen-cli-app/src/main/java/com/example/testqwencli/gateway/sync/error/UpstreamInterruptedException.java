package com.example.testqwencli.gateway.sync.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

public final class UpstreamInterruptedException extends ExternalGatewayException {

	public UpstreamInterruptedException(String requestId, InterruptedException cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_INTERRUPTED",
				"Выполнение simulated upstream было прервано", true, requestId, Map.of());
		initCause(cause);
	}
}
