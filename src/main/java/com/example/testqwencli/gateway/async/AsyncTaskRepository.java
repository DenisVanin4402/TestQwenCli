package com.example.testqwencli.gateway.async;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Порт очереди async-задач external gateway.
 */
public interface AsyncTaskRepository {

	AsyncSubmitResult submit(ExternalAsyncRequest request, int maxAttempts, Instant now);

	Optional<AsyncTask> findByTaskId(long taskId, Optional<String> clientService);

	Optional<AsyncTask> findByExternalId(UUID externalId, Optional<String> clientService);

	AsyncTaskUpdateResult cancel(long taskId, Optional<String> clientService, Instant now);

	AsyncTaskUpdateResult retry(long taskId, Optional<String> clientService, Instant now);

	Optional<AsyncTaskClaim> claimNextPending(Instant now);

	Optional<AsyncTask> complete(long taskId, Map<String, String> result, Instant now);

	Optional<AsyncTask> failTransient(long taskId, String message, Duration backoff, Instant now);

	Optional<AsyncTask> returnClaimToPending(long taskId, Instant now);

	Optional<AsyncTask> updateCallbackDeliveryStatus(long taskId, CallbackDeliveryStatus status, Instant now);
}
