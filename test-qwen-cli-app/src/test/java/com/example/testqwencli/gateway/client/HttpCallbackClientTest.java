package com.example.testqwencli.gateway.client;

import com.example.testqwencli.gateway.model.callback.CallbackClientResponse;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import com.example.testqwencli.gateway.support.GatewayTestRequests;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCallbackClientTest {

	private static final JsonMapper JSON = JsonMapper.builder()
			.findAndAddModules()
			.build();

	@Test
	void sendPostsJsonPayloadWithAttemptAndRequestIdToTargetUri() throws Exception {
		try (RecordingCallbackServer server = RecordingCallbackServer.responding(204)) {
			CallbackPayload payload = GatewayTestRequests.doneCallbackPayload(321);
			URI targetUri = server.url("/callbacks/external-gateway?source=contract");

			CallbackClientResponse response = callbackClient().send(payload, targetUri, 3, "request-321");

			assertThat(response.statusCode()).isEqualTo(204);
			assertThat(response.successful()).isTrue();
			RecordedRequest request = server.takeRequest();
			assertThat(request.method()).isEqualTo("POST");
			assertThat(request.target()).isEqualTo("/callbacks/external-gateway?source=contract");
			assertThat(request.firstHeader("X-Callback-Attempt")).isEqualTo("3");
			assertThat(request.firstHeader("X-Request-Id")).isEqualTo("request-321");
			assertThat(request.firstHeader("Content-Type")).startsWith("application/json");

			JsonNode json = JSON.readTree(request.body());
			assertThat(json.path("eventId").asText()).isEqualTo(payload.eventId().toString());
			assertThat(json.path("taskId").asLong()).isEqualTo(payload.taskId());
			assertThat(json.path("externalId").asText()).isEqualTo(payload.externalId().toString());
			assertThat(json.path("clientService").asText()).isEqualTo(payload.clientService());
			assertThat(json.path("status").asText()).isEqualTo(payload.status().name());
			assertThat(json.path("result").path("decision").asText()).isEqualTo(payload.result().get("decision"));
			assertThat(json.path("result").path("score").asText()).isEqualTo(payload.result().get("score"));
			assertThat(json.path("finishedAt").asLong()).isEqualTo(payload.finishedAt().getEpochSecond());
		}
	}

	@Test
	void sendOmitsRequestIdHeaderWhenRequestIdIsBlank() throws Exception {
		try (RecordingCallbackServer server = RecordingCallbackServer.responding(202)) {
			CallbackClientResponse response = callbackClient().send(
					GatewayTestRequests.doneCallbackPayload(322), server.url("/callbacks"), 1, " ");

			assertThat(response.statusCode()).isEqualTo(202);
			RecordedRequest request = server.takeRequest();
			assertThat(request.firstHeader("X-Callback-Attempt")).isEqualTo("1");
			assertThat(request.headers()).doesNotContainKey("X-Request-Id");
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 503 })
	void sendThrowsHttpExceptionForNon2xxResponse(int statusCode) throws Exception {
		try (RecordingCallbackServer server = RecordingCallbackServer.responding(statusCode)) {
			assertThatThrownBy(() -> callbackClient().send(
					GatewayTestRequests.doneCallbackPayload(statusCode), server.url("/callbacks"), 2, "request-error"))
					.isInstanceOf(RestClientResponseException.class)
					.satisfies(exception -> assertThat(((RestClientResponseException) exception).getStatusCode()
							.value()).isEqualTo(statusCode));

			RecordedRequest request = server.takeRequest();
			assertThat(request.method()).isEqualTo("POST");
			assertThat(request.firstHeader("X-Callback-Attempt")).isEqualTo("2");
			assertThat(request.firstHeader("X-Request-Id")).isEqualTo("request-error");
		}
	}

	@Test
	void sendThrowsResourceAccessExceptionWhenServerDoesNotRespondBeforeReadTimeout() throws Exception {
		try (RecordingCallbackServer server = RecordingCallbackServer.respondingAfter(204, Duration.ofSeconds(2))) {
			HttpCallbackClient client = callbackClient(Duration.ofMillis(100), Duration.ofMillis(100));

			assertThatThrownBy(() -> client.send(
					GatewayTestRequests.doneCallbackPayload(323), server.url("/callbacks"), 1, "request-timeout"))
					.isInstanceOf(ResourceAccessException.class);

			assertThat(server.takeRequest().method()).isEqualTo("POST");
		}
	}

	@Test
	void sendThrowsResourceAccessExceptionWhenConnectionFailsBeforeHttpResponse() throws Exception {
		HttpCallbackClient client = callbackClient(Duration.ofMillis(200), Duration.ofMillis(200));
		URI unavailableUrl = closedLoopbackPortUrl();

		assertThatThrownBy(() -> client.send(
				GatewayTestRequests.doneCallbackPayload(324), unavailableUrl, 1, "request-connection-failed"))
				.isInstanceOf(ResourceAccessException.class);
	}

	private static HttpCallbackClient callbackClient() {
		return new HttpCallbackClient(RestClient.builder());
	}

	private static HttpCallbackClient callbackClient(Duration connectTimeout, Duration readTimeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
		requestFactory.setReadTimeout((int) readTimeout.toMillis());
		return new HttpCallbackClient(RestClient.builder().requestFactory(requestFactory));
	}

	private static URI closedLoopbackPortUrl() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			return URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/callbacks");
		}
	}

	private record RecordedRequest(String method, String target, Map<String, List<String>> headers, String body) {

		private String firstHeader(String name) {
			List<String> values = headers.get(name);
			if (values == null || values.isEmpty()) {
				return null;
			}
			return values.getFirst();
		}
	}

	private static final class RecordingCallbackServer implements AutoCloseable {

		private final HttpServer server;
		private final ExecutorService executor;
		private final BlockingQueue<RecordedRequest> requests = new ArrayBlockingQueue<>(4);
		private final int statusCode;
		private final Duration responseDelay;

		private RecordingCallbackServer(int statusCode, Duration responseDelay) throws IOException {
			this.statusCode = statusCode;
			this.responseDelay = responseDelay;
			this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			this.executor = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "http-callback-client-test-server");
				thread.setDaemon(true);
				return thread;
			});
			server.createContext("/", this::handle);
			server.setExecutor(executor);
			server.start();
		}

		private static RecordingCallbackServer responding(int statusCode) throws IOException {
			return new RecordingCallbackServer(statusCode, Duration.ZERO);
		}

		private static RecordingCallbackServer respondingAfter(int statusCode, Duration responseDelay)
				throws IOException {
			return new RecordingCallbackServer(statusCode, responseDelay);
		}

		private URI url(String target) {
			return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + target);
		}

		private RecordedRequest takeRequest() throws InterruptedException {
			RecordedRequest request = requests.poll(2, TimeUnit.SECONDS);
			assertThat(request).as("callback endpoint должен получить HTTP-запрос").isNotNull();
			return request;
		}

		private void handle(HttpExchange exchange) throws IOException {
			byte[] body = exchange.getRequestBody().readAllBytes();
			requests.add(new RecordedRequest(
					exchange.getRequestMethod(),
					exchange.getRequestURI().toString(),
					copyHeaders(exchange.getRequestHeaders()),
					new String(body, StandardCharsets.UTF_8)));
			sleepBeforeResponse();
			try {
				exchange.sendResponseHeaders(statusCode, -1);
			}
			catch (IOException ignored) {
				// Клиент мог закрыть соединение после read timeout.
			}
			finally {
				exchange.close();
			}
		}

		private void sleepBeforeResponse() {
			if (responseDelay.isZero()) {
				return;
			}
			try {
				Thread.sleep(responseDelay.toMillis());
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}

		private static Map<String, List<String>> copyHeaders(Headers headers) {
			Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
			return copy;
		}

		@Override
		public void close() {
			server.stop(0);
			executor.shutdownNow();
		}
	}
}
