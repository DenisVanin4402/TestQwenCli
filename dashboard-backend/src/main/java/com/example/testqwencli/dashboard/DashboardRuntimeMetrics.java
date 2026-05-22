package com.example.testqwencli.dashboard;

public record DashboardRuntimeMetrics(
		long syncSuccess,
		long syncNoSlot,
		long syncTimeout,
		long syncErrors,
		long asyncAccepted,
		long asyncRejected,
		long asyncErrors,
		long asyncDispatchIterations,
		long callbackDispatchIterations,
		long expiredLeases,
		int activeSyncRequests,
		int activeAsyncSubmits,
		long actualSyncRps,
		long actualAsyncRps,
		long p50LatencyMs,
		long p95LatencyMs,
		long p99LatencyMs
) {
}
