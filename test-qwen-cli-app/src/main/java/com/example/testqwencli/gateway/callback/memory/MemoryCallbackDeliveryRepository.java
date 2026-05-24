package com.example.testqwencli.gateway.callback.memory;

import com.example.testqwencli.gateway.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.async.AsyncTask;
import com.example.testqwencli.gateway.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.async.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.callback.CallbackDelivery;
import com.example.testqwencli.gateway.callback.CallbackDeliveryRepository;
import com.example.testqwencli.gateway.callback.CallbackDeliveryRepositoryStats;
import com.example.testqwencli.gateway.callback.CallbackPayload;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MemoryCallbackDeliveryRepository implements CallbackDeliveryRepository {

	private static final Comparator<StoredCallbackDelivery> DELIVERY_ORDER = Comparator
			.comparing(StoredCallbackDelivery::availableAt)
			.thenComparing(StoredCallbackDelivery::createdAt)
			.thenComparing(delivery -> delivery.deliveryId().toString());

	private final Map<UUID, StoredCallbackDelivery> deliveriesById = new LinkedHashMap<>();
	private final Map<Long, UUID> deliveryIdsByTaskId = new LinkedHashMap<>();

	@Override
	public synchronized CallbackDelivery createPending(AsyncTask task, URI callbackUrl, int maxAttempts,
			Instant now) {
		Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
		validateFinalCallbackTask(task);
		validateMaxAttempts(maxAttempts);
		Objects.requireNonNull(now, "now must not be null");
		return put(StoredCallbackDelivery.pending(task, callbackUrl, maxAttempts, now)).toDelivery();
	}

	@Override
	public synchronized CallbackDelivery createDead(AsyncTask task, String message, int maxAttempts, Instant now) {
		validateFinalCallbackTask(task);
		validateMaxAttempts(maxAttempts);
		Objects.requireNonNull(now, "now must not be null");
		return put(StoredCallbackDelivery.dead(task, normalizeErrorMessage(message), maxAttempts, now)).toDelivery();
	}

	@Override
	public synchronized Optional<CallbackDelivery> findByTaskId(long taskId) {
		UUID deliveryId = deliveryIdsByTaskId.get(taskId);
		if (deliveryId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(deliveriesById.get(deliveryId)).map(StoredCallbackDelivery::toDelivery);
	}

	@Override
	public synchronized Optional<CallbackDelivery> claimNextPending(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		Optional<StoredCallbackDelivery> candidate = deliveriesById.values()
				.stream()
				.filter(delivery -> delivery.status() == CallbackDeliveryStatus.PENDING
						|| delivery.status() == CallbackDeliveryStatus.RETRY)
				.filter(delivery -> !delivery.availableAt().isAfter(now))
				.min(DELIVERY_ORDER);
		if (candidate.isEmpty()) {
			return Optional.empty();
		}
		StoredCallbackDelivery claimed = candidate.orElseThrow().claim(now);
		deliveriesById.put(claimed.deliveryId(), claimed);
		return Optional.of(claimed.toDelivery());
	}

	@Override
	public synchronized Optional<CallbackDelivery> markDelivered(UUID deliveryId, Instant now) {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(now, "now must not be null");
		StoredCallbackDelivery delivery = deliveriesById.get(deliveryId);
		if (delivery == null || delivery.status() != CallbackDeliveryStatus.DELIVERING) {
			return Optional.empty();
		}
		StoredCallbackDelivery delivered = delivery.delivered(now);
		deliveriesById.put(deliveryId, delivered);
		return Optional.of(delivered.toDelivery());
	}

	@Override
	public synchronized Optional<CallbackDelivery> markRetryOrDead(UUID deliveryId, String message, Duration backoff,
			Instant now) {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(backoff, "backoff must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (backoff.isNegative()) {
			throw new IllegalArgumentException("Backoff retry callback-доставки не должен быть отрицательным");
		}
		StoredCallbackDelivery delivery = deliveriesById.get(deliveryId);
		if (delivery == null || delivery.status() != CallbackDeliveryStatus.DELIVERING) {
			return Optional.empty();
		}
		StoredCallbackDelivery failed = delivery.retryOrDead(normalizeErrorMessage(message), backoff, now);
		deliveriesById.put(deliveryId, failed);
		return Optional.of(failed.toDelivery());
	}

	@Override
	public synchronized Optional<CallbackDelivery> markDead(UUID deliveryId, String message, Instant now) {
		Objects.requireNonNull(deliveryId, "deliveryId must not be null");
		Objects.requireNonNull(now, "now must not be null");
		StoredCallbackDelivery delivery = deliveriesById.get(deliveryId);
		if (delivery == null || delivery.status() == CallbackDeliveryStatus.DELIVERED) {
			return Optional.empty();
		}
		StoredCallbackDelivery dead = delivery.dead(normalizeErrorMessage(message), now);
		deliveriesById.put(deliveryId, dead);
		return Optional.of(dead.toDelivery());
	}

	@Override
	public synchronized List<CallbackDelivery> recoverTimedOutDeliveries(Instant timedOutBefore, String message,
			Duration backoff, Instant now) {
		Objects.requireNonNull(timedOutBefore, "timedOutBefore must not be null");
		Objects.requireNonNull(backoff, "backoff must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (backoff.isNegative()) {
			throw new IllegalArgumentException("Backoff retry callback-доставки не должен быть отрицательным");
		}
		List<CallbackDelivery> recovered = new ArrayList<>();
		for (StoredCallbackDelivery delivery : deliveriesById.values()) {
			if (delivery.status() == CallbackDeliveryStatus.DELIVERING
					&& delivery.startedAt() != null
					&& delivery.startedAt().isBefore(timedOutBefore)) {
				StoredCallbackDelivery updated = delivery.retryOrDead(normalizeErrorMessage(message), backoff, now);
				deliveriesById.put(updated.deliveryId(), updated);
				recovered.add(updated.toDelivery());
			}
		}
		return recovered;
	}

	@Override
	public synchronized CallbackDeliveryRepositoryStats stats(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		EnumMap<CallbackDeliveryStatus, Long> statusCounts = new EnumMap<>(CallbackDeliveryStatus.class);
		for (CallbackDeliveryStatus status : CallbackDeliveryStatus.values()) {
			statusCounts.put(status, 0L);
		}
		Instant oldestBacklogCreatedAt = null;
		for (StoredCallbackDelivery delivery : deliveriesById.values()) {
			statusCounts.compute(delivery.status(), (status, count) -> count == null ? 1L : count + 1);
			if (isBacklog(delivery.status()) && (oldestBacklogCreatedAt == null
					|| delivery.createdAt().isBefore(oldestBacklogCreatedAt))) {
				oldestBacklogCreatedAt = delivery.createdAt();
			}
		}
		return new CallbackDeliveryRepositoryStats(statusCounts, oldestBacklogCreatedAt);
	}

	private StoredCallbackDelivery put(StoredCallbackDelivery delivery) {
		UUID existingDeliveryId = deliveryIdsByTaskId.put(delivery.payload().taskId(), delivery.deliveryId());
		if (existingDeliveryId != null) {
			deliveriesById.remove(existingDeliveryId);
		}
		deliveriesById.put(delivery.deliveryId(), delivery);
		return delivery;
	}

	private static void validateFinalCallbackTask(AsyncTask task) {
		Objects.requireNonNull(task, "task must not be null");
		if (task.deliveryMode() != AsyncDeliveryMode.CALLBACK) {
			throw new IllegalArgumentException("Callback delivery создается только для deliveryMode=CALLBACK");
		}
		if (!isFinal(task.status())) {
			throw new IllegalArgumentException("Callback delivery создается только для финальной async-задачи");
		}
	}

	private static void validateMaxAttempts(int maxAttempts) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
	}

	private static boolean isFinal(AsyncTaskStatus status) {
		return status == AsyncTaskStatus.DONE
				|| status == AsyncTaskStatus.FAILED
				|| status == AsyncTaskStatus.DEAD
				|| status == AsyncTaskStatus.CANCELLED;
	}

	private static String normalizeErrorMessage(String message) {
		if (message == null || message.isBlank()) {
			return "Callback-доставка завершилась ошибкой";
		}
		return message;
	}

	private static boolean isBacklog(CallbackDeliveryStatus status) {
		return status == CallbackDeliveryStatus.PENDING
				|| status == CallbackDeliveryStatus.DELIVERING
				|| status == CallbackDeliveryStatus.RETRY;
	}

	private record StoredCallbackDelivery(
			UUID deliveryId,
			CallbackPayload payload,
			URI callbackUrl,
			CallbackDeliveryStatus status,
			int attempt,
			int maxAttempts,
			Instant createdAt,
			Instant availableAt,
			Instant startedAt,
			Instant completedAt,
			String lastError
	) {

		private static StoredCallbackDelivery pending(AsyncTask task, URI callbackUrl, int maxAttempts, Instant now) {
			return new StoredCallbackDelivery(UUID.randomUUID(),
					CallbackPayload.fromTask(UUID.randomUUID(), task), callbackUrl, CallbackDeliveryStatus.PENDING,
					0, maxAttempts, now, now, null, null, null);
		}

		private static StoredCallbackDelivery dead(AsyncTask task, String message, int maxAttempts, Instant now) {
			return new StoredCallbackDelivery(UUID.randomUUID(),
					CallbackPayload.fromTask(UUID.randomUUID(), task), null, CallbackDeliveryStatus.DEAD, 0,
					maxAttempts, now, now, null, now, message);
		}

		private StoredCallbackDelivery claim(Instant now) {
			return new StoredCallbackDelivery(deliveryId, payload.withEventId(UUID.randomUUID()), callbackUrl,
					CallbackDeliveryStatus.DELIVERING, attempt + 1, maxAttempts, createdAt, availableAt, now, null,
					lastError);
		}

		private StoredCallbackDelivery delivered(Instant now) {
			return new StoredCallbackDelivery(deliveryId, payload, callbackUrl, CallbackDeliveryStatus.DELIVERED,
					attempt, maxAttempts, createdAt, availableAt, startedAt, now, null);
		}

		private StoredCallbackDelivery retryOrDead(String message, Duration backoff, Instant now) {
			if (attempt >= maxAttempts) {
				return dead(message, now);
			}
			return new StoredCallbackDelivery(deliveryId, payload, callbackUrl, CallbackDeliveryStatus.RETRY,
					attempt, maxAttempts, createdAt, now.plus(backoff), startedAt, null, message);
		}

		private StoredCallbackDelivery dead(String message, Instant now) {
			return new StoredCallbackDelivery(deliveryId, payload, callbackUrl, CallbackDeliveryStatus.DEAD, attempt,
					maxAttempts, createdAt, availableAt, startedAt, now, message);
		}

		private CallbackDelivery toDelivery() {
			return new CallbackDelivery(deliveryId, payload, callbackUrl, status, attempt, maxAttempts, createdAt,
					availableAt, startedAt, completedAt, lastError);
		}
	}
}
