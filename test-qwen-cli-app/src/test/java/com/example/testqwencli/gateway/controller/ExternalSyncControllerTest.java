package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.dashboard.DashboardGatewayRequest;
import com.example.testqwencli.dashboard.DashboardSimulationSettings;
import com.example.testqwencli.dashboard.DashboardSimulationSettingsStore;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.services.SlotManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"external-gateway.sync.wait-timeout-ms=1ms",
		"external-gateway.upstream.simulated-delay-ms=0ms",
		"external-gateway.slots.sync-acquire-poll-interval=1ms"
})
@AutoConfigureMockMvc
class ExternalSyncControllerTest {

	private static final UUID EXTERNAL_ID = UUID.fromString("4c48a4dc-3226-4e63-8597-4ee793fc3c3c");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SlotManager slotManager;

	@Autowired
	private AsyncTaskRepository taskRepository;

	@Autowired
	private DashboardSimulationSettingsStore simulationSettingsStore;

	@Test
	void syncReturnsSucceededResponseWithResultMap() throws Exception {
		Map<String, Object> request = Map.of(
				"externalId", EXTERNAL_ID,
				"clientService", "invest-pay",
				"payload", Map.of("operation", "calculate", "amount", 1000.50, "currency", "RUB")
		);

		mockMvc.perform(post("/v1/external/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-sync-success")
						.header("Idempotency-Key", "idempotency-123")
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.externalId").value(EXTERNAL_ID.toString()))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.result.decision").value("APPROVED"))
				.andExpect(jsonPath("$.result.score").value("82"))
				.andExpect(jsonPath("$.result.reasonCode").value("OK"))
				.andExpect(jsonPath("$.upstreamStatus").value(200))
				.andExpect(jsonPath("$.durationMs").isNumber());

		AsyncTask trace = onlyTrace(EXTERNAL_ID, "invest-pay");
		assertThat(trace.deliveryMode()).isEqualTo(AsyncDeliveryMode.SYNC);
		assertThat(trace.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(trace.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.NOT_REQUIRED);
		assertThat(trace.result()).containsEntry("decision", "APPROVED");
		assertThat(trace.attempts()).isEqualTo(1);
	}

	@Test
	void syncUpstreamTimeoutReturnsGatewayTimeoutAndPersistsFailedTrace() throws Exception {
		DashboardSimulationSettings originalSettings = simulationSettingsStore.current();
		simulationSettingsStore.update(new DashboardSimulationSettings(20, 0, 0, 1, 20, 0));
		UUID externalId = UUID.fromString("5d0fc1d9-1087-4642-9f48-0587e38559c1");
		try {
			Map<String, Object> request = Map.of(
					"externalId", externalId,
					"clientService", "invest-pay",
					"payload", Map.of(
							"operation", "calculate",
							DashboardGatewayRequest.SYNC_TIMEOUT_PAYLOAD_KEY, 1
					)
			);

			mockMvc.perform(post("/v1/external/sync")
							.contentType(MediaType.APPLICATION_JSON)
							.header("X-Request-Id", "req-upstream-timeout")
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isGatewayTimeout())
					.andExpect(jsonPath("$.code").value("UPSTREAM_TIMEOUT"))
					.andExpect(jsonPath("$.message").value("Upstream не ответил в пределах sync timeout"))
					.andExpect(jsonPath("$.retryable").value(true))
					.andExpect(jsonPath("$.requestId").value("req-upstream-timeout"))
					.andExpect(jsonPath("$.details.syncTimeoutMs").value(1))
					.andExpect(jsonPath("$.details.plannedDelayMs").value(20));

			AsyncTask trace = onlyTrace(externalId, "invest-pay");
			assertThat(trace.deliveryMode()).isEqualTo(AsyncDeliveryMode.SYNC);
			assertThat(trace.status()).isEqualTo(AsyncTaskStatus.FAILED);
			assertThat(trace.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.NOT_REQUIRED);
			assertThat(trace.attempts()).isEqualTo(1);
			assertThat(trace.error().code()).isEqualTo("UPSTREAM_TIMEOUT");
			assertThat(trace.error().retryable()).isTrue();
		}
		finally {
			simulationSettingsStore.update(originalSettings);
		}
	}

