package com.example.testqwencli.gateway.integration;

import com.example.testqwencli.gateway.client.CallbackClient;
import com.example.testqwencli.gateway.client.upstream.ExternalUpstreamClient;
import com.example.testqwencli.gateway.config.ExternalGatewayAsyncProperties;
import com.example.testqwencli.gateway.config.ExternalGatewayCallbackProperties;
import com.example.testqwencli.gateway.config.ExternalGatewaySlotProperties;
import com.example.testqwencli.gateway.model.async.AsyncSubmitResult;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncPriority;
import com.example.testqwencli.gateway.model.async.enums.AsyncSubmitResultType;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.callback.CallbackClientResponse;
import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.model.slot.enums.SlotKind;
import com.example.testqwencli.gateway.model.slot.enums.SyncAcquireWaitMode;
import com.example.testqwencli.gateway.model.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.model.upstream.ExternalUpstreamResponse;
import com.example.testqwencli.gateway.repository.AsyncTaskRepository;
import com.example.testqwencli.gateway.repository.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.repository.SlotRepository;
import com.example.testqwencli.gateway.services.SlotManager;
import com.example.testqwencli.gateway.services.impl.CallbackDeliveryDispatcherImpl;
import com.example.testqwencli.gateway.services.impl.ExternalAsyncDispatcherImpl;
import com.example.testqwencli.gateway.services.impl.PollingSyncSlotWaitStrategy;
import com.example.testqwencli.gateway.services.impl.SlotManagerImpl;
import com.example.testqwencli.gateway.support.GatewayTestRequests;
import com.example.testqwencli.gateway.support.MutableTestClock;
import com.example.testqwencli.gateway.support.PostgresIntegrationTestSupport;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
		"external-gateway.repository.type=postgres",
		"external-gateway.postgres.liquibase-enabled=true",
		"external-gateway.slots.sync-acquire-wait-mode=polling",
		"external-gateway.slots.lease-reap-interval-ms=600000",
		"external-gateway.async.dispatcher-enabled=false",
		"external-gateway.callback.delivery-enabled=false"
})
class PostgresConcurrencyIT extends PostgresIntegrationTestSupport {

	private static final int TOTAL_SLOTS = 5;
	private static final int TARGET_FREE_SYNC_SLOTS = 1;
	private static final int ASYNC_MAX_ATTEMPTS = 3;
	private static final int CALLBACK_MAX_ATTEMPTS = 3;
	private static final Duration FUTURE_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration RETRY_BACKOFF = Duration.ofMillis(100);
	private static final Duration CALLBACK_TIMEOUT = Duration.ofSeconds(30);

	@Autowired
	private AsyncTaskRepository taskRepository;

	@Autowired
	private CallbackDeliveryRepository deliveryRepository;

	@Autowired
	private SlotRepository slotRepository;

	@BeforeEach
	void cleanBeforeTest() {
		cleanGatewayTables();
	}

	@AfterEach
	void cleanAfterTest() {
		cleanGatewayTables();
	}

	@Test
	void concurrentAsyncDispatchersDoNotProcessSamePostgresTaskTwice() throws Exception {
		Instant now = currentInstant();
		AsyncTask task = taskRepository.submit(asyncRequest(1601, AsyncDeliveryMode.POLLING),
				ASYNC_MAX_ATTEMPTS, now).task();
		MutableTestClock clock = new MutableTestClock(now.plusMillis(1));
		BlockingUpstreamClient upstreamClient = new BlockingUpstreamClient();
		ExternalAsyncDispatcherImpl firstDispatcher = asyncDispatcher(upstreamClient, clock);
		ExternalAsyncDispatcherImpl secondDispatcher = asyncDispatcher(upstreamClient, clock);
		ExecutorService executor = fixedThreadExecutor(2, "postgres-async-dispatcher");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = List.of();

		try {
			futures = submitConcurrentTasks(executor, 2, ready, start,
					index -> index == 0 ? firstDispatcher.dispatchOnce() : secondDispatcher.dispatchOnce());
			assertThat(ready.await(5, TimeUnit.SECONDS))
					.as("оба async dispatcher должны быть готовы к конкурентному старту")
					.isTrue();
			start.countDown();

			try {
				assertThat(upstreamClient.awaitCallStarted())
						.as("ровно один dispatcher должен начать upstream-вызов")
						.isTrue();
			}
			finally {
				upstreamClient.allowResponse();
			}

			List<Boolean> dispatchResults = collectFutureResults(futures, FUTURE_TIMEOUT,
					"конкурентная обработка одной async-задачи");

			assertThat(dispatchResults).containsExactlyInAnyOrder(true, false);
			assertThat(upstreamClient.calls()).isEqualTo(1);
			assertThat(taskRepository.findByTaskId(task.taskId(), Optional.empty()).orElseThrow())
					.satisfies(storedTask -> {
						assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
						assertThat(storedTask.attempts()).isEqualTo(1);
						assertThat(storedTask.result()).containsEntry("decision", "APPROVED");
					});
		}
		finally {
			upstreamClient.allowResponse();
			cancelIfRunning(futures);
			executor.shutdownNow();
			firstDispatcher.shutdown();
			secondDispatcher.shutdown();
		}
	}

