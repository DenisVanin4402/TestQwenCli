package com.example.testqwencli.dashboard;

import java.time.Instant;

/**
 * Полный снимок состояния дашборда, который UI получает одним HTTP-запросом.
 *
 * @param generatedAt момент формирования снимка.
 * @param load состояние генератора нагрузки.
 * @param metrics накопленные runtime-метрики.
 * @param health техническое состояние gateway и очередей.
 * @param simulation активные настройки симуляции upstream/callback.
 */
public record DashboardSnapshot(
		Instant generatedAt,
		DashboardLoadState load,
		DashboardRuntimeMetrics metrics,
		DashboardHealthSnapshot health,
		DashboardSimulationSettings simulation
) {
}
