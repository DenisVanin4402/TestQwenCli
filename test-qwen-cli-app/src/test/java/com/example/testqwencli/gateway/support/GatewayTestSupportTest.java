package com.example.testqwencli.gateway.support;

import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import com.example.testqwencli.gateway.model.async.enums.AsyncTaskStatus;
import com.example.testqwencli.gateway.model.callback.CallbackPayload;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayTestSupportTest {

	@Test
	void requestFactoriesCreateStableSyncAndAsyncRequests() {
		ExternalSyncRequest syncRequest = GatewayTestRequests.syncRequest(GatewayTestRequests.externalId(10));
		ExternalAsyncRequest asyncRequest = GatewayTestRequests.asyncPollingRequest(GatewayTestRequests.externalId(11));

		assertThat(syncRequest.clientService()).isEqualTo(GatewayTestRequests.CLIENT_SERVICE);
		assertThat(syncRequest.payload()).containsEntry("operation", "calculate");
		assertThat(asyncRequest.deliveryMode()).isEqualTo(AsyncDeliveryMode.POLLING);
		assertThat(asyncRequest.payload()).containsEntry("currency", "RUB");
	}

	@Test
	void callbackFactoryBuildsPayloadFromFinalTask() {
		CallbackPayload payload = GatewayTestRequests.doneCallbackPayload(42);

		assertThat(payload.taskId()).isEqualTo(42);
		assertThat(payload.status()).isEqualTo(AsyncTaskStatus.DONE);
		assertThat(payload.result()).containsEntry("decision", "APPROVED");
		assertThat(payload.error()).isNull();
	}

	@Test
	void mutableClockCanMoveTimeAndChangeZone() {
		MutableTestClock clock = new MutableTestClock(Instant.parse("2026-05-22T00:00:00Z"));

		assertThat(clock.advance(Duration.ofSeconds(5))).isEqualTo(Instant.parse("2026-05-22T00:00:05Z"));
		assertThat(clock.set(Instant.parse("2026-05-22T00:01:00Z")))
				.isEqualTo(Instant.parse("2026-05-22T00:01:00Z"));
		assertThat(clock.withZone(ZoneId.of("Europe/Moscow")).getZone())
				.isEqualTo(ZoneId.of("Europe/Moscow"));
	}

	@Test
	void awaiterWaitsUntilOptionalAppears() {
		AtomicInteger attempts = new AtomicInteger();
		AsyncTestAwaiter awaiter = AsyncTestAwaiter.of(Duration.ofMillis(200), Duration.ofMillis(1));

		String result = awaiter.untilPresent("test optional",
				() -> attempts.incrementAndGet() >= 2 ? Optional.of("ready") : Optional.empty());

		assertThat(result).isEqualTo("ready");
		assertThat(attempts).hasValue(2);
	}

	@Test
	void awaiterReportsDescriptionOnTimeout() {
		AsyncTestAwaiter awaiter = AsyncTestAwaiter.of(Duration.ofMillis(20), Duration.ofMillis(1));

		assertThatThrownBy(() -> awaiter.untilTrue("never true", () -> false))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("never true");
	}
}
