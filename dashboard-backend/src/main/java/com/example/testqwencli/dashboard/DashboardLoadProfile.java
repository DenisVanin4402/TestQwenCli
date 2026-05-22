package com.example.testqwencli.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardLoadProfile(
		@Min(0) @Max(300) int syncRps,
		@Min(0) @Max(500) int asyncRps,
		@Min(0) @Max(100) int highPriorityPercent,
		@Min(100) @Max(60_000) int timeoutMs,
		@Min(1) @Max(200) int dispatchBatchSize,
		@Size(max = 10) List<String> clientServices
) {

	private static final List<String> DEFAULT_CLIENT_SERVICES = List.of("invest-pay", "user-expertise");

	public DashboardLoadProfile {
		if (timeoutMs == 0) {
			timeoutMs = 1500;
		}
		if (dispatchBatchSize == 0) {
			dispatchBatchSize = 30;
		}
		if (clientServices == null || clientServices.isEmpty()) {
			clientServices = DEFAULT_CLIENT_SERVICES;
		}
		clientServices = clientServices.stream()
				.filter(service -> service != null && !service.isBlank())
				.map(String::trim)
				.distinct()
				.toList();
		if (clientServices.isEmpty()) {
			clientServices = DEFAULT_CLIENT_SERVICES;
		}
	}

	public static DashboardLoadProfile defaults() {
		return new DashboardLoadProfile(18, 28, 35, 1500, 30, DEFAULT_CLIENT_SERVICES);
	}
}
