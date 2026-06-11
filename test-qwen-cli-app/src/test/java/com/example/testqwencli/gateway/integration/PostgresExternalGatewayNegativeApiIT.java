package com.example.testqwencli.gateway.integration;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncPriority;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.services.SlotManager;
import com.example.testqwencli.gateway.support.GatewayTestRequests;
import com.example.testqwencli.gateway.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"external-gateway.repository.type=postgres",
		"external-gateway.postgres.liquibase-enabled=true",
		"external-gateway.slots.sync-acquire-wait-mode=polling",
		"external-gateway.slots.lease-reap-interval-ms=600000",
		"external-gateway.slots.sync-acquire-poll-interval=1ms",
		"external-gateway.sync.wait-timeout-ms=1ms",
		"external-gateway.upstream.simulated-delay-ms=0ms",
		"external-gateway.async.dispatcher-enabled=false",
		"external-gateway.callback.delivery-enabled=false"
})
class PostgresExternalGatewayNegativeApiIT extends PostgresIntegrationTestSupport {

	private static final int TOTAL_SLOTS = 5;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private SlotManager slotManager;

	@Autowired
	private AsyncTaskRepository taskRepository;

	@BeforeEach
	void cleanBeforeTest() {
		cleanGatewayTables();
	}

	@AfterEach
	void cleanAfterTest() {
		cleanGatewayTables();
	}

