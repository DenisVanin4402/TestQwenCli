package com.example.testqwencli.gateway.slot;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
class SlotLeaseReaperScheduler {

	private final SlotManager slotManager;

	SlotLeaseReaperScheduler(SlotManager slotManager) {
		this.slotManager = Objects.requireNonNull(slotManager, "slotManager must not be null");
	}

	@Scheduled(fixedDelayString = "${external-gateway.slots.lease-reap-interval-ms:1000}")
	void reapExpiredLeases() {
		slotManager.reapExpiredLeases();
	}
}
