package com.example.testqwencli.gateway.async;

public enum CallbackDeliveryStatus {
	NOT_REQUIRED,
	PENDING,
	DELIVERING,
	DELIVERED,
	RETRY,
	DEAD
}
