package com.example.testqwencli.gateway.callback;

import com.example.testqwencli.gateway.callback.config.ExternalGatewayCallbackProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "external-gateway.callback.delivery-enabled", havingValue = "true")
class CallbackDeliveryDispatcherScheduler {

	private final CallbackDeliveryDispatcher dispatcher;
	private final ExternalGatewayCallbackProperties properties;

	CallbackDeliveryDispatcherScheduler(
			CallbackDeliveryDispatcher dispatcher,
			ExternalGatewayCallbackProperties properties
	) {
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	@Scheduled(fixedDelayString = "${external-gateway.callback.delivery-interval-ms:100}")
	void dispatch() {
		dispatcher.dispatchBatch(properties.deliveryBatchSize());
	}
}
