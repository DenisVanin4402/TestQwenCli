package com.example.testqwencli.gateway.async;

import com.example.testqwencli.gateway.async.config.ExternalGatewayAsyncProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "external-gateway.async.dispatcher-enabled", havingValue = "true")
class ExternalAsyncDispatcherScheduler {

	private final ExternalAsyncDispatcher dispatcher;
	private final ExternalGatewayAsyncProperties properties;

	ExternalAsyncDispatcherScheduler(ExternalAsyncDispatcher dispatcher, ExternalGatewayAsyncProperties properties) {
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	@Scheduled(fixedDelayString = "${external-gateway.async.dispatch-interval-ms:100}")
	void dispatch() {
		dispatcher.dispatchBatch(properties.dispatchBatchSize());
	}
}
