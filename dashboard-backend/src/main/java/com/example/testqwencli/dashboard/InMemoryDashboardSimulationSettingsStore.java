package com.example.testqwencli.dashboard;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class InMemoryDashboardSimulationSettingsStore implements DashboardSimulationSettingsStore {

	private final AtomicReference<DashboardSimulationSettings> settings =
			new AtomicReference<>(DashboardSimulationSettings.defaults());

	@Override
	public DashboardSimulationSettings current() {
		return settings.get();
	}

	@Override
	public DashboardSimulationSettings update(DashboardSimulationSettings settings) {
		Objects.requireNonNull(settings, "settings must not be null");
		this.settings.set(settings);
		return settings;
	}
}
