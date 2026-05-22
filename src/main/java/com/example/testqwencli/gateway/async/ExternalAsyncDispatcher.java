package com.example.testqwencli.gateway.async;

import com.example.testqwencli.gateway.async.config.ExternalGatewayAsyncProperties;
import com.example.testqwencli.gateway.callback.CallbackDeliveryPlanner;
import com.example.testqwencli.gateway.slot.SlotLease;
import com.example.testqwencli.gateway.slot.SlotManager;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamClient;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamRequest;
import com.example.testqwencli.gateway.sync.upstream.ExternalUpstreamResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

@Service
public class ExternalAsyncDispatcher {

	private static final Logger log = LoggerFactory.getLogger(ExternalAsyncDispatcher.class);

	private final AsyncTaskRepository repository;
	private final SlotManager slotManager;
	private final ExternalUpstreamClient upstreamClient;
	private final ExternalGatewayAsyncProperties properties;
	private final Clock clock;
	private final Optional<CallbackDeliveryPlanner> callbackDeliveryPlanner;

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
	}

	/**
	 * Выполняет одну доступную async-задачу, если очередь и лимит слотов позволяют старт.
	 *
	 * @return {@code true}, если задача была обработана или завершилась управляемой ошибкой
	 */
	public boolean dispatchOnce() {
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

	private boolean dispatchClaim(AsyncTaskClaim claim, SlotLease slotLease) {
		AsyncTask task = claim.task();
		try {
			ExternalUpstreamResponse response = upstreamClient.call(toUpstreamRequest(claim));
			repository.complete(task.taskId(), response.result(), clock.instant())
					.ifPresent(this::planCallbackDelivery);
			log.info("Async-задача завершена: taskId={}, clientService={}", task.taskId(), task.clientService());
			return true;
		}
		catch (RuntimeException exception) {
			repository.failTransient(task.taskId(), exception.getMessage(), properties.retryBackoffMs(),
					clock.instant())
					.filter(updatedTask -> updatedTask.status() == AsyncTaskStatus.DEAD)
					.ifPresent(this::planCallbackDelivery);
			log.warn("Async-задача завершилась transient-ошибкой: taskId={}, clientService={}, error={}",
					task.taskId(), task.clientService(), exception.toString());
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

	private static String owner(AsyncTask task) {
		return "async:" + task.clientService() + ":" + task.taskId();
	}
}
