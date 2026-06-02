package com.example.testqwencli.gateway.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурация simulated upstream adapter.
 *
 * @param simulatedDelayMs базовая задержка ответа upstream в локальной симуляции
 */
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
