package com.example.testqwencli.gateway.integration;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import com.example.testqwencli.gateway.model.sync.ExternalSyncResponse;
import com.example.testqwencli.gateway.model.sync.enums.ExternalSyncStatus;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.support.GatewayTestRequests;
import com.example.testqwencli.gateway.support.PostgresIntegrationTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
		"external-gateway.sync.wait-timeout-ms=250ms",
		"external-gateway.upstream.simulated-delay-ms=0ms",
		"external-gateway.async.dispatcher-enabled=true",
		"external-gateway.async.dispatch-interval-ms=25",
		"external-gateway.async.dispatch-batch-size=1",
		"external-gateway.callback.delivery-enabled=false"
})
class PostgresExternalGatewayHappyPathIT extends PostgresIntegrationTestSupport {

	@Autowired
	private TestRestTemplate restTemplate;

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
	void syncRequestUsesRealHttpAndPersistsTraceInPostgres() {
		UUID externalId = GatewayTestRequests.externalId(201);
		ExternalSyncRequest request = GatewayTestRequests.syncRequest(externalId);

		ResponseEntity<ExternalSyncResponse> response = restTemplate.postForEntity("/v1/external/sync",
				jsonEntity(request, "e2e-sync-success"), ExternalSyncResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).satisfies(body -> {
			assertThat(body).isNotNull();
			assertThat(body.externalId()).isEqualTo(externalId);
			assertThat(body.status()).isEqualTo(ExternalSyncStatus.SUCCEEDED);
			assertThat(body.result()).containsEntry("decision", "APPROVED");
			assertThat(body.result()).containsEntry("score", "82");
			assertThat(body.upstreamStatus()).isEqualTo(200);
			assertThat(body.durationMs()).isNotNegative();
		});

		AsyncTask trace = onlyTrace(externalId);
		assertThat(trace.deliveryMode()).isEqualTo(AsyncDeliveryMode.SYNC);
		assertThat(trace.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(trace.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.NOT_REQUIRED);
		assertThat(trace.result()).containsEntry("decision", "APPROVED");
		assertThat(trace.attempts()).isEqualTo(1);
		assertThat(trace.finishedAt()).isNotNull();
	}

	@Test
	void asyncPollingRequestUsesRealHttpDispatcherAndPersistsDoneTaskInPostgres() {
		UUID externalId = GatewayTestRequests.externalId(202);
		ExternalAsyncRequest request = GatewayTestRequests.asyncPollingRequest(externalId);

		ResponseEntity<AsyncSubmitResponse> submitResponse = restTemplate.postForEntity("/v1/external/async",
				jsonEntity(request, "e2e-async-submit"), AsyncSubmitResponse.class);

		assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(submitResponse.getBody()).satisfies(body -> {
			assertThat(body).isNotNull();
			assertThat(body.externalId()).isEqualTo(externalId);
			assertThat(body.status()).isEqualTo(AsyncTaskStatus.PENDING);
			assertThat(body.deliveryMode()).isEqualTo(AsyncDeliveryMode.POLLING);
			assertThat(body.alreadyExisted()).isFalse();
		});
		long taskId = submitResponse.getBody().taskId();

		AsyncTask apiTask = asyncAwaiter(Duration.ofSeconds(10)).until("async polling task DONE",
				() -> restTemplate.exchange("/v1/external/async/{taskId}", HttpMethod.GET,
						clientServiceEntity(), AsyncTask.class, taskId),
				response -> response.getStatusCode() == HttpStatus.OK
						&& response.getBody() != null
						&& response.getBody().status() == AsyncTaskStatus.DONE)
				.getBody();

		assertThat(apiTask).isNotNull();
		assertThat(apiTask.taskId()).isEqualTo(taskId);
		assertThat(apiTask.externalId()).isEqualTo(externalId);
		assertThat(apiTask.deliveryMode()).isEqualTo(AsyncDeliveryMode.POLLING);
		assertThat(apiTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.NOT_REQUIRED);
		assertThat(apiTask.result()).containsEntry("decision", "APPROVED");
		assertThat(apiTask.attempts()).isEqualTo(1);
		assertThat(apiTask.error()).isNull();
		assertThat(apiTask.finishedAt()).isNotNull();

		AsyncTask persistedTask = taskRepository.findByTaskId(taskId, Optional.of(GatewayTestRequests.CLIENT_SERVICE))
				.orElseThrow();
		assertThat(persistedTask).isEqualTo(apiTask);
	}

	private HttpEntity<Object> jsonEntity(Object body, String requestId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Request-Id", requestId);
		headers.set("Idempotency-Key", requestId + "-idempotency");
		return new HttpEntity<>(body, headers);
	}

	private HttpEntity<Void> clientServiceEntity() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Client-Service", GatewayTestRequests.CLIENT_SERVICE);
		headers.set("X-Request-Id", "e2e-async-poll");
		return new HttpEntity<>(headers);
	}

	private AsyncTask onlyTrace(UUID externalId) {
		List<AsyncTask> traces = taskRepository.findRequestTracesByExternalId(externalId,
				Optional.of(GatewayTestRequests.CLIENT_SERVICE));
		assertThat(traces).hasSize(1);
		return traces.getFirst();
	}
}
