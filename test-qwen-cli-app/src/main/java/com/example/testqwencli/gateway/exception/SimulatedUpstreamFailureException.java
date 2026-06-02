package com.example.testqwencli.gateway.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public final class SimulatedUpstreamFailureException extends ExternalGatewayException {

	public SimulatedUpstreamFailureException(String requestId) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_SIMULATED_FAILURE",
				"Simulated upstream завершился ошибкой", true, requestId, Map.of());
	}
}
