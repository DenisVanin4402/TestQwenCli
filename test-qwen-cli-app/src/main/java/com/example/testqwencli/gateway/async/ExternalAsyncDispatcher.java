package com.example.testqwencli.gateway.async;

import com.example.testqwencli.gateway.async.config.ExternalGatewayAsyncProperties;
import com.example.testqwencli.gateway.callback.CallbackDeliveryPlanner;
import com.example.testqwencli.gateway.slot.SlotLease;
import com.example.testqwencli.gateway.slot.SlotManager;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamClient;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
public class ExternalAsyncDispatcher {

	private static final Logger log = LoggerFactory.getLogger(ExternalAsyncDispatcher.class);

	private final AsyncTaskRepository repository;
	private final SlotManager slotManager;
	private final ExternalUpstreamClient upstreamClient;
	private final ExternalGatewayAsyncProperties properties;
	private final Clock clock;
	private final Optional<CallbackDeliveryPlanner> callbackDeliveryPlanner;
	private final ExecutorService dispatchExecutor;
	private final Semaphore dispatchPermits;

	public ExternalAsyncDispatcher(
			AsyncTaskRepository repository,
			SlotManager slotManager,
			ExternalUpstreamClient upstreamClient,
			ExternalGatewayAsyncProperties properties,
			Clock clock,
			Optional<CallbackDeliveryPlanner> callbackDeliveryPlanner
	) {
		this.repository = Objects.requireNonNull(repository, "repository must not be null");
		this.slotManager = Objects.requireNonNull(slotManager, "slotManager must not be null");
		this.upstreamClient = Objects.requireNonNull(upstreamClient, "upstreamClient must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
		this.callbackDeliveryPlanner = Objects.requireNonNull(callbackDeliveryPlanner,
				"callbackDeliveryPlanner must not be null");
		this.dispatchExecutor = Executors.newFixedThreadPool(properties.dispatchBatchSize());
		this.dispatchPermits = new Semaphore(properties.dispatchBatchSize());
	}

	public boolean dispatchOnce() {
		return repository.executeInProcessingTransaction(this::dispatchOnceInTransaction);
	}

	private boolean dispatchOnceInTransaction() {
		Optional<AsyncTaskClaim> claimedTask = repository.claimNextPending(clock.instant());
		if (claimedTask.isEmpty()) {
			return false;
		}

		AsyncTaskClaim claim = claimedTask.orElseThrow();
		AsyncTask task = claim.task();
		Optional<SlotLease> lease = slotManager.tryAcquireAsyncSlot(owner(task), Long.toString(task.taskId()));
		if (lease.isEmpty()) {
			repository.returnClaimToPending(task.taskId(), clock.instant());
			log.debug("Async-задача возвращена в очередь без запуска: taskId={}", task.taskId());
			return false;
		}

		return dispatchClaim(claim, lease.orElseThrow());
	}

	public int dispatchBatch(int maxIterations) {
		int attempts = Math.max(0, maxIterations);
		if (attempts == 0) {
			return 0;
		}
		int startedWorkers = 0;
		for (int index = 0; index < attempts; index++) {
			if (!dispatchPermits.tryAcquire()) {
				break;
			}
			try {
				dispatchExecutor.submit(this::dispatchUntilIdle);
				startedWorkers++;
			}
			catch (RuntimeException exception) {
				dispatchPermits.release();
				throw exception;
			}
		}
		return startedWorkers;
	}

	private void dispatchUntilIdle() {
		try {
			while (dispatchOnce()) {
				// Воркер сразу берет следующую задачу, чтобы не ждать следующего scheduled tick.
			}
		}
		catch (RuntimeException exception) {
			log.warn("Async-dispatch worker завершился с ошибкой", exception);
		}
		finally {
			dispatchPermits.release();
		}
	}

	private boolean dispatchClaim(AsyncTaskClaim claim, SlotLease slotLease) {
		AsyncTask task = claim.task();
		long startedNanos = System.nanoTime();
		try {
			ExternalUpstreamResponse response = upstreamClient.call(toUpstreamRequest(claim));
			long durationMs = elapsedMillis(startedNanos);
			repository.complete(task.taskId(), response.result(), clock.instant())
					.ifPresent(this::planCallbackDelivery);
			log.info("Async-задача завершена: taskId={}, clientService={}, durationMs={}",
					task.taskId(), task.clientService(), durationMs);
			return true;
		}
		catch (RuntimeException exception) {
			long durationMs = elapsedMillis(startedNanos);
			repository.failTransient(task.taskId(), exception.getMessage(), properties.retryBackoffMs(),
					clock.instant())
					.filter(updatedTask -> updatedTask.status() == AsyncTaskStatus.DEAD)
					.ifPresent(this::planCallbackDelivery);
			log.warn("Async-задача завершилась transient-ошибкой: taskId={}, clientService={}, durationMs={}, error={}",
					task.taskId(), task.clientService(), durationMs, exception.toString());
			log.debug("Детали transient-ошибки async-задачи: taskId={}", task.taskId(), exception);
			return true;
		}
		finally {
			releaseSlot(slotLease);
		}
	}

	private ExternalUpstreamRequest toUpstreamRequest(AsyncTaskClaim claim) {
		AsyncTask task = claim.task();
		return new ExternalUpstreamRequest(task.externalId(), task.clientService(), claim.payload(),
				"async-task-" + task.taskId(), task.clientService() + ":" + task.externalId());
	}

	private void planCallbackDelivery(AsyncTask task) {
		callbackDeliveryPlanner.ifPresent(planner -> planner.planForFinalTask(task));
	}

	private void releaseSlot(SlotLease lease) {
		boolean released = slotManager.release(lease.slotId(), lease.leaseId());
		if (!released) {
			log.warn("Не удалось освободить async-слот: slotId={}, leaseId={}", lease.slotId(), lease.leaseId());
		}
	}

	@PreDestroy
	void shutdown() {
		dispatchExecutor.shutdownNow();
	}

	private static long elapsedMillis(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	private static String owner(AsyncTask task) {
		return "async:" + task.clientService() + ":" + task.taskId();
	}
}
