package com.example.testqwencli.dashboard;

import java.time.Instant;

public record DashboardSnapshot(
		Instant generatedAt,
		DashboardLoadState load,
		DashboardRuntimeMetrics metrics,
		DashboardHealthSnapshot health,
		DashboardSimulationSettings simulation
) {
}