	@Test
	void syncRequestReturnsTooManyRequestsAndPersistsFailedTraceWhenAllSlotsAreBusy() {
		UUID externalId = GatewayTestRequests.externalId(301);
		ExternalSyncRequest request = GatewayTestRequests.syncRequest(externalId);
		List<SlotLease> leases = acquireAllSyncSlots();
		try {
			ResponseEntity<JsonNode> response = restTemplate.postForEntity("/v1/external/sync",
					jsonEntity(request, "e2e-no-slot"), JsonNode.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
			JsonNode error = response.getBody();
			assertThat(error).isNotNull();
			assertThat(error.path("code").asText()).isEqualTo("NO_SLOT_AVAILABLE");
			assertThat(error.path("retryable").asBoolean()).isTrue();
			assertThat(error.path("requestId").asText()).isEqualTo("e2e-no-slot");
			assertThat(error.path("details").path("syncWaitTimeoutMs").asLong()).isEqualTo(1L);

			AsyncTask trace = onlyTrace(externalId);
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
	void malformedJsonReturnsInvalidRequestWithoutPersistingRows() {
		String body = """
				{"externalId":"%s","clientService":"%s","payload":
				""".formatted(GatewayTestRequests.externalId(302), GatewayTestRequests.CLIENT_SERVICE);

		ResponseEntity<JsonNode> response = restTemplate.postForEntity("/v1/external/sync",
				rawJsonEntity(body, "e2e-malformed-json"), JsonNode.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		JsonNode error = response.getBody();
		assertThat(error).isNotNull();
		assertThat(error.path("code").asText()).isEqualTo("INVALID_REQUEST");
		assertThat(error.path("retryable").asBoolean()).isFalse();
		assertThat(error.path("requestId").asText()).isEqualTo("e2e-malformed-json");
		assertThat(error.path("details").path("reason").asText())
				.isEqualTo("Тело запроса не соответствует контракту");
		assertThat(requestQueueRows()).isZero();
	}

	@Test
	void validationErrorReturnsStableRequestIdAndFieldDetails() {
		Map<String, Object> request = Map.of(
				"externalId", GatewayTestRequests.externalId(303),
				"priority", "HIGH",
				"deliveryMode", "POLLING"
		);

		ResponseEntity<JsonNode> response = restTemplate.postForEntity("/v1/external/async",
				jsonEntity(request, "e2e-validation"), JsonNode.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		JsonNode error = response.getBody();
		assertThat(error).isNotNull();
		assertThat(error.path("code").asText()).isEqualTo("VALIDATION_ERROR");
		assertThat(error.path("message").asText()).isEqualTo("Запрос не прошел валидацию");
		assertThat(error.path("retryable").asBoolean()).isFalse();
		assertThat(error.path("requestId").asText()).isEqualTo("e2e-validation");
		assertThat(error.path("details").path("fields").path("clientService").asText())
				.isEqualTo("clientService обязателен");
		assertThat(error.path("details").path("fields").path("payload").asText())
				.isEqualTo("payload обязателен");
		assertThat(requestQueueRows()).isZero();
	}

	@Test
	void asyncIdempotencyConflictReturnsConflictAndKeepsOriginalTask() {
		UUID externalId = GatewayTestRequests.externalId(304);
		AsyncSubmitResponse original = submitAsync(GatewayTestRequests.asyncPollingRequest(externalId),
				"e2e-conflict-original");
		Map<String, Object> conflictingRequest = asyncRequestMap(externalId, AsyncPriority.LOW,
				AsyncDeliveryMode.CALLBACK, Map.of("operation", "calculate", "amount", 200));

		ResponseEntity<JsonNode> response = restTemplate.postForEntity("/v1/external/async",
				jsonEntity(conflictingRequest, "e2e-conflict"), JsonNode.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		JsonNode error = response.getBody();
		assertThat(error).isNotNull();
		assertThat(error.path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
		assertThat(error.path("retryable").asBoolean()).isFalse();
		assertThat(error.path("requestId").asText()).isEqualTo("e2e-conflict");
		assertThat(error.path("details").path("existingTaskId").asLong()).isEqualTo(original.taskId());
		assertThat(textValues(error.path("details").path("conflictingFields")))
				.containsExactly("payload", "priority", "deliveryMode");

		assertThat(persistedTask(original.taskId())).satisfies(task -> {
			assertThat(task.externalId()).isEqualTo(externalId);
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.PENDING);
			assertThat(task.priority()).isEqualTo(AsyncPriority.HIGH);
			assertThat(task.deliveryMode()).isEqualTo(AsyncDeliveryMode.POLLING);
			assertThat(task.result()).isNull();
			assertThat(task.error()).isNull();
		});
		assertThat(taskRepository.findRequestTracesByExternalId(externalId,
				Optional.of(GatewayTestRequests.CLIENT_SERVICE))).hasSize(1);
	}

	@Test
	void cancelPendingTaskAndRepeatedCancelReturnCancelledTaskFromPostgres() {
		UUID externalId = GatewayTestRequests.externalId(305);
		AsyncSubmitResponse submitted = submitAsync(GatewayTestRequests.asyncCallbackRequest(externalId),
				"e2e-cancel-submit");

		ResponseEntity<AsyncTask> firstCancel = restTemplate.exchange("/v1/external/async/{taskId}",
				HttpMethod.DELETE, clientCommandEntity("e2e-cancel-first"), AsyncTask.class, submitted.taskId());
		ResponseEntity<AsyncTask> secondCancel = restTemplate.exchange("/v1/external/async/{taskId}",
				HttpMethod.DELETE, clientCommandEntity("e2e-cancel-second"), AsyncTask.class, submitted.taskId());

		assertThat(firstCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(firstCancel.getBody()).satisfies(task -> {
			assertThat(task).isNotNull();
			assertThat(task.taskId()).isEqualTo(submitted.taskId());
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.CANCELLED);
			assertThat(task.error().code()).isEqualTo("TASK_CANCELLED");
			assertThat(task.retryable()).isFalse();
		});
		assertThat(secondCancel.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(secondCancel.getBody()).satisfies(task -> {
			assertThat(task).isNotNull();
			assertThat(task.taskId()).isEqualTo(submitted.taskId());
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.CANCELLED);
			assertThat(task.error().code()).isEqualTo("TASK_CANCELLED");
		});
		assertThat(persistedTask(submitted.taskId())).isEqualTo(secondCancel.getBody());
	}

	@Test
	void retryPendingTaskReturnsStateConflictAndLeavesTaskPending() {
		UUID externalId = GatewayTestRequests.externalId(306);
		AsyncSubmitResponse submitted = submitAsync(GatewayTestRequests.asyncPollingRequest(externalId),
				"e2e-retry-submit");

		ResponseEntity<JsonNode> response = restTemplate.exchange("/v1/external/async/{taskId}/retry",
				HttpMethod.POST, clientCommandEntity("e2e-retry-pending"), JsonNode.class, submitted.taskId());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		JsonNode error = response.getBody();
		assertThat(error).isNotNull();
		assertThat(error.path("code").asText()).isEqualTo("TASK_STATE_CONFLICT");
		assertThat(error.path("retryable").asBoolean()).isFalse();
		assertThat(error.path("requestId").asText()).isEqualTo("e2e-retry-pending");
		assertThat(error.path("details").path("taskId").asLong()).isEqualTo(submitted.taskId());
		assertThat(error.path("details").path("currentStatus").asText()).isEqualTo("PENDING");

		assertThat(persistedTask(submitted.taskId())).satisfies(task -> {
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.PENDING);
			assertThat(task.error()).isNull();
			assertThat(task.retryable()).isFalse();
		});
	}

	private AsyncSubmitResponse submitAsync(ExternalAsyncRequest request, String requestId) {
		ResponseEntity<AsyncSubmitResponse> response = restTemplate.postForEntity("/v1/external/async",
				jsonEntity(request, requestId), AsyncSubmitResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody()).isNotNull();
		return response.getBody();
	}

	private HttpEntity<Object> jsonEntity(Object body, String requestId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Request-Id", requestId);
		return new HttpEntity<>(body, headers);
	}

	private HttpEntity<String> rawJsonEntity(String body, String requestId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Request-Id", requestId);
		return new HttpEntity<>(body, headers);
	}

	private HttpEntity<Void> clientCommandEntity(String requestId) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Client-Service", GatewayTestRequests.CLIENT_SERVICE);
		headers.set("X-Request-Id", requestId);
		return new HttpEntity<>(headers);
	}

	private List<SlotLease> acquireAllSyncSlots() {
		return IntStream.range(0, TOTAL_SLOTS)
				.mapToObj(index -> slotManager.acquireSyncSlot("preoccupied-sync-" + index, Duration.ZERO)
						.orElseThrow())
				.toList();
	}

	private AsyncTask onlyTrace(UUID externalId) {
		List<AsyncTask> traces = taskRepository.findRequestTracesByExternalId(externalId,
				Optional.of(GatewayTestRequests.CLIENT_SERVICE));
		assertThat(traces).hasSize(1);
		return traces.getFirst();
	}

	private AsyncTask persistedTask(long taskId) {
		return taskRepository.findByTaskId(taskId, Optional.of(GatewayTestRequests.CLIENT_SERVICE)).orElseThrow();
	}

	private Long requestQueueRows() {
		return jdbcTemplate().queryForObject("SELECT COUNT(*) FROM " + POSTGRES_SCHEMA + ".ext_request_queue",
				Long.class);
	}

	private static Map<String, Object> asyncRequestMap(UUID externalId, AsyncPriority priority,
			AsyncDeliveryMode deliveryMode, Map<String, Object> payload) {
		LinkedHashMap<String, Object> request = new LinkedHashMap<>();
		request.put("externalId", externalId);
		request.put("clientService", GatewayTestRequests.CLIENT_SERVICE);
		request.put("priority", priority);
		request.put("deliveryMode", deliveryMode);
		request.put("payload", payload);
		return request;
	}

	private static List<String> textValues(JsonNode array) {
		return StreamSupport.stream(array.spliterator(), false)
				.map(JsonNode::asText)
				.toList();
	}
}
