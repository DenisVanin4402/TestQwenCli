package com.example.testqwencli.dashboard;

import com.example.testqwencli.gateway.exception.UpstreamTimeoutException;
import com.example.testqwencli.gateway.services.ExternalAsyncService;
import com.example.testqwencli.gateway.services.ExternalSyncService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayDashboardClientAdapterTest {

	@Test
	void callSyncMapsUpstreamTimeoutToDashboardTimeout() {
		ExternalSyncService syncService = mock(ExternalSyncService.class);
		when(syncService.sync(any(), any()))
				.thenThrow(new UpstreamTimeoutException("req-timeout", Duration.ofMillis(100),
						Duration.ofMillis(250)));
		GatewayDashboardClientAdapter adapter = new GatewayDashboardClientAdapter(
				syncService,
				mock(ExternalAsyncService.class)
		);
		DashboardGatewayRequest request = new DashboardGatewayRequest(
				UUID.randomUUID(),
				"invest-pay",
				DashboardRequestPriority.HIGH,
				false,
				Map.of(DashboardGatewayRequest.SYNC_TIMEOUT_PAYLOAD_KEY, 100),
				"req-timeout"
		);

		DashboardCallOutcome outcome = adapter.callSync(request);

		assertThat(outcome.status()).isEqualTo(DashboardCallStatus.TIMEOUT);
		assertThat(outcome.code()).isEqualTo("UPSTREAM_TIMEOUT");
	}
}
