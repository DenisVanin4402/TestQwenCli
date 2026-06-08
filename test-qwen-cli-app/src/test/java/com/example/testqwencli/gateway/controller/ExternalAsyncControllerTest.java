package com.example.testqwencli.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"external-gateway.repository.type=memory",
		"external-gateway.async.dispatcher-enabled=false",
		"external-gateway.callback.delivery-enabled=false"
})
@AutoConfigureMockMvc
class ExternalAsyncControllerTest {

	private static final String CLIENT_SERVICE = "invest-pay";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void postAsyncCreatesPendingTaskAndReturnsStatusUrl() throws Exception {
		UUID externalId = UUID.fromString("7d7b61df-6100-482d-8521-a1c43400ff01");

		mockMvc.perform(post("/v1/external/async")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-async-create")
						.content(objectMapper.writeValueAsString(defaultRequest(externalId))))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.taskId").isNumber())
				.andExpect(jsonPath("$.externalId").value(externalId.toString()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.deliveryMode").value("CALLBACK"))
				.andExpect(jsonPath("$.statusUrl").value(startsWith("/v1/external/async/")))
				.andExpect(jsonPath("$.alreadyExisted").value(false));
	}

	@Test
	void repeatedPostWithSameClientServiceAndExternalIdReturnsExistingTask() throws Exception {
		UUID externalId = UUID.fromString("1cebc6e0-41f4-47cb-88f1-a915f6dc7801");
		Map<String, Object> request = defaultRequest(externalId);
		long firstTaskId = taskId(submit(request, "req-async-first"));

		mockMvc.perform(post("/v1/external/async")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-async-second")
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.taskId").value(firstTaskId))
				.andExpect(jsonPath("$.alreadyExisted").value(true));
	}

	@Test
	void repeatedPostWithDifferentPayloadReturnsIdempotencyConflict() throws Exception {
		UUID externalId = UUID.fromString("7292d206-a94f-4ee5-b2e5-d6f22983c1a9");
		submit(defaultRequest(externalId), "req-async-original");
		Map<String, Object> conflictingRequest = Map.of(
				"externalId", externalId,
				"clientService", CLIENT_SERVICE,
				"priority", "HIGH",
				"deliveryMode", "CALLBACK",
				"payload", Map.of("operation", "calculate", "amount", 200)
		);

		mockMvc.perform(post("/v1/external/async")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-async-conflict")
						.content(objectMapper.writeValueAsString(conflictingRequest)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
				.andExpect(jsonPath("$.retryable").value(false))
				.andExpect(jsonPath("$.requestId").value("req-async-conflict"))
				.andExpect(jsonPath("$.details.conflictingFields[0]").value("payload"));
	}

	@Test
	void getByTaskIdAndExternalIdReturnTask() throws Exception {
		UUID externalId = UUID.fromString("b508d915-7c35-49c3-8c01-9022c1886f6b");
		long taskId = taskId(submit(defaultRequest(externalId), "req-async-get-create"));

		mockMvc.perform(get("/v1/external/async/{taskId}", taskId)
						.header("X-Client-Service", CLIENT_SERVICE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.externalId").value(externalId.toString()))
				.andExpect(jsonPath("$.clientService").value(CLIENT_SERVICE))
				.andExpect(jsonPath("$.priority").value("HIGH"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.callbackDeliveryStatus").value("PENDING"))
				.andExpect(jsonPath("$.attempts").value(0))
				.andExpect(jsonPath("$.maxAttempts").value(3));

		mockMvc.perform(get("/v1/external/async/by-external-id/{externalId}", externalId)
						.header("X-Client-Service", CLIENT_SERVICE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.externalId").value(externalId.toString()));
	}

	@Test
	void deletePendingTaskMovesItToCancelledAndRepeatedDeleteIsOk() throws Exception {
		UUID externalId = UUID.fromString("e638e42b-ebff-43bb-8ff4-c6f6f871a203");
		long taskId = taskId(submit(defaultRequest(externalId), "req-async-delete-create"));

		mockMvc.perform(delete("/v1/external/async/{taskId}", taskId)
						.header("X-Client-Service", CLIENT_SERVICE)
						.header("X-Request-Id", "req-async-delete"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.error.code").value("TASK_CANCELLED"));

		mockMvc.perform(delete("/v1/external/async/{taskId}", taskId)
						.header("X-Client-Service", CLIENT_SERVICE)
						.header("X-Request-Id", "req-async-delete-again"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void pollingDeliveryModeSetsCallbackDeliveryStatusToNotRequired() throws Exception {
		UUID externalId = UUID.fromString("916c9284-5ee3-499f-bdae-3037a92e57e5");
		Map<String, Object> request = Map.of(
				"externalId", externalId,
				"clientService", CLIENT_SERVICE,
				"priority", "LOW",
				"deliveryMode", "POLLING",
				"payload", Map.of("operation", "calculate", "amount", 100)
		);
		long taskId = taskId(submit(request, "req-async-polling"));

		mockMvc.perform(get("/v1/external/async/{taskId}", taskId)
						.header("X-Client-Service", CLIENT_SERVICE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deliveryMode").value("POLLING"))
				.andExpect(jsonPath("$.callbackDeliveryStatus").value("NOT_REQUIRED"));
	}

	@Test
	void retryPendingTaskReturnsConflict() throws Exception {
		UUID externalId = UUID.fromString("b2d74479-b393-4756-a8cc-7704b5750bbb");
		long taskId = taskId(submit(defaultRequest(externalId), "req-async-retry-create"));

		mockMvc.perform(post("/v1/external/async/{taskId}/retry", taskId)
						.header("X-Client-Service", CLIENT_SERVICE)
						.header("X-Request-Id", "req-async-retry"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TASK_STATE_CONFLICT"))
				.andExpect(jsonPath("$.requestId").value("req-async-retry"))
				.andExpect(jsonPath("$.details.currentStatus").value("PENDING"));
	}

	private MvcResult submit(Map<String, Object> request, String requestId) throws Exception {
		return mockMvc.perform(post("/v1/external/async")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", requestId)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted())
				.andReturn();
	}

	private long taskId(MvcResult result) throws Exception {
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
		return response.path("taskId").asLong();
	}

	private static Map<String, Object> defaultRequest(UUID externalId) {
		return Map.of(
				"externalId", externalId,
				"clientService", CLIENT_SERVICE,
				"priority", "HIGH",
				"deliveryMode", "CALLBACK",
				"payload", Map.of("operation", "calculate", "amount", 100)
		);
	}
}
