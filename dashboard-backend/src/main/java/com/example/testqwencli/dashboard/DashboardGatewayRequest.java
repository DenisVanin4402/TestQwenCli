package com.example.testqwencli.dashboard;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DashboardGatewayRequest(
		UUID externalId,
		String clientService,
		DashboardRequestPriority priority,
		boolean callbackDelivery,
		Map<String, Object> payload,
		String requestId
) {

	public static final String SYNC_TIMEOUT_PAYLOAD_KEY = "dashboardSyncTimeoutMs";

	public DashboardGatewayRequest {
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(priority, "priority must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		payload = Map.copyOf(payload);
	}
}
