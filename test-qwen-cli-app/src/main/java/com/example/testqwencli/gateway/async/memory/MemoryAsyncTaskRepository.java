package com.example.testqwencli.gateway.async.memory;

import com.example.testqwencli.gateway.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.async.AsyncPayloads;
import com.example.testqwencli.gateway.async.AsyncPriority;
import com.example.testqwencli.gateway.async.AsyncSubmitResult;
import com.example.testqwencli.gateway.async.AsyncTask;
import com.example.testqwencli.gateway.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.async.AsyncTaskRepository;
import com.example.testqwencli.gateway.async.AsyncTaskRepositoryStats;
import com.example.testqwencli.gateway.async.AsyncTaskStatus;
import com.example.testqwencli.gateway.async.AsyncTaskUpdateResult;
import com.example.testqwencli.gateway.async.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.async.TaskError;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MemoryAsyncTaskRepository implements AsyncTaskRepository {

	private static final Comparator<StoredAsyncTask> DISPATCH_ORDER = Comparator
			.comparingInt(StoredAsyncTask::priorityWeight)
			.reversed()
			.thenComparing(StoredAsyncTask::availableAt)
			.thenComparingLong(StoredAsyncTask::taskId);

	private final Map<Long, StoredAsyncTask> tasksById = new LinkedHashMap<>();
	private final Map<AsyncTaskKey, Long> taskIdsByKey = new LinkedHashMap<>();
	private long nextTaskId = 1;

	@Override
	public synchronized AsyncSubmitResult submit(ExternalAsyncRequest request, int maxAttempts, Instant now) {
		Objects.requireNonNull(request, "request must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}

		AsyncTaskKey key = new AsyncTaskKey(request.clientService(), request.externalId());
		Long existingTaskId = taskIdsByKey.get(key);
		if (existingTaskId != null) {
			StoredAsyncTask existingTask = tasksById.get(existingTaskId);
			ArrayList<String> conflictingFields = existingTask.conflictingFields(request);
			if (!conflictingFields.isEmpty()) {
				return AsyncSubmitResult.idempotencyConflict(existingTask.taskId(), conflictingFields);
			}
			return AsyncSubmitResult.submitted(existingTask.toTask(), true);
		}

		StoredAsyncTask task = StoredAsyncTask.pending(nextTaskId++, request, maxAttempts, now);
		tasksById.put(task.taskId(), task);
		taskIdsByKey.put(key, task.taskId());
		return AsyncSubmitResult.submitted(task.toTask(), false);
	}

	@Override
	public synchronized Optional<AsyncTask> findByTaskId(long taskId, Optional<String> clientService) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		return findStoredByTaskId(taskId, clientService).map(StoredAsyncTask::toTask);
	}

	@Override
	public synchronized Optional<AsyncTask> findByExternalId(UUID externalId, Optional<String> clientService) {
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		if (clientService.isPresent()) {
			Long taskId = taskIdsByKey.get(new AsyncTaskKey(clientService.orElseThrow(), externalId));
			return Optional.ofNullable(taskId)
					.map(tasksById::get)
					.map(StoredAsyncTask::toTask);
		}
		return tasksById.values()
				.stream()
				.filter(task -> task.externalId().equals(externalId))
				.findFirst()
				.map(StoredAsyncTask::toTask);
	}

	@Override
	public synchronized AsyncTaskUpdateResult cancel(long taskId, Optional<String> clientService, Instant now) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(now, "now must not be null");
		Optional<StoredAsyncTask> existing = findStoredByTaskId(taskId, clientService);
		if (existing.isEmpty()) {
			return AsyncTaskUpdateResult.notFound();
		}

		StoredAsyncTask task = existing.orElseThrow();
		if (task.status() == AsyncTaskStatus.CANCELLED) {
			return AsyncTaskUpdateResult.updated(task.toTask());
		}
		if (task.status() != AsyncTaskStatus.PENDING) {
			return AsyncTaskUpdateResult.conflict(task.toTask(),
					"Async-задачу нельзя отменить в статусе " + task.status());
		}

		StoredAsyncTask cancelled = task.cancel(now);
		tasksById.put(taskId, cancelled);
		return AsyncTaskUpdateResult.updated(cancelled.toTask());
	}

	@Override
	public synchronized AsyncTaskUpdateResult retry(long taskId, Optional<String> clientService, Instant now) {
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(now, "now must not be null");
		Optional<StoredAsyncTask> existing = findStoredByTaskId(taskId, clientService);
		if (existing.isEmpty()) {
			return AsyncTaskUpdateResult.notFound();
		}

		StoredAsyncTask task = existing.orElseThrow();
		if ((task.status() == AsyncTaskStatus.FAILED || task.status() == AsyncTaskStatus.DEAD) && task.retryable()) {
			StoredAsyncTask retried = task.retry(now);
			tasksById.put(taskId, retried);
			return AsyncTaskUpdateResult.updated(retried.toTask());
		}

		return AsyncTaskUpdateResult.conflict(task.toTask(),
				"Async-задачу нельзя вернуть в очередь в статусе " + task.status());
	}

	@Override
	public synchronized Optional<AsyncTaskClaim> claimNextPending(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		Optional<StoredAsyncTask> candidate = tasksById.values()
				.stream()
				.filter(task -> task.status() == AsyncTaskStatus.PENDING)
				.filter(task -> !task.availableAt().isAfter(now))
				.min(DISPATCH_ORDER);
		if (candidate.isEmpty()) {
			return Optional.empty();
		}

		StoredAsyncTask task = candidate.orElseThrow();
		StoredAsyncTask claimed = task.claim(now);
		tasksById.put(claimed.taskId(), claimed);
		return Optional.of(claimed.toClaim());
	}

	@Override
	public synchronized Optional<AsyncTask> complete(long taskId, Map<String, String> result, Instant now) {
		Objects.requireNonNull(result, "result must not be null");
		Objects.requireNonNull(now, "now must not be null");
		StoredAsyncTask task = tasksById.get(taskId);
		if (task == null || task.status() != AsyncTaskStatus.IN_PROGRESS) {
			return Optional.empty();
		}

		StoredAsyncTask completed = task.complete(result, now);
		tasksById.put(taskId, completed);
		return Optional.of(completed.toTask());
	}

	@Override
	public synchronized Optional<AsyncTask> failTransient(long taskId, String message, Duration backoff,
			Instant now) {
		Objects.requireNonNull(backoff, "backoff must not be null");
		Objects.requireNonNull(now, "now must not be null");
		if (backoff.isNegative()) {
			throw new IllegalArgumentException("Backoff не должен быть отрицательным");
		}

		StoredAsyncTask task = tasksById.get(taskId);
		if (task == null || task.status() != AsyncTaskStatus.IN_PROGRESS) {
			return Optional.empty();
		}

		StoredAsyncTask failed = task.failTransient(normalizeErrorMessage(message), backoff, now);
		tasksById.put(taskId, failed);
		return Optional.of(failed.toTask());
	}

	@Override
	public synchronized Optional<AsyncTask> returnClaimToPending(long taskId, Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		StoredAsyncTask task = tasksById.get(taskId);
		if (task == null || task.status() != AsyncTaskStatus.IN_PROGRESS) {
			return Optional.empty();
		}

		StoredAsyncTask pending = task.returnClaim(now);
		tasksById.put(taskId, pending);
		return Optional.of(pending.toTask());
	}

	@Override
	public synchronized Optional<AsyncTask> updateCallbackDeliveryStatus(long taskId, CallbackDeliveryStatus status,
			Instant now) {
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(now, "now must not be null");
		StoredAsyncTask task = tasksById.get(taskId);
		if (task == null) {
			return Optional.empty();
		}
		StoredAsyncTask updated = task.withCallbackDeliveryStatus(status);
		tasksById.put(taskId, updated);
		return Optional.of(updated.toTask());
	}

	@Override
	public synchronized AsyncTaskRepositoryStats stats(Instant now) {
		Objects.requireNonNull(now, "now must not be null");
		EnumMap<AsyncTaskStatus, Long> statusCounts = new EnumMap<>(AsyncTaskStatus.class);
		for (AsyncTaskStatus status : AsyncTaskStatus.values()) {
			statusCounts.put(status, 0L);
		}
		long retryCount = 0;
		Instant oldestActiveCreatedAt = null;
		for (StoredAsyncTask task : tasksById.values()) {
			statusCounts.compute(task.status(), (status, count) -> count == null ? 1L : count + 1);
			if (task.status() == AsyncTaskStatus.PENDING && task.attempts() > 0) {
				retryCount++;
			}
			if (isActive(task.status()) && (oldestActiveCreatedAt == null
					|| task.createdAt().isBefore(oldestActiveCreatedAt))) {
				oldestActiveCreatedAt = task.createdAt();
			}
		}
		return new AsyncTaskRepositoryStats(statusCounts, retryCount, oldestActiveCreatedAt);
	}

	private Optional<StoredAsyncTask> findStoredByTaskId(long taskId, Optional<String> clientService) {
		StoredAsyncTask task = tasksById.get(taskId);
		if (task == null) {
			return Optional.empty();
		}
		if (clientService.isPresent() && !task.clientService().equals(clientService.orElseThrow())) {
			return Optional.empty();
		}
		return Optional.of(task);
	}

	private record AsyncTaskKey(String clientService, UUID externalId) {

		private AsyncTaskKey {
			Objects.requireNonNull(clientService, "clientService must not be null");
			Objects.requireNonNull(externalId, "externalId must not be null");
		}
	}

	private record StoredAsyncTask(
			long taskId,
			UUID externalId,
			String clientService,
			AsyncPriority priority,
			int priorityWeight,
			AsyncDeliveryMode deliveryMode,
			AsyncTaskStatus status,
			CallbackDeliveryStatus callbackDeliveryStatus,
			Map<String, Object> payload,
			Map<String, Object> result,
			TaskError error,
			int attempts,
			int maxAttempts,
			Instant createdAt,
			Instant availableAt,
			Instant startedAt,
			Instant finishedAt,
			String lastError,
			boolean retryable
	) {

		private StoredAsyncTask {
			payload = AsyncPayloads.copyMap(payload);
			if (result != null) {
				result = AsyncPayloads.copyMap(result);
			}
		}

		private static StoredAsyncTask pending(long taskId, ExternalAsyncRequest request, int maxAttempts,
				Instant now) {
			return new StoredAsyncTask(taskId, request.externalId(), request.clientService(), request.priority(),
					request.priority().weight(), request.deliveryMode(), AsyncTaskStatus.PENDING,
					callbackStatus(request.deliveryMode()), request.payload(), null, null, 0, maxAttempts, now, now,
					null, null, null, false);
		}

		private ArrayList<String> conflictingFields(ExternalAsyncRequest request) {
			ArrayList<String> fields = new ArrayList<>();
			if (!Objects.equals(payload, request.payload())) {
				fields.add("payload");
			}
			if (priority != request.priority()) {
				fields.add("priority");
			}
			if (deliveryMode != request.deliveryMode()) {
				fields.add("deliveryMode");
			}
			return fields;
		}

		private StoredAsyncTask cancel(Instant now) {
			TaskError cancelError = new TaskError("TASK_CANCELLED", "Задача отменена до начала выполнения", false);
			return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
					AsyncTaskStatus.CANCELLED, callbackDeliveryStatus, payload, null, cancelError, attempts,
					maxAttempts, createdAt, availableAt, startedAt, now, cancelError.message(), false);
		}

		private StoredAsyncTask retry(Instant now) {
			return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
					AsyncTaskStatus.PENDING, callbackStatus(deliveryMode), payload, null, null, 0, maxAttempts,
					createdAt, now, null, null, null, false);
		}

		private StoredAsyncTask claim(Instant now) {
			return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
					AsyncTaskStatus.IN_PROGRESS, callbackDeliveryStatus, payload, null, null, attempts + 1,
					maxAttempts, createdAt, availableAt, now, null, lastError, false);
		}

		private StoredAsyncTask complete(Map<String, String> result, Instant now) {
			return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
					AsyncTaskStatus.DONE, callbackDeliveryStatus, payload, new LinkedHashMap<>(result), null,
					attempts, maxAttempts, createdAt, availableAt, startedAt, now, null, false);
		}

		private StoredAsyncTask failTransient(String message, Duration backoff, Instant now) {
			if (attempts < maxAttempts) {
				return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
						AsyncTaskStatus.PENDING, callbackStatus(deliveryMode), payload, null, null, attempts,
						maxAttempts, createdAt, now.plus(backoff), null, null, message, false);
			}

			TaskError taskError = new TaskError("UPSTREAM_TRANSIENT_FAILURE", message, true);
			return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
					AsyncTaskStatus.DEAD, callbackDeliveryStatus, payload, null, taskError, attempts, maxAttempts,
					createdAt, availableAt, startedAt, now, message, true);
		}

		private StoredAsyncTask returnClaim(Instant now) {
			return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
					AsyncTaskStatus.PENDING, callbackStatus(deliveryMode), payload, null, null,
					Math.max(0, attempts - 1), maxAttempts, createdAt, now, null, null, lastError, false);
		}

		private StoredAsyncTask withCallbackDeliveryStatus(CallbackDeliveryStatus status) {
			return new StoredAsyncTask(taskId, externalId, clientService, priority, priorityWeight, deliveryMode,
					this.status, status, payload, result, error, attempts, maxAttempts, createdAt, availableAt,
					startedAt, finishedAt, lastError, retryable);
		}

		private AsyncTask toTask() {
			return new AsyncTask(taskId, externalId, clientService, priority, deliveryMode, status,
					callbackDeliveryStatus, result, error, attempts, maxAttempts, createdAt, availableAt, startedAt,
					finishedAt, lastError, retryable);
		}

		private AsyncTaskClaim toClaim() {
			return new AsyncTaskClaim(toTask(), payload);
		}

		private static CallbackDeliveryStatus callbackStatus(AsyncDeliveryMode deliveryMode) {
			if (deliveryMode == AsyncDeliveryMode.POLLING) {
				return CallbackDeliveryStatus.NOT_REQUIRED;
			}
			return CallbackDeliveryStatus.PENDING;
		}
	}

	private static String normalizeErrorMessage(String message) {
		if (message == null || message.isBlank()) {
			return "Временная ошибка upstream";
		}
		return message;
	}

	private static boolean isActive(AsyncTaskStatus status) {
		return status == AsyncTaskStatus.PENDING || status == AsyncTaskStatus.IN_PROGRESS;
	}
}
