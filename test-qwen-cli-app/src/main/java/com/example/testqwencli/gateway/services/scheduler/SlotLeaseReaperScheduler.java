package com.example.testqwencli.gateway.services.scheduler;

import com.example.testqwencli.dashboard.DashboardMetricsRegistry;
import com.example.testqwencli.gateway.services.SlotManager;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SlotLeaseReaperScheduler {

	private final SlotManager slotManager;
	private final DashboardMetricsRegistry metricsRegistry;

	SlotLeaseReaperScheduler(SlotManager slotManager, DashboardMetricsRegistry metricsRegistry) {
		this.slotManager = Objects.requireNonNull(slotManager, "slotManager must not be null");
		this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "metricsRegistry must not be null");
	}

	@Scheduled(fixedDelayString = "${external-gateway.slots.lease-reap-interval-ms:1000}")
	void reapExpiredLeases() {
		int expiredLeases = slotManager.reapExpiredLeases();
		metricsRegistry.recordExpiredLeases(expiredLeases);
	}
}
