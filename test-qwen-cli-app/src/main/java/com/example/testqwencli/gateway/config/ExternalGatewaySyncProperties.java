package com.example.testqwencli.gateway.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурация синхронного gateway API.
 *
 * @param waitTimeoutMs максимальное время ожидания sync-слота перед отказом клиенту
 */
@Validated
@ConfigurationProperties(prefix = "external-gateway.sync")
public record ExternalGatewaySyncProperties(
		@NotNull @DefaultValue("1500ms") Duration waitTimeoutMs
) {

	public ExternalGatewaySyncProperties {
		Objects.requireNonNull(waitTimeoutMs, "waitTimeoutMs must not be null");
		if (waitTimeoutMs.isNegative()) {
			throw new IllegalArgumentException("Timeout ожидания sync-слота не должен быть отрицательным");
		}
	}
}
