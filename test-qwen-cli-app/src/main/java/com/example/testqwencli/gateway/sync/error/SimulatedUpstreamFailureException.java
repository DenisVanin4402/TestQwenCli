package com.example.testqwencli.gateway.sync.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

public final class SimulatedUpstreamFailureException extends ExternalGatewayException {

	public SimulatedUpstreamFailureException(String requestId) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_SIMULATED_FAILURE",
				"Simulated upstream завершился ошибкой", true, requestId, Map.of());
	}
}
