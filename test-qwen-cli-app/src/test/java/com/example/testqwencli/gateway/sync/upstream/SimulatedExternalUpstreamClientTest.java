package com.example.testqwencli.gateway.sync.upstream;

import com.example.testqwencli.dashboard.DashboardGatewayRequest;
import com.example.testqwencli.dashboard.DashboardSimulationSettings;
import com.example.testqwencli.dashboard.DashboardSimulationSettingsStore;
import com.example.testqwencli.gateway.sync.config.ExternalGatewayUpstreamProperties;
import com.example.testqwencli.gateway.sync.error.UpstreamTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedExternalUpstreamClientTest {

	@Test
	void dashboardSyncTimeoutInterruptsSimulatedUpstreamDelay() {
		SimulatedExternalUpstreamClient client = new SimulatedExternalUpstreamClient(
				new ExternalGatewayUpstreamProperties(Duration.ZERO),
				store(new DashboardSimulationSettings(50, 0, 0, 1, 20, 0))
		);
		ExternalUpstreamRequest request = new ExternalUpstreamRequest(
				UUID.randomUUID(),
				"invest-pay",
				Map.of(DashboardGatewayRequest.SYNC_TIMEOUT_PAYLOAD_KEY, 1),
				"req-timeout",
				"idempotency-key"
		);

		assertThatThrownBy(() -> client.call(request))
				.isInstanceOfSatisfying(UpstreamTimeoutException.class, exception -> {
					assertThat(exception.code()).isEqualTo("UPSTREAM_TIMEOUT");
					assertThat(exception.details()).containsEntry("syncTimeoutMs", 1L);
					assertThat(exception.details()).containsEntry("plannedDelayMs", 50L);
				});
	}

	private static DashboardSimulationSettingsStore store(DashboardSimulationSettings settings) {
		return new DashboardSimulationSettingsStore() {
			@Override
			public DashboardSimulationSettings current() {
				return settings;
			}

			@Override
			public DashboardSimulationSettings update(DashboardSimulationSettings newSettings) {
				return newSettings;
			}
		};
	}
}
