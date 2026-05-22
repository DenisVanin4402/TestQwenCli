package com.example.testqwencli.gateway.async;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "external-gateway.async.dispatcher-enabled", havingValue = "true")
class ExternalAsyncDispatcherScheduler {

	private final ExternalAsyncDispatcher dispatcher;

	ExternalAsyncDispatcherScheduler(ExternalAsyncDispatcher dispatcher) {
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
	}

	@Scheduled(fixedDelayString = "${external-gateway.async.dispatch-interval-ms:100}")
	void dispatch() {
		dispatcher.dispatchOnce();
	}
}
