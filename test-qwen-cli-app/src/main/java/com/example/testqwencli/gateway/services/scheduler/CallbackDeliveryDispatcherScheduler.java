package com.example.testqwencli.gateway.services.scheduler;

import com.example.testqwencli.dashboard.DashboardMetricsRegistry;
import com.example.testqwencli.gateway.config.ExternalGatewayCallbackProperties;
import com.example.testqwencli.gateway.services.CallbackDeliveryDispatcher;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "external-gateway.callback.delivery-enabled", havingValue = "true")
class CallbackDeliveryDispatcherScheduler {

	private final CallbackDeliveryDispatcher dispatcher;
	private final ExternalGatewayCallbackProperties properties;
	private final DashboardMetricsRegistry metricsRegistry;

	CallbackDeliveryDispatcherScheduler(
			CallbackDeliveryDispatcher dispatcher,
			ExternalGatewayCallbackProperties properties,
			DashboardMetricsRegistry metricsRegistry
	) {
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "metricsRegistry must not be null");
	}

	@Scheduled(fixedDelayString = "${external-gateway.callback.delivery-interval-ms:100}")
	void dispatch() {
		int dispatched = dispatcher.dispatchBatch(properties.deliveryBatchSize());
		metricsRegistry.recordCallbackDispatchIterations(dispatched);
	}

	@EventListener(ApplicationReadyEvent.class)
	void recoverOnStartup() {
		dispatcher.recoverTimedOutDeliveries();
	}

	@Scheduled(fixedDelayString = "${external-gateway.callback.delivery-recovery-interval-ms:1000}")
	void recoverTimedOutDeliveries() {
		dispatcher.recoverTimedOutDeliveries();
	}
}
