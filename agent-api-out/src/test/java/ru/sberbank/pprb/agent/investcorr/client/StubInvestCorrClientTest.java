package ru.sberbank.pprb.agent.investcorr.client;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.sberbank.pprb.agent.investcorr.config.StubScenario;
import ru.sberbank.pprb.agent.investcorr.exception.InvestCorrCallException;
import ru.sberbank.pprb.agent.investcorr.mapper.InvestCorrMapperImpl;
import ru.sberbank.pprb.agent.investcorr.service.InvestCorrAdapter;
import ru.sberbank.pprb.agent.model.dto.operation.OperationContext;
import ru.sberbank.pprb.agent.model.enums.Operation;

/** Проверяет успех и ошибки одного вызова менеджера без журналов и повторных попыток. */
class StubInvestCorrClientTest {
    /** Успех создаёт один эффект, а потеря ответа не отменяет уже выполненный внешний эффект. */
    @Test
    void performsOneCallForEachOutcome() {
        OperationContext context =
                new OperationContext(UUID.randomUUID(), "org", "user", "payment", null);
        for (StubScenario scenario : StubScenario.values()) {
            StubInvestCorrClient client = new StubInvestCorrClient(scenario);
            InvestCorrAdapter manager = new InvestCorrAdapter(client, new InvestCorrMapperImpl());
            if (scenario == StubScenario.ACCEPTED) {
                manager.callOperation(Operation.STATUS, context);
            } else {
                assertThatThrownBy(() -> manager.callOperation(Operation.STATUS, context))
                        .isInstanceOf(InvestCorrCallException.class);
            }
            assertThat(client.physicalCalls()).isEqualTo(1);
            assertThat(client.logicalEffects())
                    .isEqualTo(
                            scenario == StubScenario.ACCEPTED
                                            || scenario == StubScenario.ACCEPTED_RESPONSE_LOST
                                    ? 1
                                    : 0);
        }
    }
}
