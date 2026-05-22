package com.example.testqwencli.gateway.slot;

@FunctionalInterface
public interface SlotReleaseNotificationPublisher {

	void publishSlotReleased();

	static SlotReleaseNotificationPublisher noop() {
		return () -> {
		};
	}
}
