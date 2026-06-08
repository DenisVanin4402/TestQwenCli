package com.example.testqwencli.dashboard;

import com.example.testqwencli.dashboard.enums.DashboardRequestPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Профиль нагрузки, который дашборд использует для генерации sync- и async-запросов к gateway.
 *
 * @param syncRps целевая интенсивность синхронных запросов в секунду; {@code 0} отключает sync-нагрузку.
 * @param asyncRps целевая интенсивность асинхронных submit-запросов в секунду; {@code 0} отключает async-нагрузку.
 * @param highPriorityPercent доля async-запросов с приоритетом {@link DashboardRequestPriority#HIGH}, от {@code 0}
 * до {@code 100}.
 * @param timeoutMs таймаут sync-вызова gateway из дашборда; если передан {@code 0}, используется значение по
 * умолчанию.
 * @param dispatchBatchSize размер батча, который дашборд просит использовать для ручного async/callback dispatch.
 * @param clientServices список клиентских сервисов, между которыми распределяется нагрузка; пустой список заменяется
 * сервисами по умолчанию.
 */
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

	/**
	 * Возвращает базовый профиль для локальной проверки gateway без ручной настройки дашборда.
	 *
	 * @return профиль с умеренной sync/async нагрузкой и двумя клиентскими сервисами.
	 */
	public static DashboardLoadProfile defaults() {
		return new DashboardLoadProfile(18, 28, 35, 1500, 30, DEFAULT_CLIENT_SERVICES);
	}
}
