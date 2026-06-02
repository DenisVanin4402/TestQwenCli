package com.example.testqwencli.dashboard;

import com.example.testqwencli.gateway.config.ExternalGatewayUpstreamProperties;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class GatewayDashboardSimulationDefaults implements ApplicationRunner {

	private final DashboardSimulationSettingsStore settingsStore;
	private final ExternalGatewayUpstreamProperties upstreamProperties;

	GatewayDashboardSimulationDefaults(
			DashboardSimulationSettingsStore settingsStore,
			ExternalGatewayUpstreamProperties upstreamProperties
	) {
		this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore must not be null");
		this.upstreamProperties = Objects.requireNonNull(upstreamProperties, "upstreamProperties must not be null");
	}

	@Override
	public void run(ApplicationArguments args) {
		DashboardSimulationSettings current = settingsStore.current();
		settingsStore.update(new DashboardSimulationSettings(
				Math.toIntExact(upstreamProperties.simulatedDelayMs().toMillis()),
				current.jitterMs(),
				current.errorRatePercent(),
				current.responseSizeKb(),
				current.callbackLatencyMs(),
				current.callbackErrorRatePercent()
		));
	}
}
