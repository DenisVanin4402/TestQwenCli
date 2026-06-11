package com.example.testqwencli.gateway.services.scheduler;

import com.example.testqwencli.dashboard.DashboardMetricsRegistry;
import com.example.testqwencli.gateway.config.ExternalGatewayAsyncProperties;
import com.example.testqwencli.gateway.config.ExternalGatewayCallbackProperties;
import com.example.testqwencli.gateway.model.slot.SlotLease;
import com.example.testqwencli.gateway.services.CallbackDeliveryDispatcher;
import com.example.testqwencli.gateway.services.ExternalAsyncDispatcher;
import com.example.testqwencli.gateway.services.SlotManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySchedulerTest {

	@Test
	void asyncSchedulerDispatchUsesConfiguredBatchSizeAndRecordsPositiveCount() {
		RecordingAsyncDispatcher dispatcher = new RecordingAsyncDispatcher(3);
		DashboardMetricsRegistry metricsRegistry = metricsRegistry();
		ExternalAsyncDispatcherScheduler scheduler = new ExternalAsyncDispatcherScheduler(dispatcher,
				asyncProperties(7), metricsRegistry);

		scheduler.dispatch();

		assertThat(dispatcher.lastMaxIterations()).isEqualTo(7);
		assertThat(dispatcher.calls()).isEqualTo(1);
		assertThat(metricsRegistry.snapshot().asyncDispatchIterations()).isEqualTo(3);
	}

	@Test
	void asyncSchedulerDoesNotRecordZeroCount() {
		RecordingAsyncDispatcher dispatcher = new RecordingAsyncDispatcher(0);
		DashboardMetricsRegistry metricsRegistry = metricsRegistry();
		ExternalAsyncDispatcherScheduler scheduler = new ExternalAsyncDispatcherScheduler(dispatcher,
				asyncProperties(7), metricsRegistry);

		scheduler.dispatch();

		assertThat(dispatcher.calls()).isEqualTo(1);
		assertThat(metricsRegistry.snapshot().asyncDispatchIterations()).isZero();
	}

	@Test
	void callbackSchedulerDispatchUsesConfiguredBatchSizeAndRecordsPositiveCount() {
		RecordingCallbackDispatcher dispatcher = new RecordingCallbackDispatcher(4, 0);
		DashboardMetricsRegistry metricsRegistry = metricsRegistry();
		CallbackDeliveryDispatcherScheduler scheduler = new CallbackDeliveryDispatcherScheduler(dispatcher,
				callbackProperties(6), metricsRegistry);

		scheduler.dispatch();

		assertThat(dispatcher.lastMaxIterations()).isEqualTo(6);
		assertThat(dispatcher.dispatchCalls()).isEqualTo(1);
		assertThat(metricsRegistry.snapshot().callbackDispatchIterations()).isEqualTo(4);
	}

	@Test
	void callbackSchedulerDoesNotRecordZeroCount() {
		RecordingCallbackDispatcher dispatcher = new RecordingCallbackDispatcher(0, 0);
		DashboardMetricsRegistry metricsRegistry = metricsRegistry();
		CallbackDeliveryDispatcherScheduler scheduler = new CallbackDeliveryDispatcherScheduler(dispatcher,
				callbackProperties(6), metricsRegistry);

		scheduler.dispatch();

		assertThat(dispatcher.dispatchCalls()).isEqualTo(1);
		assertThat(metricsRegistry.snapshot().callbackDispatchIterations()).isZero();
	}

	@Test
	void callbackSchedulerRunsRecoveryOnStartupAndScheduledTick() {
		RecordingCallbackDispatcher dispatcher = new RecordingCallbackDispatcher(0, 2);
		CallbackDeliveryDispatcherScheduler scheduler = new CallbackDeliveryDispatcherScheduler(dispatcher,
				callbackProperties(6), metricsRegistry());

		scheduler.recoverOnStartup();
		scheduler.recoverTimedOutDeliveries();

		assertThat(dispatcher.recoverCalls()).isEqualTo(2);
	}

	@Test
	void slotLeaseReaperSchedulerRecordsOnlyPositiveExpiredLeaseCount() {
		RecordingSlotManager slotManager = new RecordingSlotManager(2, 0);
		DashboardMetricsRegistry metricsRegistry = metricsRegistry();
		SlotLeaseReaperScheduler scheduler = new SlotLeaseReaperScheduler(slotManager, metricsRegistry);

		scheduler.reapExpiredLeases();
		scheduler.reapExpiredLeases();

		assertThat(slotManager.reapCalls()).isEqualTo(2);
		assertThat(metricsRegistry.snapshot().expiredLeases()).isEqualTo(2);
	}

	@Test
	void disabledAsyncSchedulerBeanIsNotCreated() {
		asyncContextRunner("external-gateway.async.dispatcher-enabled=false")
				.run(context -> assertThat(context.getBeansOfType(ExternalAsyncDispatcherScheduler.class)).isEmpty());
	}

	@Test
	void enabledAsyncSchedulerBeanIsCreated() {
		asyncContextRunner("external-gateway.async.dispatcher-enabled=true")
				.run(context -> assertThat(context).hasSingleBean(ExternalAsyncDispatcherScheduler.class));
	}

	@Test
	void disabledCallbackSchedulerBeanIsNotCreated() {
		callbackContextRunner("external-gateway.callback.delivery-enabled=false")
				.run(context -> assertThat(context.getBeansOfType(CallbackDeliveryDispatcherScheduler.class))
						.isEmpty());
	}

	@Test
	void enabledCallbackSchedulerBeanIsCreated() {
		callbackContextRunner("external-gateway.callback.delivery-enabled=true")
				.run(context -> assertThat(context).hasSingleBean(CallbackDeliveryDispatcherScheduler.class));
	}

	private static ApplicationContextRunner asyncContextRunner(String property) {
		return new ApplicationContextRunner()
				.withUserConfiguration(AsyncSchedulerConfiguration.class)
				.withBean(ExternalAsyncDispatcher.class, () -> new RecordingAsyncDispatcher(1))
				.withBean(ExternalGatewayAsyncProperties.class, () -> asyncProperties(5))
				.withBean(DashboardMetricsRegistry.class, GatewaySchedulerTest::metricsRegistry)
				.withPropertyValues(property);
	}

	private static ApplicationContextRunner callbackContextRunner(String property) {
		return new ApplicationContextRunner()
				.withUserConfiguration(CallbackSchedulerConfiguration.class)
				.withBean(CallbackDeliveryDispatcher.class, () -> new RecordingCallbackDispatcher(1, 1))
				.withBean(ExternalGatewayCallbackProperties.class, () -> callbackProperties(5))
				.withBean(DashboardMetricsRegistry.class, GatewaySchedulerTest::metricsRegistry)
				.withPropertyValues(property);
	}

	private static ExternalGatewayAsyncProperties asyncProperties(int dispatchBatchSize) {
		return new ExternalGatewayAsyncProperties(Duration.ofMillis(100), 3, true, dispatchBatchSize,
				Duration.ofSeconds(1));
	}

	private static ExternalGatewayCallbackProperties callbackProperties(int deliveryBatchSize) {
		return new ExternalGatewayCallbackProperties(true, 3, deliveryBatchSize, Duration.ofSeconds(1),
				Duration.ofMillis(100), Duration.ofSeconds(30), Duration.ofSeconds(1));
	}

	private static DashboardMetricsRegistry metricsRegistry() {
		return new DashboardMetricsRegistry(Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC));
	}

	@Configuration(proxyBeanMethods = false)
	@Import(ExternalAsyncDispatcherScheduler.class)
	private static class AsyncSchedulerConfiguration {
	}

	@Configuration(proxyBeanMethods = false)
	@Import(CallbackDeliveryDispatcherScheduler.class)
	private static class CallbackSchedulerConfiguration {
	}

	private static final class RecordingAsyncDispatcher implements ExternalAsyncDispatcher {

		private final int result;
		private int lastMaxIterations;
		private int calls;

		private RecordingAsyncDispatcher(int result) {
			this.result = result;
		}

		@Override
		public boolean dispatchOnce() {
			return result > 0;
		}

		@Override
		public int dispatchBatch(int maxIterations) {
			this.lastMaxIterations = maxIterations;
			this.calls++;
			return result;
		}

		@Override
		public void shutdown() {
		}

		private int lastMaxIterations() {
			return lastMaxIterations;
		}

		private int calls() {
			return calls;
		}
	}

	private static final class RecordingCallbackDispatcher implements CallbackDeliveryDispatcher {

		private final int dispatchResult;
		private final int recoveryResult;
		private int lastMaxIterations;
		private int dispatchCalls;
		private int recoverCalls;

		private RecordingCallbackDispatcher(int dispatchResult, int recoveryResult) {
			this.dispatchResult = dispatchResult;
			this.recoveryResult = recoveryResult;
		}

		@Override
		public boolean dispatchOnce() {
			return dispatchResult > 0;
		}

		@Override
		public int dispatchBatch(int maxIterations) {
			this.lastMaxIterations = maxIterations;
			this.dispatchCalls++;
			return dispatchResult;
		}

		@Override
		public int recoverTimedOutDeliveries() {
			this.recoverCalls++;
			return recoveryResult;
		}

		@Override
		public void shutdown() {
		}

		private int lastMaxIterations() {
			return lastMaxIterations;
		}

		private int dispatchCalls() {
			return dispatchCalls;
		}

		private int recoverCalls() {
			return recoverCalls;
		}
	}

	private static final class RecordingSlotManager implements SlotManager {

		private final int[] reapResults;
		private int reapCalls;

		private RecordingSlotManager(int... reapResults) {
			this.reapResults = reapResults;
		}

		@Override
		public Optional<SlotLease> acquireSyncSlot(String owner, Duration timeout) {
			return Optional.empty();
		}

		@Override
		public Optional<SlotLease> tryAcquireAsyncSlot(String owner, String taskId) {
			return Optional.empty();
		}

		@Override
		public boolean release(int slotId, UUID leaseId) {
			return false;
		}

		@Override
		public Optional<SlotLease> heartbeat(int slotId, UUID leaseId) {
			return Optional.empty();
		}

		@Override
		public int reapExpiredLeases() {
			int index = Math.min(reapCalls, reapResults.length - 1);
			reapCalls++;
			return reapResults[index];
		}

		private int reapCalls() {
			return reapCalls;
		}
	}
}
