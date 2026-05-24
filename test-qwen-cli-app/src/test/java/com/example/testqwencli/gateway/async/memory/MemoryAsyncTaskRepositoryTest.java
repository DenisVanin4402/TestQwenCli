package com.example.testqwencli.gateway.async.memory;

import com.example.testqwencli.gateway.async.AsyncDeliveryMode;
import com.example.testqwencli.gateway.async.AsyncPriority;
import com.example.testqwencli.gateway.async.AsyncTaskClaim;
import com.example.testqwencli.gateway.async.ExternalAsyncRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAsyncTaskRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");

	private final MemoryAsyncTaskRepository repository = new MemoryAsyncTaskRepository();

	@Test
	void claimNextPendingSelectsHighPriorityBeforeLowPriority() {
		repository.submit(request("3a4f5ba3-b502-42e6-b2eb-6d38d0cc5aa1", AsyncPriority.LOW), 3, NOW);
		repository.submit(request("531e8f69-2333-4f49-865a-89f1d136a022", AsyncPriority.HIGH), 3, NOW);

		AsyncTaskClaim claim = repository.claimNextPending(NOW).orElseThrow();

		assertThat(claim.task().priority()).isEqualTo(AsyncPriority.HIGH);
		assertThat(claim.task().status().name()).isEqualTo("IN_PROGRESS");
		assertThat(claim.task().attempts()).isEqualTo(1);
	}

	@Test
	void claimNextPendingDoesNotSelectFreshInProgressTaskAgain() {
		repository.submit(request("639820fa-435e-4e65-b0c6-a665cdde211f", AsyncPriority.HIGH), 3, NOW);
		AsyncTaskClaim firstClaim = repository.claimNextPending(NOW).orElseThrow();

		Optional<AsyncTaskClaim> secondClaim = repository.claimNextPending(NOW.plusSeconds(60));

		assertThat(secondClaim).isEmpty();
		assertThat(repository.findByTaskId(firstClaim.task().taskId(), Optional.empty()).orElseThrow().attempts())
				.isEqualTo(1);
	}

	private static ExternalAsyncRequest request(String externalId, AsyncPriority priority) {
		return new ExternalAsyncRequest(UUID.fromString(externalId), "invest-pay", priority,
				AsyncDeliveryMode.CALLBACK, Map.of("operation", "calculate"));
	}
}
