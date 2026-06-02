package com.example.testqwencli.gateway.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурация планирования, отправки и recovery callback-доставок.
 *
 * @param deliveryEnabled включает scheduled callback dispatcher
 * @param maxAttempts максимальное число попыток доставки
 * @param deliveryBatchSize максимальное число delivery worker-ов за один tick
 * @param retryBackoffMs задержка перед повторной доставкой
 * @param deliveryIntervalMs интервал scheduler-а callback dispatcher-а
 * @param deliveryTimeoutMs таймаут, после которого {@code DELIVERING} считается зависшим
 * @param deliveryRecoveryIntervalMs интервал scheduler-а recovery зависших доставок
 */
@Validated
@ConfigurationProperties(prefix = "external-gateway.callback")
public record ExternalGatewayCallbackProperties(
		@DefaultValue("false") boolean deliveryEnabled,
		@Min(1) @DefaultValue("3") int maxAttempts,
		@Min(1) @Max(10) @DefaultValue("10") int deliveryBatchSize,
		@NotNull @DefaultValue("1000ms") Duration retryBackoffMs,
		@NotNull @DefaultValue("100ms") Duration deliveryIntervalMs,
		@NotNull @DefaultValue("30s") Duration deliveryTimeoutMs,
		@NotNull @DefaultValue("1000ms") Duration deliveryRecoveryIntervalMs
) {

	public ExternalGatewayCallbackProperties {
		Objects.requireNonNull(retryBackoffMs, "retryBackoffMs must not be null");
		Objects.requireNonNull(deliveryIntervalMs, "deliveryIntervalMs must not be null");
		Objects.requireNonNull(deliveryTimeoutMs, "deliveryTimeoutMs must not be null");
		Objects.requireNonNull(deliveryRecoveryIntervalMs, "deliveryRecoveryIntervalMs must not be null");
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts должен быть положительным");
		}
		if (deliveryBatchSize < 1 || deliveryBatchSize > 10) {
			throw new IllegalArgumentException("deliveryBatchSize должен быть от 1 до 10");
		}
		if (retryBackoffMs.isNegative()) {
			throw new IllegalArgumentException("Backoff retry callback-доставки не должен быть отрицательным");
		}
		if (deliveryIntervalMs.isNegative() || deliveryIntervalMs.isZero()) {
			throw new IllegalArgumentException("Интервал callback-доставки должен быть положительным");
		}
		if (deliveryTimeoutMs.isNegative() || deliveryTimeoutMs.isZero()) {
			throw new IllegalArgumentException("Timeout callback-доставки должен быть положительным");
		}
		if (deliveryRecoveryIntervalMs.isNegative() || deliveryRecoveryIntervalMs.isZero()) {
			throw new IllegalArgumentException("Интервал восстановления callback-доставок должен быть положительным");
		}
	}
}
