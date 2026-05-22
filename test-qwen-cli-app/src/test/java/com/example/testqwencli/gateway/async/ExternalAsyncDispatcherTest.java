package com.example.testqwencli.gateway.async;

import com.example.testqwencli.gateway.async.config.ExternalGatewayAsyncProperties;
import com.example.testqwencli.gateway.async.memory.MemoryAsyncTaskRepository;
import com.example.testqwencli.gateway.slot.PollingSyncSlotWaitStrategy;
import com.example.testqwencli.gateway.slot.SlotManager;
import com.example.testqwencli.gateway.slot.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.slot.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.slot.memory.MemorySlotRepository;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamClient;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalAsyncDispatcherTest {

	private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");
	private static final Duration RETRY_BACKOFF = Duration.ofMillis(50);

	@Test
	void dispatchOnceMovesPendingTaskToDoneAndStoresResult() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository repository = new MemoryAsyncTaskRepository();
		AsyncTask task = submit(repository, "09199009-c69e-4e1e-bc08-5deec70e4c42", 3, clock.instant());
		RecordingUpstreamClient upstreamClient = RecordingUpstreamClient.succeeding(Map.of(
				"decision", "APPROVED",
				"score", "82"
		));
		ExternalAsyncDispatcher dispatcher = dispatcher(repository, upstreamClient, clock);

		assertThat(dispatcher.dispatchOnce()).isTrue();

		AsyncTask storedTask = repository.findByTaskId(task.taskId(), java.util.Optional.empty()).orElseThrow();
		assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(storedTask.result()).containsEntry("decision", "APPROVED");
		assertThat(storedTask.result()).containsEntry("score", "82");
		assertThat(storedTask.attempts()).isEqualTo(1);
		assertThat(upstreamClient.calls()).isEqualTo(1);
	}

	@Test
	void dispatchOnceDoesNotStartTaskWhenAsyncSlotIsUnavailable() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository repository = new MemoryAsyncTaskRepository();
		MemorySlotRepository slotRepository = new MemorySlotRepository(slotProperties());
		slotRepository.registerSyncWaiter("sync-waiter", clock.instant());
		AsyncTask task = submit(repository, "93c9fa5d-dd37-41a1-96fc-1ac373ce8dd4", 3, clock.instant());
		RecordingUpstreamClient upstreamClient = RecordingUpstreamClient.succeeding(Map.of("decision", "APPROVED"));
		ExternalAsyncDispatcher dispatcher = dispatcher(repository, slotRepository, upstreamClient, clock);

		assertThat(dispatcher.dispatchOnce()).isFalse();

		AsyncTask storedTask = repository.findByTaskId(task.taskId(), java.util.Optional.empty()).orElseThrow();
		assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.PENDING);
		assertThat(storedTask.attempts()).isZero();
		assertThat(upstreamClient.calls()).isZero();
	}

	@Test
	void secondDispatchOnceDoesNotProcessCompletedTaskAgain() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository repository = new MemoryAsyncTaskRepository();
		AsyncTask task = submit(repository, "4137a842-d98c-41e7-aea6-0d7272175d23", 3, clock.instant());
		RecordingUpstreamClient upstreamClient = RecordingUpstreamClient.succeeding(Map.of("decision", "APPROVED"));
		ExternalAsyncDispatcher dispatcher = dispatcher(repository, upstreamClient, clock);

		assertThat(dispatcher.dispatchOnce()).isTrue();
		AsyncTask completedTask = repository.findByTaskId(task.taskId(), java.util.Optional.empty()).orElseThrow();
		assertThat(dispatcher.dispatchOnce()).isFalse();

		AsyncTask storedTask = repository.findByTaskId(task.taskId(), java.util.Optional.empty()).orElseThrow();
		assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(storedTask.attempts()).isEqualTo(completedTask.attempts());
		assertThat(upstreamClient.calls()).isEqualTo(1);
	}

	@Test
	void runtimeExceptionReturnsTaskToPendingBeforeMaxAttemptsAndMovesToDeadAfterLimit() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository repository = new MemoryAsyncTaskRepository();
		AsyncTask task = submit(repository, "f6a16f1d-42f7-4c6b-b26d-92e377fc898d", 2, clock.instant());
		RecordingUpstreamClient upstreamClient = RecordingUpstreamClient.failing();
		ExternalAsyncDispatcher dispatcher = dispatcher(repository, upstreamClient, clock);

		assertThat(dispatcher.dispatchOnce()).isTrue();

		AsyncTask pendingTask = repository.findByTaskId(task.taskId(), java.util.Optional.empty()).orElseThrow();
		assertThat(pendingTask.status()).isEqualTo(AsyncTaskStatus.PENDING);
		assertThat(pendingTask.attempts()).isEqualTo(1);
		assertThat(pendingTask.availableAt()).isEqualTo(NOW.plus(RETRY_BACKOFF));
		assertThat(pendingTask.lastError()).isEqualTo("upstream timeout");
		assertThat(dispatcher.dispatchOnce()).isFalse();
		assertThat(upstreamClient.calls()).isEqualTo(1);

		clock.advance(RETRY_BACKOFF);
		assertThat(dispatcher.dispatchOnce()).isTrue();

		AsyncTask deadTask = repository.findByTaskId(task.taskId(), java.util.Optional.empty()).orElseThrow();
		assertThat(deadTask.status()).isEqualTo(AsyncTaskStatus.DEAD);
		assertThat(deadTask.attempts()).isEqualTo(2);
		assertThat(deadTask.error()).isNotNull();
		assertThat(deadTask.error().retryable()).isTrue();
		assertThat(upstreamClient.calls()).isEqualTo(2);
	}

	@Test
	void dispatchBatchRunsTasksConcurrentlyAndUsesAvailableAsyncSlots() {
		MutableClock clock = new MutableClock(NOW);
		MemoryAsyncTaskRepository repository = new MemoryAsyncTaskRepository();
		submit(repository, "04ccaf8c-9e26-4476-8b35-16b68136b4d1", 3, clock.instant());
		submit(repository, "e86d4d66-d1c9-403f-9518-5a9887f4dedf", 3, clock.instant());
		submit(repository, "f04e7244-62af-47b9-a4ca-59726da2d2bb", 3, clock.instant());
		BlockingUpstreamClient upstreamClient = new BlockingUpstreamClient(2);
		ExternalAsyncDispatcher dispatcher = dispatcher(repository, upstreamClient, clock);

		try {
			int completed = dispatcher.dispatchBatch(3);

			assertThat(completed).isEqualTo(3);
			assertThat(upstreamClient.maxActive()).isGreaterThan(1);
		}
		finally {
			dispatcher.shutdown();
		}
	}

	private static ExternalAsyncDispatcher dispatcher(
			MemoryAsyncTaskRepository repository,
			ExternalUpstreamClient upstreamClient,
			Clock clock
	) {
		return dispatcher(repository, new MemorySlotRepository(slotProperties()), upstreamClient, clock);
	}

	private static ExternalAsyncDispatcher dispatcher(
			MemoryAsyncTaskRepository repository,
			MemorySlotRepository slotRepository,
			ExternalUpstreamClient upstreamClient,
			Clock clock
	) {
		ExternalGatewaySlotProperties slotProperties = slotProperties();
		SlotManager slotManager = new SlotManager(slotRepository, clock,
				new PollingSyncSlotWaitStrategy(duration -> {
				}), slotProperties);
		return new ExternalAsyncDispatcher(repository, slotManager, upstreamClient, asyncProperties(), clock,
				java.util.Optional.empty());
	}

	private static AsyncTask submit(MemoryAsyncTaskRepository repository, String externalId, int maxAttempts,
			Instant now) {
		return repository.submit(new ExternalAsyncRequest(UUID.fromString(externalId), "invest-pay",
				AsyncPriority.HIGH, AsyncDeliveryMode.CALLBACK, Map.of("operation", "calculate")),
				maxAttempts, now).task();
	}

	private static ExternalGatewayAsyncProperties asyncProperties() {
		return new ExternalGatewayAsyncProperties(Duration.ofMillis(100), 3, false, 32, RETRY_BACKOFF);
	}

	private static ExternalGatewaySlotProperties slotProperties() {
		return new ExternalGatewaySlotProperties(2, 0, Duration.ofSeconds(30), Duration.ofSeconds(5),
				Duration.ofMillis(10), SyncAcquireWaitMode.POLLING);
	}

	private static final class RecordingUpstreamClient implements ExternalUpstreamClient {

		private final Map<String, String> result;
		private final boolean failing;
		private final AtomicInteger calls = new AtomicInteger();

		private RecordingUpstreamClient(Map<String, String> result, boolean failing) {
			this.result = result;
			this.failing = failing;
		}

		private static RecordingUpstreamClient succeeding(Map<String, String> result) {
			return new RecordingUpstreamClient(result, false);
		}

		private static RecordingUpstreamClient failing() {
			return new RecordingUpstreamClient(Map.of(), true);
		}

		@Override
		public ExternalUpstreamResponse call(ExternalUpstreamRequest request) {
			calls.incrementAndGet();
			if (failing) {
				throw new IllegalStateException("upstream timeout");
			}
			return new ExternalUpstreamResponse(result, 200, null);
		}

		private int calls() {
			return calls.get();
		}
	}

	private static final class BlockingUpstreamClient implements ExternalUpstreamClient {

		private final CountDownLatch enoughCallsStarted;
		private final AtomicInteger active = new AtomicInteger();
		private final AtomicInteger maxActive = new AtomicInteger();

		private BlockingUpstreamClient(int expectedConcurrentCalls) {
			this.enoughCallsStarted = new CountDownLatch(expectedConcurrentCalls);
		}

		@Override
		public ExternalUpstreamResponse call(ExternalUpstreamRequest request) {
			int current = active.incrementAndGet();
			maxActive.accumulateAndGet(current, Math::max);
			enoughCallsStarted.countDown();
			try {
				enoughCallsStarted.await(1, TimeUnit.SECONDS);
				return new ExternalUpstreamResponse(Map.of("decision", "APPROVED"), 200, null);
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