	@Test
	void concurrentCallbackDispatchersDoNotDeliverSamePostgresDeliveryTwice() throws Exception {
		Instant now = currentInstant();
		AsyncTask doneTask = createDoneCallbackTask(1602, now);
		CallbackDelivery delivery = deliveryRepository.createPending(doneTask, GatewayTestRequests.CALLBACK_URL,
				CALLBACK_MAX_ATTEMPTS, now.plusMillis(3));
		MutableTestClock clock = new MutableTestClock(now.plusMillis(4));
		BlockingCallbackClient callbackClient = new BlockingCallbackClient();
		CallbackDeliveryDispatcherImpl firstDispatcher = callbackDispatcher(callbackClient, clock);
		CallbackDeliveryDispatcherImpl secondDispatcher = callbackDispatcher(callbackClient, clock);
		ExecutorService executor = fixedThreadExecutor(2, "postgres-callback-dispatcher");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = List.of();

		try {
			futures = submitConcurrentTasks(executor, 2, ready, start,
					index -> index == 0 ? firstDispatcher.dispatchOnce() : secondDispatcher.dispatchOnce());
			assertThat(ready.await(5, TimeUnit.SECONDS))
					.as("оба callback dispatcher должны быть готовы к конкурентному старту")
					.isTrue();
			start.countDown();

			try {
				assertThat(callbackClient.awaitCallStarted())
						.as("ровно один dispatcher должен начать callback-доставку")
						.isTrue();
			}
			finally {
				callbackClient.allowResponse();
			}

			List<Boolean> dispatchResults = collectFutureResults(futures, FUTURE_TIMEOUT,
					"конкурентная доставка одного callback");

			assertThat(dispatchResults).containsExactlyInAnyOrder(true, false);
			assertThat(callbackClient.calls()).isEqualTo(1);
			assertThat(deliveryRepository.findByTaskId(doneTask.taskId()).orElseThrow())
					.satisfies(storedDelivery -> {
						assertThat(storedDelivery.deliveryId()).isEqualTo(delivery.deliveryId());
						assertThat(storedDelivery.status()).isEqualTo(CallbackDeliveryStatus.DELIVERED);
						assertThat(storedDelivery.attempt()).isEqualTo(1);
					});
			assertThat(taskRepository.findByTaskId(doneTask.taskId(), Optional.empty()).orElseThrow())
					.satisfies(storedTask -> {
						assertThat(storedTask.status()).isEqualTo(AsyncTaskStatus.DONE);
						assertThat(storedTask.callbackDeliveryStatus())
								.isEqualTo(CallbackDeliveryStatus.DELIVERED);
					});
		}
		finally {
			callbackClient.allowResponse();
			cancelIfRunning(futures);
			executor.shutdownNow();
			firstDispatcher.shutdown();
			secondDispatcher.shutdown();
		}
	}

