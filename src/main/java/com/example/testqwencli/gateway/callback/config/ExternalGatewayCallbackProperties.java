package com.example.testqwencli.gateway.callback.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "external-gateway.callback")
public record ExternalGatewayCallbackProperties(
		@DefaultValue("false") boolean deliveryEnabled,
		@Min(1) @DefaultValue("3") int maxAttempts,
		@NotNull @DefaultValue("1000ms") Duration retryBackoffMs,
		@NotNull @DefaultValue("100ms") Duration deliveryIntervalMs
) {

	public ExternalGatewayCallbackProperties {
		Objects.requireNonNull(retryBackoffMs, "retryBackoffMs must not be null");
		Objects.requireNonNull(deliveryIntervalMs, "deliveryIntervalMs must not be null");
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
		if (retryBackoffMs.isNegative()) {
			throw new IllegalArgumentException("Backoff retry callback-доставки не должен быть отрицательным");
		}
		if (deliveryIntervalMs.isNegative() || deliveryIntervalMs.isZero()) {
			throw new IllegalArgumentException("Интервал callback-доставки должен быть положительным");
		}
	}
}
