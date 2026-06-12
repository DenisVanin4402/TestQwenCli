package com.example.testqwencli.dashboard;

import com.example.testqwencli.dashboard.enums.DashboardCallStatus;
import com.example.testqwencli.dashboard.enums.DashboardRequestPriority;
import com.example.testqwencli.gateway.exception.UpstreamTimeoutException;
import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.sync.ExternalSyncHeaders;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import com.example.testqwencli.gateway.model.sync.ExternalSyncResponse;
import com.example.testqwencli.gateway.services.ExternalAsyncService;
import com.example.testqwencli.gateway.services.ExternalSyncService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayDashboardClientAdapterTest {

	@Test
	void callSyncMapsUpstreamTimeoutToDashboardTimeout() {
		GatewayDashboardClientAdapter adapter = new GatewayDashboardClientAdapter(
				new TimeoutSyncService(),
				new UnsupportedAsyncService()
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

	private static final class TimeoutSyncService implements ExternalSyncService {

		@Override
		public ExternalSyncResponse sync(ExternalSyncRequest request, ExternalSyncHeaders headers) {
			throw new UpstreamTimeoutException("req-timeout", Duration.ofMillis(100), Duration.ofMillis(250));
		}
	}

	private static final class UnsupportedAsyncService implements ExternalAsyncService {

		@Override
		public AsyncSubmitResponse submit(ExternalAsyncRequest request, String requestId) {
			throw unsupported();
		}

		@Override
		public AsyncTask getByTaskId(long taskId, String clientService, String requestId) {
			throw unsupported();
		}

		@Override
		public AsyncTask getByExternalId(UUID externalId, String clientService, String requestId) {
			throw unsupported();
		}

		@Override
		public AsyncTask cancel(long taskId, String clientService, String requestId) {
			throw unsupported();
		}

		@Override
		public AsyncTask retry(long taskId, String clientService, String requestId) {
			throw unsupported();
		}

		private static UnsupportedOperationException unsupported() {
			return new UnsupportedOperationException("Async service не используется в этом тесте");
		}
	}
}