	@Test
	void concurrentAsyncSubmitWithSameIdempotencyKeyCreatesSinglePostgresRow() throws Exception {
		Instant now = currentInstant();
		int contenders = 12;
		UUID externalId = GatewayTestRequests.externalId(1603);
		ExternalAsyncRequest request = new ExternalAsyncRequest(externalId, GatewayTestRequests.CLIENT_SERVICE,
				AsyncPriority.HIGH, AsyncDeliveryMode.CALLBACK, GatewayTestRequests.upstreamPayload());

		List<AsyncSubmitResult> results = runConcurrently(contenders, "postgres-idempotent-submit",
				index -> taskRepository.submit(request, ASYNC_MAX_ATTEMPTS, now.plusMillis(index)));

		assertThat(results).extracting(AsyncSubmitResult::type)
				.containsOnly(AsyncSubmitResultType.SUBMITTED);
		assertThat(results).filteredOn(result -> !result.alreadyExisted()).hasSize(1);
		assertThat(results).filteredOn(AsyncSubmitResult::alreadyExisted).hasSize(contenders - 1);
		assertThat(results).extracting(result -> result.task().taskId())
				.containsOnly(results.getFirst().task().taskId());
		assertThat(countAsyncRowsByIdempotencyKey(externalId)).isEqualTo(1);
	}

	@Test
	void waitingSyncAcquireKeepsReleasedSlotFromConcurrentAsyncLeaseAttempts() throws Exception {
		SlotManager slotManager = pollingSlotManager(Clock.systemUTC(), Duration.ofMillis(50));
		List<SlotLease> preoccupiedLeases = new ArrayList<>(acquireAllSyncSlots(slotManager));
		ExecutorService syncExecutor = fixedThreadExecutor(1, "postgres-sync-reserve");
		AtomicReference<SlotLease> waitingSyncLease = new AtomicReference<>();
		List<SlotLease> asyncLeases = new ArrayList<>();
		Future<Optional<SlotLease>> waitingSync = syncExecutor.submit(
				() -> slotManager.acquireSyncSlot("waiting-sync-reserve", Duration.ofSeconds(3)));

		try {
			awaitLiveSyncWaiters(1);

			SlotLease releasedLease = preoccupiedLeases.removeFirst();
			assertThat(slotManager.release(releasedLease.slotId(), releasedLease.leaseId())).isTrue();

			List<Optional<SlotLease>> asyncResults = runConcurrently(TOTAL_SLOTS * 2,
					"postgres-async-reserve", index -> slotRepository.acquireAsyncSlot(
							"async-reserve-" + index, "task-" + index, currentInstant()));
			asyncResults.stream()
					.flatMap(Optional::stream)
					.forEach(asyncLeases::add);

			Optional<SlotLease> syncResult = waitingSync.get(2, TimeUnit.SECONDS);
			assertThat(syncResult)
					.as("ожидающий sync acquire должен забрать освобожденный slot")
					.isPresent();
			waitingSyncLease.set(syncResult.orElseThrow());

			assertThat(asyncResults)
					.as("async lease не должен занять slot, пока есть sync waiter или восстановленный sync lease")
					.allSatisfy(result -> assertThat(result).isEmpty());
			assertThat(waitingSyncLease.get().slotId()).isEqualTo(releasedLease.slotId());
			assertThat(slotRepository.countBusySlots(SlotKind.SYNC)).isEqualTo(TOTAL_SLOTS);
			assertThat(slotRepository.countBusySlots(SlotKind.ASYNC)).isZero();
		}
		finally {
			cancelIfRunning(waitingSync);
			releaseIfPresent(slotManager, waitingSyncLease.get());
			asyncLeases.forEach(lease -> releaseIfPresent(slotManager, lease));
			preoccupiedLeases.forEach(lease -> releaseIfPresent(slotManager, lease));
			syncExecutor.shutdownNow();
		}
	}

	private ExternalAsyncDispatcherImpl asyncDispatcher(ExternalUpstreamClient upstreamClient, Clock clock) {
		return new ExternalAsyncDispatcherImpl(taskRepository, pollingSlotManager(clock, Duration.ofMillis(10)),
				upstreamClient, asyncProperties(), clock, Optional.empty());
	}

	private CallbackDeliveryDispatcherImpl callbackDispatcher(CallbackClient callbackClient, Clock clock) {
		return new CallbackDeliveryDispatcherImpl(deliveryRepository, taskRepository, callbackClient,
				callbackProperties(), clock);
	}

