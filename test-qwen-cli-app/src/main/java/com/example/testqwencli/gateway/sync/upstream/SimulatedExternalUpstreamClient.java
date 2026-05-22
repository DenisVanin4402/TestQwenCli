package com.example.testqwencli.gateway.sync.upstream;

import com.example.testqwencli.dashboard.DashboardGatewayRequest;
import com.example.testqwencli.dashboard.DashboardSimulationSettings;
import com.example.testqwencli.dashboard.DashboardSimulationSettingsStore;
import com.example.testqwencli.gateway.sync.config.ExternalGatewayUpstreamProperties;
import com.example.testqwencli.gateway.sync.error.SimulatedUpstreamFailureException;
import com.example.testqwencli.gateway.sync.error.UpstreamInterruptedException;
import com.example.testqwencli.gateway.sync.error.UpstreamTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
public final class SimulatedExternalUpstreamClient implements ExternalUpstreamClient {

	private static final Map<String, String> STABLE_RESULT = Map.of(
			"decision", "APPROVED",
			"score", "82",
			"reasonCode", "OK"
	);

	private final ExternalGatewayUpstreamProperties upstreamProperties;
	private final DashboardSimulationSettingsStore simulationSettingsStore;

	public SimulatedExternalUpstreamClient(
			ExternalGatewayUpstreamProperties upstreamProperties,
			DashboardSimulationSettingsStore simulationSettingsStore
	) {
		this.upstreamProperties = Objects.requireNonNull(upstreamProperties, "upstreamProperties must not be null");
		this.simulationSettingsStore = Objects.requireNonNull(simulationSettingsStore,
				"simulationSettingsStore must not be null");
	}

	@Override
	public ExternalUpstreamResponse call(ExternalUpstreamRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		DashboardSimulationSettings settings = simulationSettingsStore.current();
		Duration delay = delay(settings);
		Optional<Duration> syncTimeout = dashboardSyncTimeout(request);
		if (syncTimeout.isPresent() && delay.compareTo(syncTimeout.orElseThrow()) > 0) {
			Duration timeout = syncTimeout.orElseThrow();
			sleep(request, timeout);
			throw new UpstreamTimeoutException(request.requestId(), timeout, delay);
		}
		sleep(request, delay);
		if (ThreadLocalRandom.current().nextInt(100) < settings.errorRatePercent()) {
			throw new SimulatedUpstreamFailureException(request.requestId());
		}
		return new ExternalUpstreamResponse(result(settings), 200, null);
	}

	private void sleep(ExternalUpstreamRequest request, Duration delay) {
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

	private Optional<Duration> dashboardSyncTimeout(ExternalUpstreamRequest request) {
		Map<String, Object> payload = request.payload();
		if (payload == null) {
			return Optional.empty();
		}
		Object rawTimeout = payload.get(DashboardGatewayRequest.SYNC_TIMEOUT_PAYLOAD_KEY);
		if (rawTimeout instanceof Number number && number.longValue() > 0) {
			return Optional.of(Duration.ofMillis(number.longValue()));
		}
		if (rawTimeout instanceof String text && !text.isBlank()) {
			try {
				long timeoutMs = Long.parseLong(text.trim());
				return timeoutMs > 0 ? Optional.of(Duration.ofMillis(timeoutMs)) : Optional.empty();
			}
			catch (NumberFormatException exception) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	private Duration delay(DashboardSimulationSettings settings) {
		int jitter = settings.jitterMs() <= 0 ? 0 : ThreadLocalRandom.current().nextInt(settings.jitterMs() + 1);
		int configuredDelay = Math.toIntExact(upstreamProperties.simulatedDelayMs().toMillis());
		int baseDelay = settings.latencyMs() >= 0 ? settings.latencyMs() : configuredDelay;
		return Duration.ofMillis(Math.max(0, baseDelay + jitter));
	}

	private Map<String, String> result(DashboardSimulationSettings settings) {
		LinkedHashMap<String, String> result = new LinkedHashMap<>(STABLE_RESULT);
		int responseSize = Math.max(1, settings.responseSizeKb()) * 1024;
		result.put("responseSizeBytes", Integer.toString(responseSize));
		result.put("payload", "x".repeat(responseSize));
		return result;
	}
}
