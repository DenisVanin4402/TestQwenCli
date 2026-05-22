package com.example.testqwencli.gateway.callback;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "external-gateway.callback.delivery-enabled", havingValue = "true")
class CallbackDeliveryDispatcherScheduler {

	private final CallbackDeliveryDispatcher dispatcher;

	CallbackDeliveryDispatcherScheduler(CallbackDeliveryDispatcher dispatcher) {
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
	}

	@Scheduled(fixedDelayString = "${external-gateway.callback.delivery-interval-ms:100}")
	void dispatch() {
		dispatcher.dispatchOnce();
	}
}