	private SlotManager pollingSlotManager(Clock clock, Duration pollInterval) {
		return new SlotManagerImpl(slotRepository, clock,
				new PollingSyncSlotWaitStrategy(duration ->
						TimeUnit.MILLISECONDS.sleep(Math.max(1, duration.toMillis()))),
				slotProperties(pollInterval));
	}

	private AsyncTask createDoneCallbackTask(int index, Instant now) {
		AsyncTask submitted = taskRepository.submit(asyncRequest(index, AsyncDeliveryMode.CALLBACK),
				ASYNC_MAX_ATTEMPTS, now).task();
		AsyncTaskClaim claim = taskRepository.executeInProcessingTransaction(
				() -> taskRepository.claimNextPending(now.plusMillis(1))).orElseThrow();
		assertThat(claim.task().taskId()).isEqualTo(submitted.taskId());
		return taskRepository.complete(claim.task().taskId(), GatewayTestRequests.upstreamResult(),
				now.plusMillis(2)).orElseThrow();
	}

	private List<SlotLease> acquireAllSyncSlots(SlotManager slotManager) {
		return IntStream.range(0, TOTAL_SLOTS)
				.mapToObj(index -> slotManager.acquireSyncSlot("preoccupied-sync-" + index, Duration.ZERO)
						.orElseThrow())
				.toList();
	}

	private void awaitLiveSyncWaiters(long expectedCount) {
		asyncAwaiter(Duration.ofSeconds(5)).untilAsserted("PostgreSQL зарегистрировал ожидающий sync acquire",
				() -> assertThat(liveSyncWaiters()).isEqualTo(expectedCount));
	}

	private long liveSyncWaiters() {
		return jdbcTemplate().queryForObject(
				"SELECT COUNT(*) FROM " + POSTGRES_SCHEMA + ".ext_sync_waiters", Long.class);
	}

	private long countAsyncRowsByIdempotencyKey(UUID externalId) {
		return jdbcTemplate().queryForObject("""
				SELECT COUNT(*)
				FROM %s.ext_request_queue
				WHERE client_service = ?
				  AND external_id = ?
				  AND delivery_mode IN ('CALLBACK', 'POLLING')
				""".formatted(POSTGRES_SCHEMA), Long.class, GatewayTestRequests.CLIENT_SERVICE, externalId);
	}

	private static ExternalAsyncRequest asyncRequest(int index, AsyncDeliveryMode deliveryMode) {
		return new ExternalAsyncRequest(GatewayTestRequests.externalId(index), GatewayTestRequests.CLIENT_SERVICE,
				AsyncPriority.HIGH, deliveryMode, GatewayTestRequests.upstreamPayload());
	}

	private static ExternalGatewayAsyncProperties asyncProperties() {
		return new ExternalGatewayAsyncProperties(Duration.ofMillis(100), ASYNC_MAX_ATTEMPTS, false, 2,
				RETRY_BACKOFF);
	}

	private static ExternalGatewayCallbackProperties callbackProperties() {
		return new ExternalGatewayCallbackProperties(false, CALLBACK_MAX_ATTEMPTS, 2, RETRY_BACKOFF,
				Duration.ofMillis(100), CALLBACK_TIMEOUT, Duration.ofSeconds(1));
	}

	private static ExternalGatewaySlotProperties slotProperties(Duration pollInterval) {
		return new ExternalGatewaySlotProperties(TOTAL_SLOTS, TARGET_FREE_SYNC_SLOTS, Duration.ofSeconds(30),
				Duration.ofSeconds(5), pollInterval, SyncAcquireWaitMode.POLLING);
	}

