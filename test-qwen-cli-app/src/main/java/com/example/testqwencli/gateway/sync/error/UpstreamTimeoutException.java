package com.example.testqwencli.gateway.sync.error;

import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Map;

public final class UpstreamTimeoutException extends ExternalGatewayException {

	public UpstreamTimeoutException(String requestId, Duration timeout, Duration plannedDelay) {
		super(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT",
				"Upstream не ответил в пределах sync timeout", true, requestId,
				Map.of(
						"syncTimeoutMs", timeout.toMillis(),
						"plannedDelayMs", plannedDelay.toMillis()
				));
	}
}
