package com.example.testqwencli.gateway.config;

import com.example.testqwencli.gateway.model.slot.enums.SyncAcquireWaitMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Конфигурация общего пула слотов external gateway.
 *
 * @param total общее число одновременных upstream-вызовов
 * @param targetFreeSyncSlots резерв, который async-нагрузка должна оставлять для sync
 * @param leaseTtl TTL lease-записи слота
 * @param syncWaiterTtl TTL записи sync-ожидателя
 * @param syncAcquirePollInterval fallback интервал между попытками получить sync-слот
 * @param syncAcquireWaitMode стратегия ожидания sync-слота
 */
@Validated
@ConfigurationProperties(prefix = "external-gateway.slots")
public record ExternalGatewaySlotProperties(
		@Min(1) int total,
		@Min(0) int targetFreeSyncSlots,
		@NotNull Duration leaseTtl,
		@NotNull Duration syncWaiterTtl,
		@NotNull @DefaultValue("10ms") Duration syncAcquirePollInterval,
		@NotNull @DefaultValue("POLLING") SyncAcquireWaitMode syncAcquireWaitMode
) {

	public ExternalGatewaySlotProperties {
		Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
		Objects.requireNonNull(syncWaiterTtl, "syncWaiterTtl must not be null");
		Objects.requireNonNull(syncAcquirePollInterval, "syncAcquirePollInterval must not be null");
		Objects.requireNonNull(syncAcquireWaitMode, "syncAcquireWaitMode must not be null");
		if (total < 1) {
			throw new IllegalArgumentException("Общее число слотов должно быть положительным");
		}
		if (targetFreeSyncSlots < 0) {
			throw new IllegalArgumentException("Резерв sync-слотов не должен быть отрицательным");
		}
		requirePositive(leaseTtl, "TTL lease слота должен быть положительным");
		requirePositive(syncWaiterTtl, "TTL sync waiter должен быть положительным");
		requirePositive(syncAcquirePollInterval, "Интервал опроса sync-слота должен быть положительным");
		if (targetFreeSyncSlots > total) {
			throw new IllegalArgumentException("Резерв sync-слотов не должен превышать общее число слотов");
		}
	}

	private static void requirePositive(Duration value, String message) {
		if (value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(message);
		}
	}
}