	private static Instant currentInstant() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}

	private static <T> List<T> runConcurrently(int contenders, String threadNamePrefix,
			IndexedCallable<T> action) throws Exception {
		ExecutorService executor = fixedThreadExecutor(contenders, threadNamePrefix);
		CountDownLatch ready = new CountDownLatch(contenders);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<T>> futures = List.of();
		try {
			futures = submitConcurrentTasks(executor, contenders, ready, start, action);
			assertThat(ready.await(5, TimeUnit.SECONDS))
					.as("все конкурентные задачи должны быть готовы к старту: " + threadNamePrefix)
					.isTrue();
			start.countDown();
			return collectFutureResults(futures, FUTURE_TIMEOUT, threadNamePrefix);
		}
		finally {
			cancelIfRunning(futures);
			executor.shutdownNow();
		}
	}

	private static <T> List<Future<T>> submitConcurrentTasks(ExecutorService executor, int contenders,
			CountDownLatch ready, CountDownLatch start, IndexedCallable<T> action) {
		return IntStream.range(0, contenders)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					if (!start.await(5, TimeUnit.SECONDS)) {
						throw new AssertionError("Конкурентный старт не был открыт за 5 секунд");
					}
					return action.call(index);
				}))
				.toList();
	}

	private static <T> List<T> collectFutureResults(List<? extends Future<T>> futures, Duration timeout,
			String description) throws Exception {
		List<T> results = new ArrayList<>(futures.size());
		long deadlineNanos = System.nanoTime() + timeout.toNanos();
		for (Future<T> future : futures) {
			long remainingNanos = deadlineNanos - System.nanoTime();
			assertThat(remainingNanos)
					.as(description + " должен завершиться до истечения timeout")
					.isPositive();
			results.add(future.get(remainingNanos, TimeUnit.NANOSECONDS));
		}
		return results;
	}

	private static ExecutorService fixedThreadExecutor(int threadCount, String threadNamePrefix) {
		AtomicInteger threadIndex = new AtomicInteger();
		return Executors.newFixedThreadPool(threadCount, runnable -> {
			Thread thread = new Thread(runnable, threadNamePrefix + "-" + threadIndex.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		});
	}

	private static void cancelIfRunning(List<? extends Future<?>> futures) {
		futures.forEach(PostgresConcurrencyIT::cancelIfRunning);
	}

	private static void cancelIfRunning(Future<?> future) {
		if (!future.isDone()) {
			future.cancel(true);
		}
	}

	private static void releaseIfPresent(SlotManager slotManager, SlotLease lease) {
		if (lease != null) {
			slotManager.release(lease.slotId(), lease.leaseId());
		}
	}

	@FunctionalInterface
	private interface IndexedCallable<T> {

		T call(int index) throws Exception;
	}

	private static final class BlockingUpstreamClient implements ExternalUpstreamClient {

		private final CountDownLatch callStarted = new CountDownLatch(1);
		private final CountDownLatch allowResponse = new CountDownLatch(1);
		private final AtomicInteger calls = new AtomicInteger();

		@Override
		public ExternalUpstreamResponse call(ExternalUpstreamRequest request) {
			calls.incrementAndGet();
			callStarted.countDown();
			awaitRelease(allowResponse, "upstream response");
			return new ExternalUpstreamResponse(GatewayTestRequests.upstreamResult(), 200, null);
		}

		private boolean awaitCallStarted() throws InterruptedException {
			return callStarted.await(5, TimeUnit.SECONDS);
		}

		private void allowResponse() {
			allowResponse.countDown();
		}

		private int calls() {
			return calls.get();
		}
	}

	private static final class BlockingCallbackClient implements CallbackClient {

		private final CountDownLatch callStarted = new CountDownLatch(1);
		private final CountDownLatch allowResponse = new CountDownLatch(1);
		private final AtomicInteger calls = new AtomicInteger();

		@Override
		public CallbackClientResponse send(CallbackPayload payload, URI url, int attempt, String requestId) {
			calls.incrementAndGet();
			callStarted.countDown();
			awaitRelease(allowResponse, "callback response");
			return new CallbackClientResponse(204);
		}

		private boolean awaitCallStarted() throws InterruptedException {
			return callStarted.await(5, TimeUnit.SECONDS);
		}

		private void allowResponse() {
			allowResponse.countDown();
		}

		private int calls() {
			return calls.get();
		}
	}

	private static void awaitRelease(CountDownLatch latch, String description) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Не дождались разрешения продолжить " + description);
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Ожидание " + description + " прервано", exception);
		}
	}
}