	@Test
	void syncSimulatedUpstreamFailureReturnsServiceUnavailableAndPersistsFailedTrace() throws Exception {
		DashboardSimulationSettings originalSettings = simulationSettingsStore.current();
		simulationSettingsStore.update(new DashboardSimulationSettings(0, 0, 100, 1, 20, 0));
		UUID externalId = UUID.fromString("24da58ee-af61-472e-ac58-afd1ac0c5a6a");
		try {
			Map<String, Object> request = Map.of(
					"externalId", externalId,
					"clientService", "invest-pay",
					"payload", Map.of("operation", "calculate")
			);

			mockMvc.perform(post("/v1/external/sync")
							.contentType(MediaType.APPLICATION_JSON)
							.header("X-Request-Id", "req-upstream-error")
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.code").value("UPSTREAM_SIMULATED_FAILURE"))
					.andExpect(jsonPath("$.message").value("Simulated upstream завершился ошибкой"))
					.andExpect(jsonPath("$.retryable").value(true))
					.andExpect(jsonPath("$.requestId").value("req-upstream-error"));

			AsyncTask trace = onlyTrace(externalId, "invest-pay");
			assertThat(trace.deliveryMode()).isEqualTo(AsyncDeliveryMode.SYNC);
			assertThat(trace.status()).isEqualTo(AsyncTaskStatus.FAILED);
			assertThat(trace.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.NOT_REQUIRED);
			assertThat(trace.attempts()).isEqualTo(1);
			assertThat(trace.error().code()).isEqualTo("UPSTREAM_SIMULATED_FAILURE");
			assertThat(trace.error().retryable()).isTrue();
		}
		finally {
			simulationSettingsStore.update(originalSettings);
		}
	}

	@Test
	void repeatedSyncRequestsWithSameExternalIdCreateSeparateTraceRows() throws Exception {
		UUID externalId = UUID.fromString("b38293f4-37d6-4b43-ae82-a544d4b4ac97");
		Map<String, Object> request = Map.of(
				"externalId", externalId,
				"clientService", "invest-pay",
				"payload", Map.of("operation", "calculate", "amount", 1000.50, "currency", "RUB")
		);

		mockMvc.perform(post("/v1/external/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-sync-repeat-first")
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));
		mockMvc.perform(post("/v1/external/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-sync-repeat-second")
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCEEDED"));

		List<AsyncTask> traces = taskRepository.findRequestTracesByExternalId(externalId,
				Optional.of("invest-pay"));
		assertThat(traces).hasSize(2);
		assertThat(traces).extracting(AsyncTask::deliveryMode)
				.containsExactly(AsyncDeliveryMode.SYNC, AsyncDeliveryMode.SYNC);
		assertThat(traces).extracting(AsyncTask::status)
				.containsExactly(AsyncTaskStatus.DONE, AsyncTaskStatus.DONE);
		assertThat(traces).extracting(AsyncTask::attempts).containsExactly(1, 1);
	}

	@Test
	void syncReturnsTooManyRequestsWhenAllSyncSlotsAreBusy() throws Exception {
		List<SlotLease> leases = acquireAllSyncSlots();
		try {
			Map<String, Object> request = Map.of(
					"externalId", UUID.fromString("56f57774-aa9d-470f-8cda-fc76f8309e5b"),
					"clientService", "invest-pay",
					"payload", Map.of("operation", "calculate")
			);

			mockMvc.perform(post("/v1/external/sync")
							.contentType(MediaType.APPLICATION_JSON)
							.header("X-Request-Id", "req-no-slot")
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isTooManyRequests())
					.andExpect(header().string("Retry-After", "1"))
					.andExpect(jsonPath("$.code").value("NO_SLOT_AVAILABLE"))
					.andExpect(jsonPath("$.retryable").value(true))
					.andExpect(jsonPath("$.requestId").value("req-no-slot"))
					.andExpect(jsonPath("$.details.syncWaitTimeoutMs").value(1));

			AsyncTask trace = onlyTrace(UUID.fromString("56f57774-aa9d-470f-8cda-fc76f8309e5b"), "invest-pay");
			assertThat(trace.deliveryMode()).isEqualTo(AsyncDeliveryMode.SYNC);
			assertThat(trace.status()).isEqualTo(AsyncTaskStatus.FAILED);
			assertThat(trace.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.NOT_REQUIRED);
			assertThat(trace.attempts()).isZero();
			assertThat(trace.error().code()).isEqualTo("NO_SLOT_AVAILABLE");
			assertThat(trace.error().retryable()).isTrue();
		}
		finally {
			leases.forEach(lease -> slotManager.release(lease.slotId(), lease.leaseId()));
		}
	}

	@Test
	void syncReturnsBadRequestWhenRequiredFieldIsMissing() throws Exception {
		Map<String, Object> request = Map.of(
				"clientService", "invest-pay",
				"payload", Map.of("operation", "calculate")
		);

		mockMvc.perform(post("/v1/external/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-validation")
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("Запрос не прошел валидацию"))
				.andExpect(jsonPath("$.retryable").value(false))
				.andExpect(jsonPath("$.requestId").value("req-validation"))
				.andExpect(jsonPath("$.details.fields.externalId").value("externalId обязателен"));
	}

	@Test
	void syncReturnsBadRequestWhenPayloadIsMissing() throws Exception {
		UUID externalId = UUID.fromString("8b21c8af-8708-4c19-9019-e84e2d74dcd1");
		Map<String, Object> request = Map.of(
				"externalId", externalId,
				"clientService", "invest-pay"
		);

		mockMvc.perform(post("/v1/external/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-validation-payload")
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("Запрос не прошел валидацию"))
				.andExpect(jsonPath("$.retryable").value(false))
				.andExpect(jsonPath("$.requestId").value("req-validation-payload"))
				.andExpect(jsonPath("$.details.fields.payload").value("payload обязателен"));

		assertThat(taskRepository.findRequestTracesByExternalId(externalId, Optional.empty())).isEmpty();
	}

	@Test
	void syncReturnsBadRequestWhenClientServiceIsBlank() throws Exception {
		assertBlankClientServiceRejected("", UUID.fromString("9e721cd0-f915-4e1d-8c3f-62cf334a6d64"),
				"req-validation-empty-client");
		assertBlankClientServiceRejected("  ", UUID.fromString("e79d91c8-1f8f-4029-83ef-64af8a555368"),
				"req-validation-blank-client");
	}

	private List<SlotLease> acquireAllSyncSlots() {
		return IntStream.range(0, 5)
				.mapToObj(index -> slotManager.acquireSyncSlot("preoccupied-sync-" + index, Duration.ZERO)
						.orElseThrow())
				.toList();
	}

	private void assertBlankClientServiceRejected(String clientService, UUID externalId, String requestId)
			throws Exception {
		Map<String, Object> request = Map.of(
				"externalId", externalId,
				"clientService", clientService,
				"payload", Map.of("operation", "calculate")
		);

		mockMvc.perform(post("/v1/external/sync")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", requestId)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("Запрос не прошел валидацию"))
				.andExpect(jsonPath("$.retryable").value(false))
				.andExpect(jsonPath("$.requestId").value(requestId))
				.andExpect(jsonPath("$.details.fields.clientService").value("clientService обязателен"));

		assertThat(taskRepository.findRequestTracesByExternalId(externalId, Optional.empty())).isEmpty();
	}

	private AsyncTask onlyTrace(UUID externalId, String clientService) {
		List<AsyncTask> traces = taskRepository.findRequestTracesByExternalId(externalId, Optional.of(clientService));
		assertThat(traces).hasSize(1);
		return traces.getFirst();
	}
}
