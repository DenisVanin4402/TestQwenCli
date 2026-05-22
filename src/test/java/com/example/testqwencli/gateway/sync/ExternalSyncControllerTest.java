package com.example.testqwencli.gateway.sync;

import com.example.testqwencli.gateway.slot.SlotLease;
import com.example.testqwencli.gateway.slot.SlotManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

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

	private List<SlotLease> acquireAllSyncSlots() {
		return IntStream.range(0, 5)
				.mapToObj(index -> slotManager.acquireSyncSlot("preoccupied-sync-" + index, Duration.ZERO)
						.orElseThrow())
				.toList();
	}
}
