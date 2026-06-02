package com.example.testqwencli.gateway.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public final class UpstreamInterruptedException extends ExternalGatewayException {

	public UpstreamInterruptedException(String requestId, InterruptedException cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_INTERRUPTED",
				"Выполнение simulated upstream было прервано", true, requestId, Map.of());
		initCause(cause);
	}
}
