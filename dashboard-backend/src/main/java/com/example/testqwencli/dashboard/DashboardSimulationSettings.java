package com.example.testqwencli.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Настройки симуляции внешнего upstream и callback endpoint, которыми управляет дашборд.
 *
 * @param latencyMs базовая задержка ответа upstream в миллисекундах.
 * @param jitterMs случайное отклонение задержки upstream в миллисекундах.
 * @param errorRatePercent вероятность ошибки upstream в процентах.
 * @param responseSizeKb размер синтетического ответа upstream в килобайтах.
 * @param callbackLatencyMs базовая задержка симулированного callback endpoint в миллисекундах.
 * @param callbackErrorRatePercent вероятность ошибки callback endpoint в процентах.
 */
public record DashboardSimulationSettings(
		@Min(0) @Max(20000) int latencyMs,
		@Min(0) @Max(3000) int jitterMs,
		@Min(0) @Max(100) int errorRatePercent,
		@Min(1) @Max(1024) int responseSizeKb,
		@Min(0) @Max(5000) int callbackLatencyMs,
		@Min(0) @Max(100) int callbackErrorRatePercent
) {

	/**
	 * Возвращает настройки симуляции, пригодные для локального запуска без ручной конфигурации.
	 *
	 * @return базовые задержки, отсутствие ошибок и небольшой размер ответа.
	 */
	public static DashboardSimulationSettings defaults() {
		return new DashboardSimulationSettings(25, 10, 0, 8, 20, 0);
	}
}
