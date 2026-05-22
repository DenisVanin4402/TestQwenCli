package com.example.testqwencli.dashboard;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

	private final DashboardLoadRunner loadRunner;
	private final DashboardMetricsRegistry metricsRegistry;
	private final DashboardSnapshotService snapshotService;
	private final DashboardSimulationSettingsStore simulationSettingsStore;
	private final DashboardHealthProvider healthProvider;

	public DashboardController(
			DashboardLoadRunner loadRunner,
			DashboardMetricsRegistry metricsRegistry,
			DashboardSnapshotService snapshotService,
			DashboardSimulationSettingsStore simulationSettingsStore,
			DashboardHealthProvider healthProvider
	) {
		this.loadRunner = Objects.requireNonNull(loadRunner, "loadRunner must not be null");
		this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "metricsRegistry must not be null");
		this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService must not be null");
		this.simulationSettingsStore = Objects.requireNonNull(simulationSettingsStore,
				"simulationSettingsStore must not be null");
		this.healthProvider = Objects.requireNonNull(healthProvider, "healthProvider must not be null");
	}

	@GetMapping
	public ResponseEntity<Void> dashboardPage() {
		return ResponseEntity.status(302).location(URI.create("/dashboard/index.html")).build();
	}

	@GetMapping("/api/snapshot")
	public DashboardSnapshot snapshot() {
		return snapshotService.snapshot();
	}

	@GetMapping("/api/health")
	public DashboardHealthSnapshot health() {
		return healthProvider.snapshot();
	}

	@GetMapping("/api/load/profile")
	public DashboardLoadProfile profile() {
		return loadRunner.state().profile();
	}

	@PutMapping("/api/load/profile")
	public DashboardLoadProfile updateProfile(@Valid @RequestBody DashboardLoadProfile profile) {
		return loadRunner.updateProfile(profile);
	}

	@PostMapping("/api/load/start")
	public DashboardLoadState startLoad() {
		return loadRunner.start();
	}

	@PostMapping("/api/load/stop")
	public DashboardLoadState stopLoad() {
		return loadRunner.stop();
	}

	@PostMapping("/api/load/reset")
	public DashboardSnapshot resetMetrics() {
		metricsRegistry.reset();
		return snapshotService.snapshot();
	}

	@GetMapping("/api/upstream-simulation")
	public DashboardSimulationSettings simulationSettings() {
		return simulationSettingsStore.current();
	}

	@PutMapping("/api/upstream-simulation")
	public DashboardSimulationSettings updateSimulationSettings(
			@Valid @RequestBody DashboardSimulationSettings settings
	) {
		return simulationSettingsStore.update(settings);
	}
}
