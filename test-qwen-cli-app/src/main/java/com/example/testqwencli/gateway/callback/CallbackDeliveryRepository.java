package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.async.AsyncTask;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CallbackDeliveryRepository {

	CallbackDelivery createPending(AsyncTask task, URI callbackUrl, int maxAttempts, Instant now);

	CallbackDelivery createDead(AsyncTask task, String message, int maxAttempts, Instant now);

	Optional<CallbackDelivery> findByTaskId(long taskId);

	Optional<CallbackDelivery> claimNextPending(Instant now);

	Optional<CallbackDelivery> markDelivered(UUID deliveryId, Instant now);

	Optional<CallbackDelivery> markRetryOrDead(UUID deliveryId, String message, Duration backoff, Instant now);

	Optional<CallbackDelivery> markDead(UUID deliveryId, String message, Instant now);

	CallbackDeliveryRepositoryStats stats(Instant now);
}
