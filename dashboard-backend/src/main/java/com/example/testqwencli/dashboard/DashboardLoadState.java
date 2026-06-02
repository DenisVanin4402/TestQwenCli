package com.example.testqwencli.dashboard;

import java.time.Instant;

/**
 * Текущее состояние генератора нагрузки в дашборде.
 *
 * @param running {@code true}, если нагрузочный поток сейчас запущен.
 * @param startedAt момент запуска текущей нагрузки; {@code null}, если нагрузка остановлена.
 * @param profile профиль, по которому запущена или будет запущена нагрузка.
 */
public record DashboardLoadState(
		boolean running,
		Instant startedAt,
		DashboardLoadProfile profile
) {
}
