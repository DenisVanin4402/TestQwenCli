package com.example.testqwencli.gateway.integration;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import com.example.testqwencli.gateway.config.ExternalGatewayClientsProperties;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.repository.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.support.GatewayTestRequests;
import com.example.testqwencli.gateway.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"external-gateway.repository.type=postgres",
		"external-gateway.postgres.liquibase-enabled=true",
		"external-gateway.slots.sync-acquire-wait-mode=polling",
		"external-gateway.slots.lease-reap-interval-ms=600000",
		"external-gateway.upstream.simulated-delay-ms=0ms",
		"external-gateway.async.dispatcher-enabled=true",
		"external-gateway.async.dispatch-interval-ms=25",
		"external-gateway.async.dispatch-batch-size=1",
		"external-gateway.callback.delivery-enabled=true",
		"external-gateway.callback.delivery-interval-ms=25",
		"external-gateway.callback.delivery-recovery-interval-ms=1000",
		"external-gateway.callback.delivery-batch-size=1",
		"external-gateway.callback.simulated-client-enabled=false"
})
class PostgresExternalGatewayCallbackIT extends PostgresIntegrationTestSupport {

	private static final CallbackEndpoint CALLBACK_ENDPOINT = CallbackEndpoint.start();

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ExternalGatewayClientsProperties clientsProperties;

	@Autowired
	private AsyncTaskRepository taskRepository;

	@Autowired
	private CallbackDeliveryRepository deliveryRepository;

	@DynamicPropertySource
	static void registerCallbackProperties(DynamicPropertyRegistry registry) {
		registry.add("external-gateway.clients[invest-pay].callback-url",
				() -> CALLBACK_ENDPOINT.url().toString());
	}

	@BeforeEach
	void cleanBeforeTest() {
		CALLBACK_ENDPOINT.reset();
		cleanGatewayTables();
	}

	@AfterEach
	void cleanAfterTest() {
		cleanGatewayTables();
		CALLBACK_ENDPOINT.reset();
	}

	@AfterAll
	static void stopCallbackEndpoint() {
		CALLBACK_ENDPOINT.stop();
	}

	@Test
	void asyncCallbackRequestDeliversHttpCallbackAndPersistsDeliveredStatuses() throws Exception {
		UUID externalId = GatewayTestRequests.externalId(203);
		ExternalAsyncRequest request = GatewayTestRequests.asyncCallbackRequest(externalId);
		assertThat(clientsProperties.callbackUrl(GatewayTestRequests.CLIENT_SERVICE))
				.contains(CALLBACK_ENDPOINT.url());

		ResponseEntity<AsyncSubmitResponse> submitResponse = restTemplate.postForEntity("/v1/external/async",
				jsonEntity(request, "e2e-callback-submit"), AsyncSubmitResponse.class);

		assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(submitResponse.getBody()).satisfies(body -> {
			assertThat(body).isNotNull();
			assertThat(body.externalId()).isEqualTo(externalId);
			assertThat(body.status()).isEqualTo(AsyncTaskStatus.PENDING);
			assertThat(body.deliveryMode()).isEqualTo(AsyncDeliveryMode.CALLBACK);
		});
		long taskId = submitResponse.getBody().taskId();

		CapturedCallback callback = CALLBACK_ENDPOINT.awaitCallback(Duration.ofSeconds(10));
		JsonNode callbackBody = objectMapper.readTree(callback.body());

		assertThat(callback.method()).isEqualTo("POST");
		assertThat(callback.path()).isEqualTo("/callbacks");
		assertThat(callback.header("X-Callback-Attempt")).isEqualTo("1");
		assertThat(callback.header("X-Request-Id")).isNotBlank();
		assertThat(callbackBody.path("eventId").asText()).isEqualTo(callback.header("X-Request-Id"));
		assertThat(callbackBody.path("taskId").asLong()).isEqualTo(taskId);
		assertThat(callbackBody.path("externalId").asText()).isEqualTo(externalId.toString());
		assertThat(callbackBody.path("clientService").asText()).isEqualTo(GatewayTestRequests.CLIENT_SERVICE);
		assertThat(callbackBody.path("status").asText()).isEqualTo("DONE");
		assertThat(callbackBody.path("result").path("decision").asText()).isEqualTo("APPROVED");

		AsyncTask deliveredTask = asyncAwaiter(Duration.ofSeconds(10)).untilPresent("callback task delivered",
				() -> taskRepository.findByTaskId(taskId, Optional.of(GatewayTestRequests.CLIENT_SERVICE))
						.filter(task -> task.status() == AsyncTaskStatus.DONE
								&& task.callbackDeliveryStatus() == CallbackDeliveryStatus.DELIVERED));
		CallbackDelivery deliveredCallback = asyncAwaiter(Duration.ofSeconds(10))
				.untilPresent("callback delivery delivered",
						() -> deliveryRepository.findByTaskId(taskId)
								.filter(delivery -> delivery.status() == CallbackDeliveryStatus.DELIVERED));

		assertThat(deliveredTask.result()).containsEntry("decision", "APPROVED");
		assertThat(deliveredTask.error()).isNull();
		assertThat(deliveredCallback.attempt()).isEqualTo(1);
		assertThat(deliveredCallback.completedAt()).isNotNull();
		assertThat(deliveredCallback.lastError()).isNull();
		assertThat(CALLBACK_ENDPOINT.capturedCount()).isEqualTo(1);

		ResponseEntity<AsyncTask> pollResponse = restTemplate.exchange("/v1/external/async/{taskId}",
				HttpMethod.GET, clientServiceEntity(), AsyncTask.class, taskId);
		assertThat(pollResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(pollResponse.getBody()).isEqualTo(deliveredTask);
	}

	private HttpEntity<Object> jsonEntity(Object body, String requestId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("X-Request-Id", requestId);
		return new HttpEntity<>(body, headers);
	}

	private HttpEntity<Void> clientServiceEntity() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Client-Service", GatewayTestRequests.CLIENT_SERVICE);
		headers.set("X-Request-Id", "e2e-callback-poll");
		return new HttpEntity<>(headers);
	}

