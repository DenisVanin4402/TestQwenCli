package com.example.testqwencli.gateway.sync.error;

import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Map;

public final class NoSlotAvailableException extends ExternalGatewayException {

	public NoSlotAvailableException(String requestId, Duration waitTimeout) {
		super(HttpStatus.TOO_MANY_REQUESTS, "NO_SLOT_AVAILABLE",
				"Нет свободного sync-слота в пределах заданного ожидания", true, requestId,
				Map.of("syncWaitTimeoutMs", waitTimeout.toMillis()));
	}
}
