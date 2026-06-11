package com.example.testqwencli.gateway.support;

import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncPriority;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.async.enums.CallbackDeliveryStatus;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import com.example.testqwencli.gateway.model.sync.ExternalSyncHeaders;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class GatewayTestRequests {

	public static final String CLIENT_SERVICE = "invest-pay";
	public static final String OTHER_CLIENT_SERVICE = "user-expertise";
	public static final URI CALLBACK_URL = URI.create("http://invest-pay/internal/external-gateway/callbacks");
	public static final Instant DEFAULT_NOW = Instant.parse("2026-05-22T00:00:00Z");

	private GatewayTestRequests() {
	}

	public static UUID externalId(int index) {
		return UUID.nameUUIDFromBytes(("external-gateway-test-" + index).getBytes(StandardCharsets.UTF_8));
	}

	public static ExternalSyncHeaders syncHeaders() {
		return new ExternalSyncHeaders("test-request-1", "test-idempotency-1");
	}

	public static ExternalSyncRequest syncRequest() {
		return syncRequest(externalId(1));
	}

	public static ExternalSyncRequest syncRequest(UUID externalId) {
		return syncRequest(externalId, CLIENT_SERVICE);
	}

	public static ExternalSyncRequest syncRequest(UUID externalId, String clientService) {
		return new ExternalSyncRequest(externalId, clientService, upstreamPayload());
	}

	public static ExternalAsyncRequest asyncPollingRequest() {
		return asyncPollingRequest(externalId(2));
	}

	public static ExternalAsyncRequest asyncPollingRequest(UUID externalId) {
		return asyncRequest(externalId, CLIENT_SERVICE, AsyncPriority.HIGH, AsyncDeliveryMode.POLLING);
	}

	public static ExternalAsyncRequest asyncCallbackRequest() {
		return asyncCallbackRequest(externalId(3));
	}

	public static ExternalAsyncRequest asyncCallbackRequest(UUID externalId) {
		return asyncRequest(externalId, CLIENT_SERVICE, AsyncPriority.HIGH, AsyncDeliveryMode.CALLBACK);
	}

	public static ExternalAsyncRequest asyncRequest(UUID externalId, String clientService, AsyncPriority priority,
			AsyncDeliveryMode deliveryMode) {
		return new ExternalAsyncRequest(externalId, clientService, priority, deliveryMode, upstreamPayload());
	}

	public static Map<String, Object> upstreamPayload() {
		LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
		payload.put("operation", "calculate");
		payload.put("amount", 1000);
		payload.put("currency", "RUB");
		return payload;
	}

	public static Map<String, String> upstreamResult() {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		result.put("decision", "APPROVED");
		result.put("score", "82");
		return result;
	}

	public static AsyncTask doneCallbackTask(long taskId) {
		return doneCallbackTask(taskId, externalId(4), DEFAULT_NOW);
	}

	public static AsyncTask doneCallbackTask(long taskId, UUID externalId, Instant finishedAt) {
		return doneTask(taskId, externalId, CLIENT_SERVICE, AsyncDeliveryMode.CALLBACK,
				CallbackDeliveryStatus.PENDING, finishedAt);
	}

	public static AsyncTask donePollingTask(long taskId, UUID externalId, Instant finishedAt) {
		return doneTask(taskId, externalId, CLIENT_SERVICE, AsyncDeliveryMode.POLLING,
				CallbackDeliveryStatus.NOT_REQUIRED, finishedAt);
	}

	public static CallbackPayload doneCallbackPayload(long taskId) {
		return CallbackPayload.fromTask(callbackEventId(taskId), doneCallbackTask(taskId));
	}

	private static AsyncTask doneTask(long taskId, UUID externalId, String clientService,
			AsyncDeliveryMode deliveryMode, CallbackDeliveryStatus callbackDeliveryStatus, Instant finishedAt) {
		Instant startedAt = finishedAt.minusMillis(250);
		LinkedHashMap<String, Object> result = new LinkedHashMap<>();
		upstreamResult().forEach(result::put);
		return new AsyncTask(
				taskId,
				externalId,
				clientService,
				AsyncPriority.HIGH,
				deliveryMode,
				AsyncTaskStatus.DONE,
				callbackDeliveryStatus,
				result,
				null,
				1,
				3,
				startedAt.minusSeconds(1),
				startedAt.minusSeconds(1),
				startedAt,
				finishedAt,
				null,
				false
		);
	}

	private static UUID callbackEventId(long taskId) {
		return UUID.nameUUIDFromBytes(("callback-event-" + taskId).getBytes(StandardCharsets.UTF_8));
	}
}
