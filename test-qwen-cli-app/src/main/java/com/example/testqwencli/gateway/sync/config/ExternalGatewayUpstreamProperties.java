package com.example.testqwencli.gateway.sync.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "external-gateway.upstream")
public record ExternalGatewayUpstreamProperties(
		@NotNull @DefaultValue("25ms") Duration simulatedDelayMs
) {

	public ExternalGatewayUpstreamProperties {
		Objects.requireNonNull(simulatedDelayMs, "simulatedDelayMs must not be null");
		if (simulatedDelayMs.isNegative()) {
			throw new IllegalArgumentException("Задержка simulated upstream не должна быть отрицательной");
		}
	}
}
