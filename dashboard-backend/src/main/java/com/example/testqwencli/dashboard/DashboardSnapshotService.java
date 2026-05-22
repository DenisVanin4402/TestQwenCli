package com.example.testqwencli.dashboard;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

@Service
public class DashboardSnapshotService {

	private final DashboardLoadRunner loadRunner;
	private final DashboardMetricsRegistry metricsRegistry;
	private final DashboardHealthProvider healthProvider;
	private final DashboardSimulationSettingsStore simulationSettingsStore;
	private final Clock clock;

	public DashboardSnapshotService(
			DashboardLoadRunner loadRunner,
			DashboardMetricsRegistry metricsRegistry,
			DashboardHealthProvider healthProvider,
			DashboardSimulationSettingsStore simulationSettingsStore,
			Clock clock
	) {
		this.loadRunner = Objects.requireNonNull(loadRunner, "loadRunner must not be null");
		this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "metricsRegistry must not be null");
		this.healthProvider = Objects.requireNonNull(healthProvider, "healthProvider must not be null");
		this.simulationSettingsStore = Objects.requireNonNull(simulationSettingsStore,
				"simulationSettingsStore must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	public DashboardSnapshot snapshot() {
		return new DashboardSnapshot(
				clock.instant(),
				loadRunner.state(),
				metricsRegistry.snapshot(),
				healthProvider.snapshot(),
				simulationSettingsStore.current()
		);
	}
}
