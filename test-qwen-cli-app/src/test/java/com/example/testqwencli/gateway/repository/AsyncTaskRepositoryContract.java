package com.example.testqwencli.gateway.repository;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResult;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.model.async.AsyncTaskRepositoryStats;
import com.example.testqwencli.gateway.model.async.AsyncTaskUpdateResult;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.SyncRequestTrace;
import com.example.testqwencli.gateway.model.async.TaskError;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncPriority;
import com.example.testqwencli.gateway.model.async.enums.AsyncSubmitResultType;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskUpdateStatus;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public interface AsyncTaskRepositoryContract {

	String CLIENT_SERVICE = "invest-pay";
	String OTHER_CLIENT_SERVICE = "user-expertise";
	int MAX_ATTEMPTS = 3;
	Instant NOW = Instant.parse("2026-05-22T00:00:00Z");

	AsyncTaskRepository repository();

	@Test
	default void submitCreatesPendingTaskAndFindsItByTaskAndExternalId() {
		ExternalAsyncRequest request = request(101, AsyncPriority.HIGH, AsyncDeliveryMode.CALLBACK);

		AsyncSubmitResult result = repository().submit(request, MAX_ATTEMPTS, NOW);

		assertThat(result.type()).isEqualTo(AsyncSubmitResultType.SUBMITTED);
		assertThat(result.alreadyExisted()).isFalse();
		assertThat(result.conflictingFields()).isEmpty();
		assertThat(result.task()).satisfies(task -> {
			assertThat(task.externalId()).isEqualTo(request.externalId());
			assertThat(task.clientService()).isEqualTo(CLIENT_SERVICE);
			assertThat(task.priority()).isEqualTo(AsyncPriority.HIGH);
			assertThat(task.deliveryMode()).isEqualTo(AsyncDeliveryMode.CALLBACK);
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.PENDING);
			assertThat(task.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.PENDING);
			assertThat(task.result()).isNull();
			assertThat(task.error()).isNull();
			assertThat(task.attempts()).isZero();
			assertThat(task.maxAttempts()).isEqualTo(MAX_ATTEMPTS);
			assertThat(task.createdAt()).isEqualTo(NOW);
			assertThat(task.availableAt()).isEqualTo(NOW);
			assertThat(task.startedAt()).isNull();
			assertThat(task.finishedAt()).isNull();
			assertThat(task.lastError()).isNull();
			assertThat(task.retryable()).isFalse();
		});
		assertThat(repository().findByTaskId(result.task().taskId(), Optional.of(CLIENT_SERVICE)))
				.contains(result.task());
		assertThat(repository().findByTaskId(result.task().taskId(), Optional.of(OTHER_CLIENT_SERVICE))).isEmpty();
		assertThat(repository().findByExternalId(request.externalId(), Optional.of(CLIENT_SERVICE)))
				.contains(result.task());
	}

	@Test
	default void repeatedSubmitWithSameIdempotencyKeyReturnsExistingTask() {
		ExternalAsyncRequest request = request(102, AsyncPriority.HIGH, AsyncDeliveryMode.POLLING);
		AsyncSubmitResult first = repository().submit(request, MAX_ATTEMPTS, NOW);

		AsyncSubmitResult second = repository().submit(request, MAX_ATTEMPTS, NOW.plusMillis(1));

		assertThat(second.type()).isEqualTo(AsyncSubmitResultType.SUBMITTED);
		assertThat(second.alreadyExisted()).isTrue();
		assertThat(second.existingTaskId()).isEqualTo(first.task().taskId());
		assertThat(second.task()).isEqualTo(first.task());
	}

	@Test
	default void submitWithSameIdempotencyKeyAndDifferentBodyReturnsConflict() {
		UUID externalId = externalId(103);
		ExternalAsyncRequest original = request(externalId, CLIENT_SERVICE, AsyncPriority.HIGH,
				AsyncDeliveryMode.CALLBACK, payload("calculate"));
		ExternalAsyncRequest conflicting = request(externalId, CLIENT_SERVICE, AsyncPriority.LOW,
				AsyncDeliveryMode.POLLING, payload("approve"));
		AsyncSubmitResult first = repository().submit(original, MAX_ATTEMPTS, NOW);

		AsyncSubmitResult result = repository().submit(conflicting, MAX_ATTEMPTS, NOW.plusMillis(1));

		assertThat(result.type()).isEqualTo(AsyncSubmitResultType.IDEMPOTENCY_CONFLICT);
		assertThat(result.alreadyExisted()).isTrue();
		assertThat(result.task()).isNull();
		assertThat(result.existingTaskId()).isEqualTo(first.task().taskId());
		assertThat(result.conflictingFields()).containsExactly("payload", "priority", "deliveryMode");
	}

	@Test
	default void claimNextPendingReturnsStoredPayloadAndDoesNotClaimInProgressTaskAgain() {
		ExternalAsyncRequest request = request(104, AsyncPriority.HIGH, AsyncDeliveryMode.POLLING);
		AsyncTask submitted = repository().submit(request, MAX_ATTEMPTS, NOW).task();

		AsyncTaskClaim claim = claimNextPending(NOW.plusMillis(1)).orElseThrow();

		assertThat(claim.payload()).containsEntry("operation", "calculate");
		assertThat(claim.payload()).containsEntry("amount", 1000);
		assertThat(claim.task()).satisfies(task -> {
			assertThat(task.taskId()).isEqualTo(submitted.taskId());
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.IN_PROGRESS);
			assertThat(task.attempts()).isEqualTo(1);
			assertThat(task.startedAt()).isEqualTo(NOW.plusMillis(1));
			assertThat(task.result()).isNull();
			assertThat(task.error()).isNull();
		});
		assertThat(claimNextPending(NOW.plusSeconds(60))).isEmpty();
		assertThat(repository().findByTaskId(submitted.taskId(), Optional.empty()).orElseThrow().attempts())
				.isEqualTo(1);
	}

	@Test
	default void claimNextPendingSelectsHighPriorityBeforeLowPriority() {
		repository().submit(request(105, AsyncPriority.LOW, AsyncDeliveryMode.POLLING), MAX_ATTEMPTS, NOW);
		repository().submit(request(106, AsyncPriority.HIGH, AsyncDeliveryMode.POLLING), MAX_ATTEMPTS,
				NOW.plusMillis(1));

		AsyncTaskClaim first = claimNextPending(NOW.plusMillis(2)).orElseThrow();
		AsyncTaskClaim second = claimNextPending(NOW.plusMillis(3)).orElseThrow();

		assertThat(first.task().priority()).isEqualTo(AsyncPriority.HIGH);
		assertThat(second.task().priority()).isEqualTo(AsyncPriority.LOW);
	}

	@Test
	default void completeOnlyUpdatesClaimedTask() {
		AsyncTask submitted = repository()
				.submit(request(107, AsyncPriority.HIGH, AsyncDeliveryMode.POLLING), MAX_ATTEMPTS, NOW)
				.task();

		assertThat(repository().complete(submitted.taskId(), upstreamResult(), NOW.plusMillis(1))).isEmpty();

		AsyncTaskClaim claim = claimNextPending(NOW.plusMillis(1)).orElseThrow();
		AsyncTask completed = repository()
				.complete(claim.task().taskId(), upstreamResult(), NOW.plusMillis(2))
				.orElseThrow();

		assertThat(completed.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(completed.result()).containsEntry("decision", "APPROVED");
		assertThat(completed.result()).containsEntry("score", "82");
		assertThat(completed.error()).isNull();
		assertThat(completed.attempts()).isEqualTo(1);
		assertThat(completed.finishedAt()).isEqualTo(NOW.plusMillis(2));
		assertThat(completed.lastError()).isNull();
		assertThat(completed.retryable()).isFalse();
	}

	@Test
	default void failTransientRetriesUntilMaxAttemptsAndManualRetryReturnsTaskToQueue() {
		AsyncTask submitted = repository()
				.submit(request(108, AsyncPriority.HIGH, AsyncDeliveryMode.POLLING), 2, NOW)
				.task();

		AsyncTaskClaim firstClaim = claimNextPending(NOW.plusMillis(1)).orElseThrow();
		assertThat(firstClaim.task().taskId()).isEqualTo(submitted.taskId());

		AsyncTask pendingRetry = repository()
				.failTransient(submitted.taskId(), "Временная ошибка upstream", Duration.ofSeconds(30),
						NOW.plusMillis(2))
				.orElseThrow();
		assertThat(pendingRetry.status()).isEqualTo(AsyncTaskStatus.PENDING);
		assertThat(pendingRetry.attempts()).isEqualTo(1);
		assertThat(pendingRetry.availableAt()).isEqualTo(NOW.plusMillis(2).plusSeconds(30));
		assertThat(pendingRetry.startedAt()).isNull();
		assertThat(pendingRetry.finishedAt()).isNull();
		assertThat(pendingRetry.lastError()).isEqualTo("Временная ошибка upstream");
		assertThat(pendingRetry.error()).isNull();
		assertThat(pendingRetry.retryable()).isFalse();
		assertThat(claimNextPending(NOW.plusSeconds(29))).isEmpty();

		AsyncTaskClaim retryClaim = claimNextPending(pendingRetry.availableAt()).orElseThrow();
		assertThat(retryClaim.task().attempts()).isEqualTo(2);

		AsyncTask dead = repository()
				.failTransient(submitted.taskId(), "Повторная временная ошибка", Duration.ofSeconds(30),
						pendingRetry.availableAt().plusMillis(1))
				.orElseThrow();
		assertThat(dead.status()).isEqualTo(AsyncTaskStatus.DEAD);
		assertThat(dead.error()).isEqualTo(new TaskError("UPSTREAM_TRANSIENT_FAILURE",
				"Повторная временная ошибка", true));
		assertThat(dead.lastError()).isEqualTo("Повторная временная ошибка");
		assertThat(dead.retryable()).isTrue();
		assertThat(dead.finishedAt()).isEqualTo(pendingRetry.availableAt().plusMillis(1));

		AsyncTaskUpdateResult retried = repository().retry(submitted.taskId(), Optional.of(CLIENT_SERVICE),
				pendingRetry.availableAt().plusMillis(2));
		assertThat(retried.status()).isEqualTo(AsyncTaskUpdateStatus.UPDATED);
		assertThat(retried.task()).satisfies(task -> {
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.PENDING);
			assertThat(task.attempts()).isZero();
			assertThat(task.availableAt()).isEqualTo(pendingRetry.availableAt().plusMillis(2));
			assertThat(task.startedAt()).isNull();
			assertThat(task.finishedAt()).isNull();
			assertThat(task.lastError()).isNull();
			assertThat(task.retryable()).isFalse();
		});
	}

	@Test
	default void returnClaimToPendingCompensatesAttemptIncrement() {
		AsyncTask submitted = repository()
				.submit(request(109, AsyncPriority.HIGH, AsyncDeliveryMode.CALLBACK), MAX_ATTEMPTS, NOW)
				.task();
		AsyncTaskClaim claim = claimNextPending(NOW.plusMillis(1)).orElseThrow();

		AsyncTask returned = repository()
				.returnClaimToPending(claim.task().taskId(), NOW.plusMillis(2))
				.orElseThrow();

		assertThat(returned.status()).isEqualTo(AsyncTaskStatus.PENDING);
		assertThat(returned.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.PENDING);
		assertThat(returned.attempts()).isZero();
		assertThat(returned.availableAt()).isEqualTo(NOW.plusMillis(2));
		assertThat(returned.startedAt()).isNull();
		assertThat(returned.finishedAt()).isNull();
		assertThat(repository().returnClaimToPending(submitted.taskId() + 1000, NOW.plusMillis(2))).isEmpty();

		AsyncTaskClaim claimedAgain = claimNextPending(NOW.plusMillis(2)).orElseThrow();
		assertThat(claimedAgain.task().attempts()).isEqualTo(1);
	}

	@Test
	default void cancelPendingTaskIsIdempotentAndHonorsClientServiceFilter() {
		AsyncTask submitted = repository()
				.submit(request(110, AsyncPriority.HIGH, AsyncDeliveryMode.CALLBACK), MAX_ATTEMPTS, NOW)
				.task();

		assertThat(repository().cancel(submitted.taskId(), Optional.of(OTHER_CLIENT_SERVICE), NOW.plusMillis(1))
				.status()).isEqualTo(AsyncTaskUpdateStatus.NOT_FOUND);

		AsyncTaskUpdateResult cancelled = repository().cancel(submitted.taskId(), Optional.of(CLIENT_SERVICE),
				NOW.plusMillis(2));
		assertThat(cancelled.status()).isEqualTo(AsyncTaskUpdateStatus.UPDATED);
		assertThat(cancelled.task()).satisfies(task -> {
			assertThat(task.status()).isEqualTo(AsyncTaskStatus.CANCELLED);
			assertThat(task.error()).isEqualTo(new TaskError("TASK_CANCELLED",
					"Задача отменена до начала выполнения", false));
			assertThat(task.finishedAt()).isEqualTo(NOW.plusMillis(2));
			assertThat(task.retryable()).isFalse();
		});

		AsyncTaskUpdateResult secondCancel = repository().cancel(submitted.taskId(), Optional.of(CLIENT_SERVICE),
				NOW.plusMillis(3));
		assertThat(secondCancel.status()).isEqualTo(AsyncTaskUpdateStatus.UPDATED);
		assertThat(secondCancel.task().status()).isEqualTo(AsyncTaskStatus.CANCELLED);
		assertThat(repository().retry(submitted.taskId(), Optional.of(CLIENT_SERVICE), NOW.plusMillis(4)).status())
				.isEqualTo(AsyncTaskUpdateStatus.CONFLICT);
	}

	@Test
	default void callbackDeliveryStatusCanBeUpdatedOnStoredTask() {
		AsyncTask submitted = repository()
				.submit(request(111, AsyncPriority.HIGH, AsyncDeliveryMode.CALLBACK), MAX_ATTEMPTS, NOW)
				.task();

		AsyncTask updated = repository()
				.updateCallbackDeliveryStatus(submitted.taskId(), CallbackDeliveryStatus.DELIVERING,
						NOW.plusMillis(1))
				.orElseThrow();

		assertThat(updated.callbackDeliveryStatus()).isEqualTo(CallbackDeliveryStatus.DELIVERING);
		assertThat(repository().updateCallbackDeliveryStatus(submitted.taskId() + 1000,
				CallbackDeliveryStatus.DELIVERING, NOW.plusMillis(1))).isEmpty();
	}

	@Test
	default void syncTraceAllowsRepeatedExternalIdAndDoesNotAffectAsyncStats() {
		UUID externalId = externalId(112);
		AsyncTask asyncTask = repository()
				.submit(request(externalId, CLIENT_SERVICE, AsyncPriority.HIGH, AsyncDeliveryMode.POLLING,
						payload("async")), MAX_ATTEMPTS, NOW)
				.task();
		AsyncTask doneTrace = repository().recordSyncTrace(new SyncRequestTrace(externalId, CLIENT_SERVICE,
				payload("sync-done"), AsyncTaskStatus.DONE, upstreamResultObjectMap(), null, 1, NOW.plusMillis(1),
				NOW.plusMillis(2), null));
		TaskError timeout = new TaskError("UPSTREAM_TIMEOUT", "Upstream не ответил вовремя", true);
		AsyncTask failedTrace = repository().recordSyncTrace(new SyncRequestTrace(externalId, CLIENT_SERVICE,
				payload("sync-failed"), AsyncTaskStatus.FAILED, null, timeout, 1, NOW.plusMillis(3),
				NOW.plusMillis(4), timeout.message()));

		List<AsyncTask> traces = repository().findRequestTracesByExternalId(externalId, Optional.of(CLIENT_SERVICE));
		AsyncTaskRepositoryStats stats = repository().stats(NOW.plusMillis(5));

		assertThat(traces).hasSize(3);
		assertThat(traces).extracting(AsyncTask::taskId)
				.containsExactly(asyncTask.taskId(), doneTrace.taskId(), failedTrace.taskId());
		assertThat(traces).extracting(AsyncTask::deliveryMode)
				.containsExactly(AsyncDeliveryMode.POLLING, AsyncDeliveryMode.SYNC, AsyncDeliveryMode.SYNC);
		assertThat(repository().findByExternalId(externalId, Optional.of(CLIENT_SERVICE))).contains(asyncTask);
		assertThat(stats.count(AsyncTaskStatus.PENDING)).isEqualTo(1);
		assertThat(stats.count(AsyncTaskStatus.DONE)).isZero();
		assertThat(stats.count(AsyncTaskStatus.FAILED)).isZero();
		assertThat(stats.oldestActiveCreatedAt()).isEqualTo(asyncTask.createdAt());
	}

	private Optional<AsyncTaskClaim> claimNextPending(Instant now) {
		return repository().executeInProcessingTransaction(() -> repository().claimNextPending(now));
	}

	private static ExternalAsyncRequest request(int index, AsyncPriority priority,
			AsyncDeliveryMode deliveryMode) {
		return request(externalId(index), CLIENT_SERVICE, priority, deliveryMode, payload("calculate"));
	}

	private static ExternalAsyncRequest request(UUID externalId, String clientService, AsyncPriority priority,
			AsyncDeliveryMode deliveryMode, Map<String, Object> payload) {
		return new ExternalAsyncRequest(externalId, clientService, priority, deliveryMode, payload);
	}

	private static UUID externalId(int index) {
		return UUID.nameUUIDFromBytes(("async-task-contract-" + index).getBytes(StandardCharsets.UTF_8));
	}

	private static Map<String, Object> payload(String operation) {
		LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
		payload.put("operation", operation);
		payload.put("amount", 1000);
		payload.put("currency", "RUB");
		return payload;
	}

	private static Map<String, String> upstreamResult() {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		result.put("decision", "APPROVED");
		result.put("score", "82");
		return result;
	}

	private static Map<String, Object> upstreamResultObjectMap() {
		LinkedHashMap<String, Object> result = new LinkedHashMap<>();
		upstreamResult().forEach(result::put);
		return result;
	}
}
