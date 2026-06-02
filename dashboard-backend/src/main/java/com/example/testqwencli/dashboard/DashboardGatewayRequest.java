package com.example.testqwencli.dashboard;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Запрос, который нагрузочный дашборд отправляет в gateway как sync-вызов или async-submit.
 *
 * @param externalId внешний идентификатор операции; используется gateway для идемпотентности async-задач.
 * @param clientService имя клиентского сервиса, от имени которого выполняется запрос.
 * @param priority приоритет запроса для async-очереди.
 * @param callbackDelivery {@code true}, если после async-обработки нужно создать callback-доставку.
 * @param payload тестовая бизнес-нагрузка, передаваемая через gateway в upstream.
 * @param requestId идентификатор HTTP-запроса дашборда для трассировки.
 */
public record DashboardGatewayRequest(
		UUID externalId,
		String clientService,
		DashboardRequestPriority priority,
		boolean callbackDelivery,
		Map<String, Object> payload,
		String requestId
) {

	/**
	 * Ключ payload-поля, через которое дашборд передает индивидуальный таймаут sync-вызова.
	 */
	public static final String SYNC_TIMEOUT_PAYLOAD_KEY = "dashboardSyncTimeoutMs";

	public DashboardGatewayRequest {
		Objects.requireNonNull(externalId, "externalId must not be null");
		Objects.requireNonNull(clientService, "clientService must not be null");
		Objects.requireNonNull(priority, "priority must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		payload = Map.copyOf(payload);
	}
}
