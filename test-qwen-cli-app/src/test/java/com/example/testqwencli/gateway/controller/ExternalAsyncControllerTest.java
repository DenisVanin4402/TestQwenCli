package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
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

	@Autowired
	private AsyncTaskRepository taskRepository;

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
	void malformedJsonReturnsInvalidRequestWithRequestId() throws Exception {
		String malformedJson = """
				{"externalId":"6ee9456e-d793-4e83-af23-53fdd8fd7b7f","clientService":"invest-pay","payload":
				""";

		mockMvc.perform(post("/v1/external/async")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Request-Id", "req-async-malformed")
						.content(malformedJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("Некорректный JSON или тип поля в запросе"))
				.andExpect(jsonPath("$.retryable").value(false))
				.andExpect(jsonPath("$.requestId").value("req-async-malformed"))
				.andExpect(jsonPath("$.details.reason").value("Тело запроса не соответствует контракту"));
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
	void getUnknownTaskReturnsNotFoundWithRequestId() throws Exception {
		mockMvc.perform(get("/v1/external/async/{taskId}", 990_001)
						.header("X-Client-Service", CLIENT_SERVICE)
						.header("X-Request-Id", "req-async-not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Async-задача не найдена"))
				.andExpect(jsonPath("$.retryable").value(false))
				.andExpect(jsonPath("$.requestId").value("req-async-not-found"))
				.andExpect(jsonPath("$.details.taskId").value(990_001));
	}

	@Test
	void getUnknownTaskWithoutOptionalHeadersReturnsNotFoundWithoutRequestId() throws Exception {
		mockMvc.perform(get("/v1/external/async/{taskId}", 990_002))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
				.andExpect(jsonPath("$.requestId").doesNotExist())
				.andExpect(jsonPath("$.details.taskId").value(990_002));
	}

	@Test
	void getTaskWithForeignClientServiceReturnsNotFound() throws Exception {
		UUID externalId = UUID.fromString("237cf9db-faae-43b4-afad-14ecce96e827");
		long taskId = taskId(submit(defaultRequest(externalId), "req-async-foreign-create"));

		mockMvc.perform(get("/v1/external/async/{taskId}", taskId)
						.header("X-Client-Service", "risk-core")
						.header("X-Request-Id", "req-async-foreign-get"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
				.andExpect(jsonPath("$.requestId").value("req-async-foreign-get"))
				.andExpect(jsonPath("$.details.taskId").value(taskId));
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
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
	void pollingTaskAfterDoneReturnsResultAndStablePublicFields() throws Exception {
		UUID externalId = UUID.fromString("1186b66f-ee2d-406d-a9b1-aeb46819dac0");
		long taskId = taskId(submit(pollingRequest(externalId), "req-async-done-create"));
		completeOnlyPendingTask(taskId, Map.of("decision", "APPROVED", "score", "82"));

		MvcResult result = mockMvc.perform(get("/v1/external/async/{taskId}", taskId)
						.header("X-Client-Service", CLIENT_SERVICE)
						.header("X-Request-Id", "req-async-done-get"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.externalId").value(externalId.toString()))
				.andExpect(jsonPath("$.clientService").value(CLIENT_SERVICE))
				.andExpect(jsonPath("$.priority").value("LOW"))
				.andExpect(jsonPath("$.deliveryMode").value("POLLING"))
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.callbackDeliveryStatus").value("NOT_REQUIRED"))
				.andExpect(jsonPath("$.result.decision").value("APPROVED"))
				.andExpect(jsonPath("$.result.score").value("82"))
				.andExpect(jsonPath("$.attempts").value(1))
				.andExpect(jsonPath("$.maxAttempts").value(3))
				.andExpect(jsonPath("$.retryable").value(false))
				.andReturn();

		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
		assertThat(fieldNames(response)).contains("taskId", "externalId", "clientService", "priority",
				"deliveryMode", "status", "callbackDeliveryStatus", "result", "error", "attempts",
				"maxAttempts", "createdAt", "availableAt", "startedAt", "finishedAt", "lastError",
				"retryable");
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

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
	void retryDeadRetryableTaskReturnsItToPending() throws Exception {
		UUID externalId = UUID.fromString("9ad5412c-21d0-4f60-80dc-e2fb456a4ba1");
		long taskId = taskId(submit(pollingRequest(externalId), "req-async-dead-create"));
		moveOnlyPendingTaskToDead(taskId);

		mockMvc.perform(post("/v1/external/async/{taskId}/retry", taskId)
						.header("X-Client-Service", CLIENT_SERVICE)
						.header("X-Request-Id", "req-async-dead-retry"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.taskId").value(taskId))
				.andExpect(jsonPath("$.externalId").value(externalId.toString()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.callbackDeliveryStatus").value("NOT_REQUIRED"))
				.andExpect(jsonPath("$.attempts").value(0))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andExpect(jsonPath("$.lastError").doesNotExist())
				.andExpect(jsonPath("$.retryable").value(false));
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

	private void completeOnlyPendingTask(long taskId, Map<String, String> result) {
		AsyncTaskClaim claim = claimSubmittedTask(taskId);
		assertThat(claim.task().taskId()).isEqualTo(taskId);
		taskRepository.complete(taskId, result, claim.task().startedAt().plusMillis(1)).orElseThrow();
	}

	private void moveOnlyPendingTaskToDead(long taskId) {
		AsyncTask task = null;
		for (int attempt = 0; attempt < 5 && (task == null || task.status() != AsyncTaskStatus.DEAD); attempt++) {
			AsyncTaskClaim claim = claimSubmittedTask(taskId);
			assertThat(claim.task().taskId()).isEqualTo(taskId);
			task = taskRepository.failTransient(taskId, "Временная ошибка upstream", Duration.ZERO,
					claim.task().startedAt().plusMillis(1)).orElseThrow();
		}
		assertThat(task).isNotNull();
		assertThat(task.status()).isEqualTo(AsyncTaskStatus.DEAD);
		assertThat(task.retryable()).isTrue();
	}

	private AsyncTaskClaim claimSubmittedTask(long taskId) {
		Instant availableAt = taskRepository.findByTaskId(taskId, Optional.empty())
				.orElseThrow()
				.availableAt();
		return claimNextPending(availableAt);
	}

	private AsyncTaskClaim claimNextPending(Instant now) {
		return taskRepository.executeInProcessingTransaction(() -> taskRepository.claimNextPending(now))
				.orElseThrow();
	}

	private static Set<String> fieldNames(JsonNode node) {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
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

	private static Map<String, Object> pollingRequest(UUID externalId) {
		return Map.of(
				"externalId", externalId,
				"clientService", CLIENT_SERVICE,
				"priority", "LOW",
				"deliveryMode", "POLLING",
				"payload", Map.of("operation", "calculate", "amount", 100)
		);
	}
}
