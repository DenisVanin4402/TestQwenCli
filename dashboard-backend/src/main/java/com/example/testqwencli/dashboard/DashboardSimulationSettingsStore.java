package com.example.testqwencli.dashboard;

public interface DashboardSimulationSettingsStore {

	DashboardSimulationSettings current();

	DashboardSimulationSettings update(DashboardSimulationSettings settings);
}