	private record CapturedCallback(
			String method,
			String path,
			HttpHeaders headers,
			String body
	) {

		private String header(String name) {
			return headers.getFirst(name);
		}
	}

	private static final class CallbackEndpoint {

		private final HttpServer server;
		private final ExecutorService executor;
		private final URI url;
		private final BlockingQueue<CapturedCallback> queue = new LinkedBlockingQueue<>();
		private final List<CapturedCallback> captured = new CopyOnWriteArrayList<>();

		private CallbackEndpoint(HttpServer server, ExecutorService executor, URI url) {
			this.server = server;
			this.executor = executor;
			this.url = url;
		}

		static CallbackEndpoint start() {
			try {
				HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
				ExecutorService executor = Executors.newSingleThreadExecutor();
				URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/callbacks");
				CallbackEndpoint endpoint = new CallbackEndpoint(server, executor, url);
				server.createContext("/callbacks", endpoint::handle);
				server.setExecutor(executor);
				server.start();
				return endpoint;
			}
			catch (IOException exception) {
				throw new IllegalStateException("Failed to start callback endpoint", exception);
			}
		}

		URI url() {
			return url;
		}

		void reset() {
			queue.clear();
			captured.clear();
		}

		CapturedCallback awaitCallback(Duration timeout) {
			try {
				CapturedCallback callback = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
				if (callback == null) {
					throw new AssertionError("Callback endpoint did not receive HTTP callback in "
							+ timeout.toMillis() + " ms");
				}
				return callback;
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Callback wait was interrupted", exception);
			}
		}

		int capturedCount() {
			return captured.size();
		}

		void stop() {
			server.stop(0);
			executor.shutdownNow();
		}

		private void handle(HttpExchange exchange) throws IOException {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			HttpHeaders headers = new HttpHeaders();
			exchange.getRequestHeaders().forEach(headers::put);
			CapturedCallback callback = new CapturedCallback(exchange.getRequestMethod(),
					exchange.getRequestURI().getPath(), headers, body);
			captured.add(callback);
			queue.add(callback);
			exchange.sendResponseHeaders(HttpStatus.NO_CONTENT.value(), -1);
			exchange.close();
		}
	}
}
