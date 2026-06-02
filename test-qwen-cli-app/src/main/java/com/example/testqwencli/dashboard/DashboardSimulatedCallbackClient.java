package com.example.testqwencli.dashboard;

import com.example.testqwencli.gateway.client.CallbackClient;
import com.example.testqwencli.gateway.model.callback.CallbackClientResponse;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Objects;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class DashboardSimulatedCallbackClient implements CallbackClient {

	private final DashboardSimulationSettingsStore settingsStore;

	DashboardSimulatedCallbackClient(DashboardSimulationSettingsStore settingsStore) {
		this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore must not be null");
	}

	@Override
	public CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId) {
		Objects.requireNonNull(payload, "payload must not be null");
		DashboardSimulationSettings settings = settingsStore.current();
		sleep(settings.callbackLatencyMs());
		if (ThreadLocalRandom.current().nextInt(100) < settings.callbackErrorRatePercent()) {
			return new CallbackClientResponse(503);
		}
		return new CallbackClientResponse(204);
	}

	private static void sleep(int latencyMs) {
		if (latencyMs <= 0) {
			return;
		}
		try {
			Thread.sleep(latencyMs);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Callback simulation interrupted", exception);
		}
	}
}
