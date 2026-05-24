package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.async.AsyncTaskRepository;
import com.example.testqwencli.gateway.callback.config.ExternalGatewayCallbackProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class CallbackDeliveryDispatcher {

	private static final Logger log = LoggerFactory.getLogger(CallbackDeliveryDispatcher.class);

	private final CallbackDeliveryRepository deliveryRepository;
	private final AsyncTaskRepository taskRepository;
	private final CallbackClient callbackClient;
	private final ExternalGatewayCallbackProperties properties;
	private final Clock clock;
	private final ExecutorService deliveryExecutor;

	public CallbackDeliveryDispatcher(
			CallbackDeliveryRepository deliveryRepository,
			AsyncTaskRepository taskRepository,
			CallbackClient callbackClient,
			ExternalGatewayCallbackProperties properties,
			Clock clock
	) {
		this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository must not be null");
		this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
		this.callbackClient = Objects.requireNonNull(callbackClient, "callbackClient must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
		this.deliveryExecutor = Executors.newFixedThreadPool(properties.deliveryBatchSize());
	}

	/**
	 * Выполняет одну доступную callback-доставку.
	 */
	public boolean dispatchOnce() {
		Optional<CallbackDelivery> claimedDelivery = deliveryRepository.claimNextPending(clock.instant());
		if (claimedDelivery.isEmpty()) {
			return false;
		}

		CallbackDelivery delivery = claimedDelivery.orElseThrow();
		taskRepository.updateCallbackDeliveryStatus(delivery.taskId(), delivery.status(), clock.instant());
		long startedNanos = System.nanoTime();
		try {
			CallbackClientResponse response = callbackClient.send(delivery.payload(), delivery.callbackUrl(),
					delivery.attempt(), delivery.payload().eventId().toString());
			long durationMs = elapsedMillis(startedNanos);
			if (response.successful()) {
				markDelivered(delivery, durationMs);
				return true;
			}
			markFailed(delivery, "Callback endpoint вернул HTTP " + response.statusCode(), durationMs);
			return true;
		}
		catch (RuntimeException exception) {
			markFailed(delivery, normalizeErrorMessage(exception), elapsedMillis(startedNanos));
			return true;
		}
	}

	public int dispatchBatch(int maxIterations) {
		int attempts = Math.min(Math.max(0, maxIterations), properties.deliveryBatchSize());
		if (attempts == 0) {
			return 0;
		}
		List<Future<Boolean>> futures = new ArrayList<>(attempts);
		for (int index = 0; index < attempts; index++) {
			futures.add(deliveryExecutor.submit(this::dispatchOnce));
		}
		return completedDeliveries(futures);
	}

	public int recoverTimedOutDeliveries() {
		Instant now = clock.instant();
		String message = "Callback-доставка зависла в DELIVERING дольше " + properties.deliveryTimeoutMs();
		List<CallbackDelivery> recovered = deliveryRepository.recoverTimedOutDeliveries(
				now.minus(properties.deliveryTimeoutMs()), message, properties.retryBackoffMs(), now);
		for (CallbackDelivery delivery : recovered) {
			taskRepository.updateCallbackDeliveryStatus(delivery.taskId(), delivery.status(), now);
			log.warn("Callback-доставка восстановлена после зависания: taskId={}, clientService={}, status={}, attempt={}, startedAt={}",
					delivery.taskId(), delivery.clientService(), delivery.status(), delivery.attempt(),
					delivery.startedAt());
		}
		return recovered.size();
	}

	private void markDelivered(CallbackDelivery delivery, long durationMs) {
		Instant now = clock.instant();
		deliveryRepository.markDelivered(delivery.deliveryId(), now)
				.ifPresent(updated -> {
					taskRepository.updateCallbackDeliveryStatus(updated.taskId(), updated.status(), now);
					log.info("Callback-доставка выполнена: taskId={}, clientService={}, attempt={}, durationMs={}",
							updated.taskId(), updated.clientService(), updated.attempt(), durationMs);
				});
	}

	private void markFailed(CallbackDelivery delivery, String message, long durationMs) {
		Instant now = clock.instant();
		deliveryRepository.markRetryOrDead(delivery.deliveryId(), message, properties.retryBackoffMs(), now)
				.ifPresent(updated -> {
					taskRepository.updateCallbackDeliveryStatus(updated.taskId(), updated.status(), now);
					log.warn("Callback-доставка завершилась ошибкой: taskId={}, clientService={}, status={}, durationMs={}, error={}",
							updated.taskId(), updated.clientService(), updated.status(), durationMs, updated.lastError());
				});
	}

	@PreDestroy
	void shutdown() {
		deliveryExecutor.shutdownNow();
	}

	private static int completedDeliveries(List<Future<Boolean>> futures) {
		int completed = 0;
		RuntimeException firstFailure = null;
		for (Future<Boolean> future : futures) {
			try {
				if (Boolean.TRUE.equals(future.get())) {
					completed++;
				}
			}
			catch (InterruptedException exception) {
				cancelRemaining(futures);
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Callback delivery batch interrupted", exception);
			}
			catch (ExecutionException exception) {
				if (firstFailure == null) {
					firstFailure = new IllegalStateException("Callback delivery batch failed", exception.getCause());
				}
			}
		}
		if (firstFailure != null) {
			throw firstFailure;
		}
		return completed;
	}

	private static void cancelRemaining(List<Future<Boolean>> futures) {
		for (Future<Boolean> future : futures) {
			future.cancel(true);
		}
	}

	private static long elapsedMillis(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	private static String normalizeErrorMessage(RuntimeException exception) {
		if (exception.getMessage() == null || exception.getMessage().isBlank()) {
			return exception.toString();
		}
		return exception.getMessage();
	}
}
