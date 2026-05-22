package com.example.testqwencli.dashboard;

public record DashboardSubmitOutcome(
		DashboardSubmitStatus status,
		long durationMs,
		String code,
		Long taskId
) {
}
