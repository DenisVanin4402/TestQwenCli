package com.example.testqwencli.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DashboardSimulationSettings(
		@Min(0) @Max(20000) int latencyMs,
		@Min(0) @Max(3000) int jitterMs,
		@Min(0) @Max(100) int errorRatePercent,
		@Min(1) @Max(1024) int responseSizeKb,
		@Min(0) @Max(5000) int callbackLatencyMs,
		@Min(0) @Max(100) int callbackErrorRatePercent
) {

	public static DashboardSimulationSettings defaults() {
		return new DashboardSimulationSettings(25, 10, 0, 8, 20, 0);
	}
}
