package ru.sberbank.pprb.agent.investcorr.client;

import java.util.concurrent.atomic.AtomicInteger;
import ru.sberbank.pprb.agent.investcorr.config.StubScenario;
import ru.sberbank.pprb.agent.investcorr.exception.InvestCorrCallException;
import ru.sberbank.pprb.agent.investcorr.generated.model.StatusRequest;
import ru.sberbank.pprb.agent.investcorr.generated.model.StatusResponse;

/**
 * Моделирует один вызов corr без сети: успех, отказ или потерю ответа. Не реализует журнал отправок
 * или дедупликацию; повторный вызов может создать новый внешний эффект.
 */
public class StubInvestCorrClient implements InvestCorrClient {
    /** Серверная настройка сценария; пользовательское ACL-сообщение её не меняет. */
    private final StubScenario scenario;

    /** Счётчик вызовов для проверки, что менеджер не делает автоматических повторов. */
    private final AtomicInteger calls = new AtomicInteger();

    /** Число смоделированных принятий, в том числе с потерянным ответом. */
    private final AtomicInteger effects = new AtomicInteger();

    /** Получает сценарий стенда из проверенной конфигурации приложения. */
    public StubInvestCorrClient(StubScenario scenario) {
        this.scenario = scenario;
    }

    @Override
    public StatusResponse submit(String key, StatusRequest request) {
        calls.incrementAndGet();
        return switch (scenario) {
            case ACCEPTED -> accept(request);
            case REJECTED ->
                    new StatusResponse()
                            .outcome("REJECTED")
                            .reasonCode("TEST_REJECTION")
                            .reasonText("Тестовый стенд отклонил запрос.");
            case TIMEOUT -> throw new InvestCorrCallException("TIMEOUT");
            case ACCEPTED_RESPONSE_LOST -> {
                accept(request);
                throw new InvestCorrCallException("RESPONSE_LOST");
            }
        };
    }

    /** Возвращает число фактических обращений к стенду, включая обращения с ошибкой. */
    public int physicalCalls() {
        return calls.get();
    }

    /** Возвращает число принятий стендом; локальный rollback приложения его не уменьшает. */
    public int logicalEffects() {
        return effects.get();
    }

    /** Моделирует принятие запроса и возвращает новый транспортный ответ. */
    private StatusResponse accept(StatusRequest request) {
        effects.incrementAndGet();
        return new StatusResponse()
                .outcome("ACCEPTED")
                .reference("stub-corr-" + request.getClientRequestId());
    }
}
