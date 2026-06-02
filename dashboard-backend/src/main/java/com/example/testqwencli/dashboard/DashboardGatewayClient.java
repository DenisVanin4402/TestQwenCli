package com.example.testqwencli.dashboard;

/**
 * Клиент gateway, через который нагрузочный дашборд выполняет sync- и async-сценарии.
 */
public interface DashboardGatewayClient {

	/**
	 * Выполняет синхронный запрос к gateway и нормализует результат для метрик дашборда.
	 *
	 * @param request тестовый запрос с внешним идентификатором, клиентским сервисом и payload.
	 * @return результат sync-вызова с длительностью и кодом ответа/ошибки.
	 */
	DashboardCallOutcome callSync(DashboardGatewayRequest request);

	/**
	 * Отправляет async-submit запрос в gateway и нормализует результат для метрик дашборда.
	 *
	 * @param request тестовый запрос с параметрами async-задачи и признаком callback-доставки.
	 * @return результат submit-операции с длительностью, кодом и task id при успешном принятии.
	 */
	DashboardSubmitOutcome submitAsync(DashboardGatewayRequest request);
}
