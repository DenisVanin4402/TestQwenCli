package com.example.testqwencli.dashboard;

public record DashboardCallOutcome(
		DashboardCallStatus status,
		long durationMs,
		String code
) {
}
