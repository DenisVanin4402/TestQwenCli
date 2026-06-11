package com.example.testqwencli.gateway.repository;

import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncPriority;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.callback.CallbackDelivery;
import com.example.testqwencli.gateway.model.callback.CallbackDeliveryRepositoryStats;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public interface CallbackDeliveryRepositoryContract {

	String CLIENT_SERVICE = "invest-pay";
	int ASYNC_MAX_ATTEMPTS = 3;
	int CALLBACK_MAX_ATTEMPTS = 2;
	Instant NOW = Instant.parse("2026-05-23T00:00:00Z");
	Duration BACKOFF = Duration.ofSeconds(30);

	CallbackDeliveryRepository repository();

	AsyncTaskRepository taskRepository();

	@Test
	default void createPendingStoresPayloadAndFindsDeliveryByTaskId() {
		AsyncTask task = doneTask(101, NOW);
		URI callbackUrl = callbackUrl(101);

		CallbackDelivery delivery = repository().createPending(task, callbackUrl, CALLBACK_MAX_ATTEMPTS,
				NOW.plusMillis(1));

		assertThat(delivery).satisfies(stored -> {
			assertThat(stored.taskId()).isEqualTo(task.taskId());
			assertThat(stored.clientService()).isEqualTo(CLIENT_SERVICE);
			assertThat(stored.callbackUrl()).isEqualTo(callbackUrl);
			assertThat(stored.status()).isEqualTo(CallbackDeliveryStatus.PENDING);
			assertThat(stored.attempt()).isZero();
			assertThat(stored.maxAttempts()).isEqualTo(CALLBACK_MAX_ATTEMPTS);
			assertThat(stored.createdAt()).isEqualTo(NOW.plusMillis(1));
			assertThat(stored.availableAt()).isEqualTo(NOW.plusMillis(1));
			assertThat(stored.startedAt()).isNull();
			assertThat(stored.completedAt()).isNull();
			assertThat(stored.lastError()).isNull();
			assertThat(stored.payload().eventId()).isNotNull();
			assertThat(stored.payload().externalId()).isEqualTo(task.externalId());
			assertThat(stored.payload().status()).isEqualTo(task.status());
			assertThat(stored.payload().result()).containsEntry("decision", "APPROVED");
		});
		assertThat(repository().findByTaskId(task.taskId())).contains(delivery);
		assertThat(repository().findByTaskId(task.taskId() + 1000)).isEmpty();
	}

	@Test
	default void createDeadStoresTerminalDeliveryWithoutCallbackUrl() {
		AsyncTask task = doneTask(102, NOW);

		CallbackDelivery delivery = repository().createDead(task, "Callback URL is not allow-listed",
				CALLBACK_MAX_ATTEMPTS, NOW.plusMillis(1));

		assertThat(delivery.status()).isEqualTo(CallbackDeliveryStatus.DEAD);
		assertThat(delivery.taskId()).isEqualTo(task.taskId());
		assertThat(delivery.callbackUrl()).isNull();
		assertThat(delivery.attempt()).isZero();
		assertThat(delivery.completedAt()).isEqualTo(NOW.plusMillis(1));
		assertThat(delivery.lastError()).isEqualTo("Callback URL is not allow-listed");
		assertThat(repository().claimNextPending(NOW.plusMillis(2))).isEmpty();
	}

	@Test
	default void createPendingUpsertsByTaskIdAndReplacesExistingDelivery() {
		AsyncTask task = doneTask(103, NOW);
		CallbackDelivery first = repository().createPending(task, callbackUrl(103), CALLBACK_MAX_ATTEMPTS,
				NOW.plusMillis(1));

		CallbackDelivery second = repository().createPending(task, callbackUrl(104), CALLBACK_MAX_ATTEMPTS + 1,
				NOW.plusMillis(2));

		assertThat(second.deliveryId()).isNotEqualTo(first.deliveryId());
		assertThat(second.callbackUrl()).isEqualTo(callbackUrl(104));
		assertThat(second.maxAttempts()).isEqualTo(CALLBACK_MAX_ATTEMPTS + 1);
		assertThat(second.createdAt()).isEqualTo(NOW.plusMillis(2));
		assertThat(repository().findByTaskId(task.taskId())).contains(second);
	}

	@Test
	default void claimNextPendingUsesAvailabilityAndRefreshesEventId() {
		AsyncTask futureTask = doneTask(104, NOW);
		AsyncTask readyTask = doneTask(105, NOW);
		repository().createPending(futureTask, callbackUrl(104), CALLBACK_MAX_ATTEMPTS, NOW.plusSeconds(10));
		CallbackDelivery ready = repository().createPending(readyTask, callbackUrl(105), CALLBACK_MAX_ATTEMPTS,
				NOW);

		CallbackDelivery claimed = repository().claimNextPending(NOW.plusMillis(1)).orElseThrow();

		assertThat(claimed.taskId()).isEqualTo(readyTask.taskId());
		assertThat(claimed.status()).isEqualTo(CallbackDeliveryStatus.DELIVERING);
		assertThat(claimed.attempt()).isEqualTo(1);
		assertThat(claimed.startedAt()).isEqualTo(NOW.plusMillis(1));
		assertThat(claimed.completedAt()).isNull();
		assertThat(claimed.payload().eventId()).isNotEqualTo(ready.payload().eventId());
		assertThat(repository().claimNextPending(NOW.plusMillis(2))).isEmpty();
		assertThat(repository().claimNextPending(NOW.plusSeconds(10)).orElseThrow().taskId())
				.isEqualTo(futureTask.taskId());
	}

	@Test
	default void claimNextPendingOrdersBacklogByAvailableAtThenCreatedAt() {
		AsyncTask laterTask = doneTask(106, NOW);
		AsyncTask earlierTask = doneTask(107, NOW);
		repository().createPending(laterTask, callbackUrl(106), CALLBACK_MAX_ATTEMPTS, NOW.plusMillis(2));
		repository().createPending(earlierTask, callbackUrl(107), CALLBACK_MAX_ATTEMPTS, NOW.plusMillis(1));

		CallbackDelivery first = repository().claimNextPending(NOW.plusMillis(2)).orElseThrow();
		CallbackDelivery second = repository().claimNextPending(NOW.plusMillis(3)).orElseThrow();

		assertThat(first.taskId()).isEqualTo(earlierTask.taskId());
		assertThat(second.taskId()).isEqualTo(laterTask.taskId());
	}

	@Test
	default void markDeliveredOnlyUpdatesDeliveringDelivery() {
		AsyncTask task = doneTask(108, NOW);
		CallbackDelivery delivery = repository().createPending(task, callbackUrl(108), CALLBACK_MAX_ATTEMPTS, NOW);

		assertThat(repository().markDelivered(delivery.deliveryId(), NOW.plusMillis(1))).isEmpty();

		CallbackDelivery claimed = repository().claimNextPending(NOW.plusMillis(1)).orElseThrow();
		CallbackDelivery delivered = repository().markDelivered(claimed.deliveryId(), NOW.plusMillis(2))
				.orElseThrow();

		assertThat(delivered.status()).isEqualTo(CallbackDeliveryStatus.DELIVERED);
		assertThat(delivered.completedAt()).isEqualTo(NOW.plusMillis(2));
		assertThat(delivered.lastError()).isNull();
		assertThat(repository().markRetryOrDead(delivered.deliveryId(), "should not retry", BACKOFF,
				NOW.plusMillis(3))).isEmpty();
		assertThat(repository().markDead(delivered.deliveryId(), "should not force dead", NOW.plusMillis(4)))
				.isEmpty();
	}

	@Test
	default void markRetryOrDeadSchedulesRetryUntilMaxAttempts() {
		AsyncTask task = doneTask(109, NOW);
		CallbackDelivery delivery = repository().createPending(task, callbackUrl(109), CALLBACK_MAX_ATTEMPTS, NOW);

		CallbackDelivery firstClaim = repository().claimNextPending(NOW.plusMillis(1)).orElseThrow();
		CallbackDelivery retry = repository().markRetryOrDead(firstClaim.deliveryId(), "HTTP 503", BACKOFF,
				NOW.plusMillis(2)).orElseThrow();

		assertThat(retry.status()).isEqualTo(CallbackDeliveryStatus.RETRY);
		assertThat(retry.attempt()).isEqualTo(1);
		assertThat(retry.availableAt()).isEqualTo(NOW.plusMillis(2).plus(BACKOFF));
		assertThat(retry.completedAt()).isNull();
		assertThat(retry.lastError()).isEqualTo("HTTP 503");
		assertThat(repository().claimNextPending(NOW.plusMillis(2).plusSeconds(29))).isEmpty();

		CallbackDelivery secondClaim = repository().claimNextPending(retry.availableAt()).orElseThrow();
		assertThat(secondClaim.attempt()).isEqualTo(2);

		CallbackDelivery dead = repository().markRetryOrDead(delivery.deliveryId(), "HTTP 503 again", BACKOFF,
				retry.availableAt().plusMillis(1)).orElseThrow();
		assertThat(dead.status()).isEqualTo(CallbackDeliveryStatus.DEAD);
		assertThat(dead.attempt()).isEqualTo(2);
		assertThat(dead.completedAt()).isEqualTo(retry.availableAt().plusMillis(1));
		assertThat(dead.lastError()).isEqualTo("HTTP 503 again");
	}

	@Test
	default void markDeadForcesNonDeliveredDelivery() {
		AsyncTask task = doneTask(110, NOW);
		CallbackDelivery delivery = repository().createPending(task, callbackUrl(110), CALLBACK_MAX_ATTEMPTS, NOW);

		CallbackDelivery dead = repository().markDead(delivery.deliveryId(), "Manual shutdown", NOW.plusMillis(1))
				.orElseThrow();

		assertThat(dead.status()).isEqualTo(CallbackDeliveryStatus.DEAD);
		assertThat(dead.completedAt()).isEqualTo(NOW.plusMillis(1));
		assertThat(dead.lastError()).isEqualTo("Manual shutdown");
		assertThat(repository().markDead(UUID.randomUUID(), "unknown", NOW.plusMillis(2))).isEmpty();
	}

	@Test
	default void recoverTimedOutDeliveriesMovesDeliveringRowsToRetryOrDead() {
		AsyncTask retryTask = doneTask(111, NOW);
		AsyncTask deadTask = doneTask(112, NOW);
		AsyncTask freshTask = doneTask(113, NOW);
		repository().createPending(retryTask, callbackUrl(111), CALLBACK_MAX_ATTEMPTS, NOW);
		repository().createPending(deadTask, callbackUrl(112), 1, NOW.plusMillis(1));
		repository().createPending(freshTask, callbackUrl(113), CALLBACK_MAX_ATTEMPTS, NOW.plusMillis(2));
		CallbackDelivery retryClaim = repository().claimNextPending(NOW.plusMillis(3)).orElseThrow();
		CallbackDelivery deadClaim = repository().claimNextPending(NOW.plusMillis(4)).orElseThrow();
		CallbackDelivery freshClaim = repository().claimNextPending(NOW.plusMillis(5)).orElseThrow();

		assertThat(repository().recoverTimedOutDeliveries(NOW.plusMillis(4), "timeout", BACKOFF,
				NOW.plusMillis(6)))
				.extracting(CallbackDelivery::taskId)
				.containsExactly(retryTask.taskId());

		assertThat(repository().findByTaskId(retryTask.taskId()).orElseThrow()).satisfies(recovered -> {
			assertThat(recovered.deliveryId()).isEqualTo(retryClaim.deliveryId());
			assertThat(recovered.status()).isEqualTo(CallbackDeliveryStatus.RETRY);
			assertThat(recovered.availableAt()).isEqualTo(NOW.plusMillis(6).plus(BACKOFF));
			assertThat(recovered.lastError()).isEqualTo("timeout");
		});
		assertThat(repository().findByTaskId(deadTask.taskId()).orElseThrow().status())
				.isEqualTo(CallbackDeliveryStatus.DELIVERING);
		assertThat(repository().findByTaskId(freshTask.taskId()).orElseThrow().deliveryId())
				.isEqualTo(freshClaim.deliveryId());

		assertThat(repository().recoverTimedOutDeliveries(NOW.plusMillis(5), "second timeout", BACKOFF,
				NOW.plusMillis(7)))
				.extracting(CallbackDelivery::taskId)
				.containsExactly(deadTask.taskId());
		assertThat(repository().findByTaskId(deadTask.taskId()).orElseThrow()).satisfies(recovered -> {
			assertThat(recovered.deliveryId()).isEqualTo(deadClaim.deliveryId());
			assertThat(recovered.status()).isEqualTo(CallbackDeliveryStatus.DEAD);
			assertThat(recovered.completedAt()).isEqualTo(NOW.plusMillis(7));
			assertThat(recovered.lastError()).isEqualTo("second timeout");
		});
		assertThat(repository().findByTaskId(freshTask.taskId()).orElseThrow().status())
				.isEqualTo(CallbackDeliveryStatus.DELIVERING);
	}

	@Test
	default void statsCountsDeliveriesAndOldestBacklogCreatedAt() {
		AsyncTask deliveringTask = doneTask(115, NOW);
		AsyncTask retryTask = doneTask(116, NOW);
		AsyncTask deadTask = doneTask(117, NOW);
		AsyncTask deliveredTask = doneTask(118, NOW);
		AsyncTask pendingTask = doneTask(114, NOW);
		repository().createPending(deliveringTask, callbackUrl(115), CALLBACK_MAX_ATTEMPTS, NOW);
		repository().createPending(retryTask, callbackUrl(116), CALLBACK_MAX_ATTEMPTS, NOW.plusMillis(2));
		repository().createPending(deadTask, callbackUrl(117), 1, NOW.plusMillis(3));
		repository().createPending(deliveredTask, callbackUrl(118), CALLBACK_MAX_ATTEMPTS, NOW.plusMillis(4));
		repository().createPending(pendingTask, callbackUrl(114), CALLBACK_MAX_ATTEMPTS, NOW.plusSeconds(60));

		CallbackDelivery delivering = repository().claimNextPending(NOW.plusMillis(5)).orElseThrow();
		CallbackDelivery retryClaim = repository().claimNextPending(NOW.plusMillis(6)).orElseThrow();
		CallbackDelivery deadClaim = repository().claimNextPending(NOW.plusMillis(7)).orElseThrow();
		CallbackDelivery deliveredClaim = repository().claimNextPending(NOW.plusMillis(8)).orElseThrow();
		assertThat(delivering.taskId()).isEqualTo(deliveringTask.taskId());
		assertThat(repository().markRetryOrDead(retryClaim.deliveryId(), "temporary error", BACKOFF,
				NOW.plusMillis(9))).isPresent();
		assertThat(repository().markRetryOrDead(deadClaim.deliveryId(), "final error", BACKOFF,
				NOW.plusMillis(10))).isPresent();
		assertThat(repository().markDelivered(deliveredClaim.deliveryId(), NOW.plusMillis(11))).isPresent();

		CallbackDeliveryRepositoryStats stats = repository().stats(NOW.plusMillis(12));

		assertThat(stats.count(CallbackDeliveryStatus.NOT_REQUIRED)).isZero();
		assertThat(stats.count(CallbackDeliveryStatus.PENDING)).isEqualTo(1);
		assertThat(stats.count(CallbackDeliveryStatus.DELIVERING)).isEqualTo(1);
		assertThat(stats.count(CallbackDeliveryStatus.RETRY)).isEqualTo(1);
		assertThat(stats.count(CallbackDeliveryStatus.DEAD)).isEqualTo(1);
		assertThat(stats.count(CallbackDeliveryStatus.DELIVERED)).isEqualTo(1);
		assertThat(stats.oldestBacklogCreatedAt()).isEqualTo(NOW);
	}

	private AsyncTask doneTask(int index, Instant baseTime) {
		AsyncTask submitted = taskRepository()
				.submit(request(index), ASYNC_MAX_ATTEMPTS, baseTime.plusMillis(index))
				.task();
		AsyncTaskClaim claim = taskRepository()
				.executeInProcessingTransaction(() -> taskRepository().claimNextPending(baseTime.plusMillis(index + 1)))
				.orElseThrow();
		assertThat(claim.task().taskId()).isEqualTo(submitted.taskId());
		return taskRepository()
				.complete(claim.task().taskId(), upstreamResult(), baseTime.plusMillis(index + 2))
				.orElseThrow();
	}

	private static ExternalAsyncRequest request(int index) {
		return new ExternalAsyncRequest(externalId(index), CLIENT_SERVICE, AsyncPriority.HIGH,
				AsyncDeliveryMode.CALLBACK, payload());
	}

	private static UUID externalId(int index) {
		return UUID.nameUUIDFromBytes(("callback-delivery-contract-" + index).getBytes(StandardCharsets.UTF_8));
	}

	private static URI callbackUrl(int index) {
		return URI.create("https://callback.example.test/tasks/" + index);
	}

	private static Map<String, Object> payload() {
		LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
		payload.put("operation", "calculate");
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
}
