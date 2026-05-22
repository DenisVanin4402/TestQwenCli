package com.example.testqwencli.gateway.async.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "external-gateway.async")
public record ExternalGatewayAsyncProperties(
		@NotNull @DefaultValue("100ms") Duration dispatchIntervalMs,
		@Min(1) @DefaultValue("3") int maxAttempts,
		@DefaultValue("false") boolean dispatcherEnabled,
		@NotNull @DefaultValue("1000ms") Duration retryBackoffMs
) {

	public ExternalGatewayAsyncProperties {
		Objects.requireNonNull(dispatchIntervalMs, "dispatchIntervalMs must not be null");
		Objects.requireNonNull(retryBackoffMs, "retryBackoffMs must not be null");
		if (dispatchIntervalMs.isNegative() || dispatchIntervalMs.isZero()) {
			throw new IllegalArgumentException("Интервал dispatcher async-задач должен быть положительным");
		}
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
		if (retryBackoffMs.isNegative()) {
			throw new IllegalArgumentException("Backoff retry async-задач не должен быть отрицательным");
		}
	}
}
