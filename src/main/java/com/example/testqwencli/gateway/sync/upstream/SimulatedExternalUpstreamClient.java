package com.example.testqwencli.gateway.sync.upstream;

import com.example.testqwencli.gateway.sync.config.ExternalGatewayUpstreamProperties;
import com.example.testqwencli.gateway.sync.error.UpstreamInterruptedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Component
public final class SimulatedExternalUpstreamClient implements ExternalUpstreamClient {

	private static final Map<String, String> STABLE_RESULT = Map.of(
			"decision", "APPROVED",
			"score", "82",
			"reasonCode", "OK"
	);

	private final ExternalGatewayUpstreamProperties upstreamProperties;

	public SimulatedExternalUpstreamClient(ExternalGatewayUpstreamProperties upstreamProperties) {
		this.upstreamProperties = Objects.requireNonNull(upstreamProperties, "upstreamProperties must not be null");
	}

	@Override
	public ExternalUpstreamResponse call(ExternalUpstreamRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		sleep(request);
		return new ExternalUpstreamResponse(STABLE_RESULT, 200, null);
	}

	private void sleep(ExternalUpstreamRequest request) {
		Duration delay = upstreamProperties.simulatedDelayMs();
		if (delay.isZero()) {
			return;
		}
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new UpstreamInterruptedException(request.requestId(), exception);
		}
	}
}
