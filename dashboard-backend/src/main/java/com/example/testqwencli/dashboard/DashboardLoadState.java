package com.example.testqwencli.dashboard;

import java.time.Instant;

public record DashboardLoadState(
		boolean running,
		Instant startedAt,
		DashboardLoadProfile profile
) {
}
