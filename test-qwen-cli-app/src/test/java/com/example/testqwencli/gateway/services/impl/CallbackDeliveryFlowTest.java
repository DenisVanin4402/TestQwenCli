package com.example.testqwencli.gateway.services.impl;

import com.example.testqwencli.gateway.client.CallbackClient;
import com.example.testqwencli.gateway.client.upstream.ExternalUpstreamClient;
import com.example.testqwencli.gateway.model.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.model.upstream.ExternalUpstreamResponse;
import com.example.testqwencli.gateway.config.ExternalGatewayAsyncProperties;
import com.example.testqwencli.gateway.config.ExternalGatewayCallbackProperties;
import com.example.testqwencli.gateway.config.ExternalGatewayClientsProperties;
import com.example.testqwencli.gateway.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.model.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.AsyncPriority;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.callback.CallbackClientResponse;
import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import com.example.testqwencli.gateway.model.slot.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.repository.memory.MemoryAsyncTaskRepository;
import com.example.testqwencli.gateway.repository.memory.MemoryCallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.memory.MemorySlotRepository;
import com.example.testqwencli.gateway.services.CallbackDeliveryDispatcher;
import com.example.testqwencli.gateway.services.CallbackDeliveryPlanner;
import com.example.testqwencli.gateway.services.ExternalAsyncDispatcher;
import com.example.testqwencli.gateway.services.impl.CallbackDeliveryDispatcherImpl;
import com.example.testqwencli.gateway.services.impl.CallbackDeliveryPlannerImpl;
import com.example.testqwencli.gateway.services.impl.ExternalAsyncDispatcherImpl;
import com.example.testqwencli.gateway.services.impl.PollingSyncSlotWaitStrategy;
import com.example.testqwencli.gateway.services.impl.SlotManagerImpl;
import com.example.testqwencli.gateway.services.SlotManager;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CallbackDeliveryFlowTest {

	private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");
	private static final Duration ASYNC_RETRY_BACKOFF = Duration.ofMillis(50);
	private static final Duration CALLBACK_RETRY_BACKOFF = Duration.ofMillis(100);
	private static final Duration CALLBACK_DELIVERY_TIMEOUT = Duration.ofSeconds(30);
	private static final URI INVEST_PAY_CALLBACK_URL =
			URI.create("http://invest-pay/internal/external-gateway/callbacks");

	@Test
	void doneCallbackTaskCreatesPendingCallbackDeliveryAndKeepsTaskDone() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				clientsWithInvestPay(), RecordingUpstreamClient.succeeding(doneResult()));
		AsyncTask task = submit(taskRepository, "09199009-c69e-4e1e-bc08-5deec70e4c42",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());

		assertThat(asyncDispatcher.dispatchOnce()).isTrue();

		AsyncTask storedTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(storedTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.PENDING);
		assertThat(storedTask.result()).containsEntry("decision", "APPROVED");

		CallbackDelivery delivery = deliveryRepository.findByTaskId(task.taskId()).orElseThrow();
		assertThat(delivery.status()).isEqualTo(CallbackDeliveryStatus.PENDING);
		assertThat(delivery.callbackUrl()).isEqualTo(INVEST_PAY_CALLBACK_URL);
		assertThat(delivery.payload().status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(delivery.payload().result()).containsEntry("score", "82");
		assertThat(delivery.payload().error()).isNull();
	}

	@Test
	void pollingTaskDoesNotCreateCallbackDeliveryAndKeepsNotRequiredStatus() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				clientsWithInvestPay(), RecordingUpstreamClient.succeeding(doneResult()));
		AsyncTask task = submit(taskRepository, "fb0abf69-4b20-4986-b184-a7036df1da7f",
				AsyncDeliveryMode.POLLING, "invest-pay", 3, clock.instant());

		assertThat(asyncDispatcher.dispatchOnce()).isTrue();

		AsyncTask storedTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(storedTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.NOT_REQUIRED);
		assertThat(deliveryRepository.findByTaskId(task.taskId())).isEmpty();
	}

	@Test
	void callbackDispatcherSendsPayloadAndMarksDeliveryDelivered(CapturedOutput output) {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				clientsWithInvestPay(), RecordingUpstreamClient.succeeding(doneResult()));
		AsyncTask task = submit(taskRepository, "a06e9271-b1c0-4e55-a91a-b69113769b18",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());
		asyncDispatcher.dispatchOnce();
		RecordingCallbackClient callbackClient = RecordingCallbackClient.succeeding(204);
		CallbackDeliveryDispatcher callbackDispatcher = callbackDispatcher(taskRepository, deliveryRepository,
				callbackClient, clock, 2);

		assertThat(callbackDispatcher.dispatchOnce()).isTrue();

		assertThat(callbackClient.calls()).isEqualTo(1);
		assertThat(callbackClient.lastAttempt()).isEqualTo(1);
		assertThat(callbackClient.lastUrl()).isEqualTo(INVEST_PAY_CALLBACK_URL);
		assertThat(callbackClient.lastPayload().taskId()).isEqualTo(task.taskId());
		assertThat(callbackClient.lastPayload().result()).containsEntry("decision", "APPROVED");
		assertThat(callbackClient.lastRequestId()).isEqualTo(callbackClient.lastPayload().eventId().toString());

		CallbackDelivery delivery = deliveryRepository.findByTaskId(task.taskId()).orElseThrow();
		assertThat(delivery.status()).isEqualTo(CallbackDeliveryStatus.DELIVERED);

		AsyncTask storedTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(storedTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.DELIVERED);
		assertThat(output.getOut()).contains("durationMs=");
	}

	@Test
	void callbackDispatcherDispatchBatchSendsDeliveriesConcurrently() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				clientsWithInvestPay(), RecordingUpstreamClient.succeeding(doneResult()));
		submit(taskRepository, "1dd4c602-d953-40b2-8106-9927d08f6e9c",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());
		submit(taskRepository, "3f4cb9be-6cb7-4494-84e3-398f26f23c78",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());
		submit(taskRepository, "b5fa620d-c606-4995-a5bf-3a6d01336ba4",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());
		asyncDispatcher.dispatchOnce();
		asyncDispatcher.dispatchOnce();
		asyncDispatcher.dispatchOnce();
		BlockingCallbackClient callbackClient = new BlockingCallbackClient(3);
		CallbackDeliveryDispatcher callbackDispatcher = callbackDispatcher(taskRepository, deliveryRepository,
				callbackClient, clock, 2);

		try {
			int completed = callbackDispatcher.dispatchBatch(3);

			assertThat(completed).isEqualTo(3);
			assertThat(callbackClient.maxActive()).isGreaterThan(1);
		}
		finally {
			callbackDispatcher.shutdown();
		}
	}

	@Test
	void callbackFailureDoesNotChangeAsyncTaskAndMovesDeliveryToRetryThenDead() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				clientsWithInvestPay(), RecordingUpstreamClient.succeeding(doneResult()));
		AsyncTask task = submit(taskRepository, "dedf2d6c-1287-4856-b88b-7c58d7606ba1",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());
		asyncDispatcher.dispatchOnce();
		RecordingCallbackClient callbackClient = RecordingCallbackClient.throwing();
		CallbackDeliveryDispatcher callbackDispatcher = callbackDispatcher(taskRepository, deliveryRepository,
				callbackClient, clock, 2);

		assertThat(callbackDispatcher.dispatchOnce()).isTrue();

		CallbackDelivery retryDelivery = deliveryRepository.findByTaskId(task.taskId()).orElseThrow();
		assertThat(retryDelivery.status()).isEqualTo(CallbackDeliveryStatus.RETRY);
		assertThat(retryDelivery.attempt()).isEqualTo(1);
		assertThat(retryDelivery.availableAt()).isEqualTo(NOW.plus(CALLBACK_RETRY_BACKOFF));
		assertThat(retryDelivery.lastError()).isEqualTo("callback timeout");

		AsyncTask retryTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(retryTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(retryTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.RETRY);

		assertThat(callbackDispatcher.dispatchOnce()).isFalse();

		clock.advance(CALLBACK_RETRY_BACKOFF);
		assertThat(callbackDispatcher.dispatchOnce()).isTrue();

		CallbackDelivery deadDelivery = deliveryRepository.findByTaskId(task.taskId()).orElseThrow();
		assertThat(deadDelivery.status()).isEqualTo(CallbackDeliveryStatus.DEAD);
		assertThat(deadDelivery.attempt()).isEqualTo(2);
		assertThat(deadDelivery.lastError()).isEqualTo("callback timeout");

		AsyncTask deadTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(deadTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(deadTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.DEAD);
	}

	@Test
	void timedOutDeliveringCallbackDeliveryReturnsToRetry() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				clientsWithInvestPay(), RecordingUpstreamClient.succeeding(doneResult()));
		AsyncTask task = submit(taskRepository, "10944cf1-855e-441e-8672-e9c8713d70cb",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());
		asyncDispatcher.dispatchOnce();
		CallbackDelivery claimed = deliveryRepository.claimNextPending(clock.instant()).orElseThrow();
		taskRepository.updateCallbackDeliveryStatus(claimed.taskId(), claimed.status(), clock.instant());
		CallbackDeliveryDispatcher callbackDispatcher = callbackDispatcher(taskRepository, deliveryRepository,
				RecordingCallbackClient.succeeding(204), clock, 2);

		clock.advance(CALLBACK_DELIVERY_TIMEOUT.minusMillis(1));
		assertThat(callbackDispatcher.recoverTimedOutDeliveries()).isZero();
		assertThat(deliveryRepository.findByTaskId(task.taskId()).orElseThrow().status())
				.isEqualTo(CallbackDeliveryStatus.DELIVERING);

		clock.advance(Duration.ofMillis(1));
		assertThat(callbackDispatcher.recoverTimedOutDeliveries()).isZero();
		assertThat(deliveryRepository.findByTaskId(task.taskId()).orElseThrow().status())
				.isEqualTo(CallbackDeliveryStatus.DELIVERING);

		clock.advance(Duration.ofMillis(1));
		assertThat(callbackDispatcher.recoverTimedOutDeliveries()).isEqualTo(1);

		CallbackDelivery recovered = deliveryRepository.findByTaskId(task.taskId()).orElseThrow();
		assertThat(recovered.status()).isEqualTo(CallbackDeliveryStatus.RETRY);
		assertThat(recovered.attempt()).isEqualTo(1);
		assertThat(recovered.availableAt()).isEqualTo(clock.instant().plus(CALLBACK_RETRY_BACKOFF));
		assertThat(recovered.lastError()).contains("зависла в DELIVERING");

		AsyncTask storedTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(storedTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.RETRY);
	}

	@Test
	void timedOutDeliveringCallbackDeliveryMovesToDeadAfterLastAttempt() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				clientsWithInvestPay(), RecordingUpstreamClient.succeeding(doneResult()), 1);
		AsyncTask task = submit(taskRepository, "ec627c8d-d9a4-4371-ae40-1601bc0758ec",
				AsyncDeliveryMode.CALLBACK, "invest-pay", 3, clock.instant());
		asyncDispatcher.dispatchOnce();
		CallbackDelivery claimed = deliveryRepository.claimNextPending(clock.instant()).orElseThrow();
		taskRepository.updateCallbackDeliveryStatus(claimed.taskId(), claimed.status(), clock.instant());
		CallbackDeliveryDispatcher callbackDispatcher = callbackDispatcher(taskRepository, deliveryRepository,
				RecordingCallbackClient.succeeding(204), clock, 1);

		clock.advance(CALLBACK_DELIVERY_TIMEOUT.plusMillis(1));
		assertThat(callbackDispatcher.recoverTimedOutDeliveries()).isEqualTo(1);

		CallbackDelivery recovered = deliveryRepository.findByTaskId(task.taskId()).orElseThrow();
		assertThat(recovered.status()).isEqualTo(CallbackDeliveryStatus.DEAD);
		assertThat(recovered.attempt()).isEqualTo(1);
		assertThat(recovered.completedAt()).isEqualTo(clock.instant());
		assertThat(recovered.lastError()).contains("зависла в DELIVERING");

		AsyncTask storedTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(storedTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.DEAD);
	}

	@Test
	void missingCallbackUrlDoesNotFailUpstreamTaskAndCreatesDeadDelivery() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository taskRepository = new MemoryAsyncTaskRepository();
		MemoryCallbackDeliveryRepository deliveryRepository = new MemoryCallbackDeliveryRepository();
		ExternalAsyncDispatcher asyncDispatcher = asyncDispatcher(taskRepository, deliveryRepository, clock,
				new ExternalGatewayClientsProperties(Map.of()), RecordingUpstreamClient.succeeding(doneResult()));
		AsyncTask task = submit(taskRepository, "38f94c69-9750-4f3a-90a6-f888dd5e3051",
				AsyncDeliveryMode.CALLBACK, "unknown-client", 3, clock.instant());

		assertThat(asyncDispatcher.dispatchOnce()).isTrue();

		AsyncTask storedTask = taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow();
		assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(storedTask.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.DEAD);
		assertThat(storedTask.result()).containsEntry("decision", "APPROVED");

		CallbackDelivery delivery = deliveryRepository.findByTaskId(task.taskId()).orElseThrow();
		assertThat(delivery.status()).isEqualTo(CallbackDeliveryStatus.DEAD);
		assertThat(delivery.callbackUrl()).isNull();
		assertThat(delivery.lastError()).contains("Callback URL не настроен");
	}

	private static ExternalAsyncDispatcher asyncDispatcher(
			MemoryAsyncTaskRepository taskRepository,
			MemoryCallbackDeliveryRepository deliveryRepository,
			Clock clock,
			ExternalGatewayClientsProperties clientsProperties,
			RecordingUpstreamClient upstreamClient
	) {
		return asyncDispatcher(taskRepository, deliveryRepository, clock, clientsProperties, upstreamClient, 2);
	}

	private static ExternalAsyncDispatcher asyncDispatcher(
			MemoryAsyncTaskRepository taskRepository,
			MemoryCallbackDeliveryRepository deliveryRepository,
			Clock clock,
			ExternalGatewayClientsProperties clientsProperties,
			RecordingUpstreamClient upstreamClient,
			int callbackMaxAttempts
	) {
		ExternalGatewaySlotProperties slotProperties = slotProperties();
		SlotManager slotManager = new SlotManagerImpl(new MemorySlotRepository(slotProperties), clock,
				new PollingSyncSlotWaitStrategy(duration -> {
				}), slotProperties);
		CallbackDeliveryPlanner planner = new CallbackDeliveryPlannerImpl(deliveryRepository, taskRepository,
				callbackProperties(callbackMaxAttempts), clientsProperties, clock);
		return new ExternalAsyncDispatcherImpl(taskRepository, slotManager, upstreamClient, asyncProperties(), clock,
				Optional.of(planner));
	}

	private static CallbackDeliveryDispatcher callbackDispatcher(
			MemoryAsyncTaskRepository taskRepository,
			MemoryCallbackDeliveryRepository deliveryRepository,
			CallbackClient callbackClient,
			Clock clock,
			int maxAttempts
	) {
		return new CallbackDeliveryDispatcherImpl(deliveryRepository, taskRepository, callbackClient,
				callbackProperties(maxAttempts), clock);
	}

	private static AsyncTask submit(MemoryAsyncTaskRepository repository, String externalId,
			AsyncDeliveryMode deliveryMode, String clientService, int maxAttempts, Instant now) {
		LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
		payload.put("operation", "calculate");
		payload.put("amount", 100);
		payload.put("callbackUrl", "http://attacker.invalid/callbacks");
		return repository.submit(new ExternalAsyncRequest(UUID.fromString(externalId), clientService,
				AsyncPriority.HIGH, deliveryMode, payload), maxAttempts, now).task();
	}

	private static Map<String, String> doneResult() {
		return Map.of(
				"decision", "APPROVED",
				"score", "82"
		);
	}

	private static ExternalGatewayAsyncProperties asyncProperties() {
		return new ExternalGatewayAsyncProperties(Duration.ofMillis(100), 3, false, 32, ASYNC_RETRY_BACKOFF);
	}

	private static ExternalGatewayCallbackProperties callbackProperties(int maxAttempts) {
		return new ExternalGatewayCallbackProperties(false, maxAttempts, 10, CALLBACK_RETRY_BACKOFF,
				Duration.ofMillis(100), CALLBACK_DELIVERY_TIMEOUT, Duration.ofSeconds(1));
	}

	private static ExternalGatewayClientsProperties clientsWithInvestPay() {
		return new ExternalGatewayClientsProperties(Map.of(
				"invest-pay", new ExternalGatewayClientsProperties.ClientProperties(INVEST_PAY_CALLBACK_URL)
		));
	}

	private static ExternalGatewaySlotProperties slotProperties() {
		return new ExternalGatewaySlotProperties(2, 0, Duration.ofSeconds(30), Duration.ofSeconds(5),
				Duration.ofMillis(10), SyncAcquireWaitMode.POLLING);
	}

	private static final class RecordingUpstreamClient implements ExternalUpstreamClient {

		private final Map<String, String> result;

		private RecordingUpstreamClient(Map<String, String> result) {
			this.result = result;
		}

		private static RecordingUpstreamClient succeeding(Map<String, String> result) {
			return new RecordingUpstreamClient(result);
		}

		@Override
		public ExternalUpstreamResponse call(ExternalUpstreamRequest request) {
			return new ExternalUpstreamResponse(result, 200, null);
		}
	}

	private static final class RecordingCallbackClient implements CallbackClient {

		private final int statusCode;
		private final boolean throwing;
		private final AtomicInteger calls = new AtomicInteger();
		private CallbackPayload lastPayload;
		private URI lastUrl;
		private int lastAttempt;
		private String lastRequestId;

		private RecordingCallbackClient(int statusCode, boolean throwing) {
			this.statusCode = statusCode;
			this.throwing = throwing;
		}

		private static RecordingCallbackClient succeeding(int statusCode) {
			return new RecordingCallbackClient(statusCode, false);
		}

		private static RecordingCallbackClient throwing() {
			return new RecordingCallbackClient(503, true);
		}

		@Override
		public CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId) {
			calls.incrementAndGet();
			lastPayload = payload;
			lastUrl = url;
			lastAttempt = attempt;
			lastRequestId = requestId;
			if (throwing) {
				throw new IllegalStateException("callback timeout");
			}
			return new CallbackClientResponse(statusCode);
		}

		private int calls() {
			return calls.get();
		}

		private CallbackPayload lastPayload() {
			return lastPayload;
		}

		private URI lastUrl() {
			return lastUrl;
		}

		private int lastAttempt() {
			return lastAttempt;
		}

		private String lastRequestId() {
			return lastRequestId;
		}
	}

	private static final class BlockingCallbackClient implements CallbackClient {

		private final CountDownLatch enoughCallsStarted;
		private final AtomicInteger active = new AtomicInteger();
		private final AtomicInteger maxActive = new AtomicInteger();

		private BlockingCallbackClient(int expectedConcurrentCalls) {
			this.enoughCallsStarted = new CountDownLatch(expectedConcurrentCalls);
		}

		@Override
		public CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId) {
			int current = active.incrementAndGet();
			maxActive.accumulateAndGet(current, Math::max);
			enoughCallsStarted.countDown();
			try {
				enoughCallsStarted.await(1, TimeUnit.SECONDS);
				return new CallbackClientResponse(204);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted", exception);
			}
			finally {
				active.decrementAndGet();
			}
		}

		private int maxActive() {
			return maxActive.get();
		}
	}

	private static final class MutableClock extends Clock {

		private final ZoneId zone;
		private Instant instant;

		private MutableClock(Instant instant) {
			this(instant, ZoneOffset.UTC);
		}

		private MutableClock(Instant instant, ZoneId zone) {
			this.instant = instant;
			this.zone = zone;
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
