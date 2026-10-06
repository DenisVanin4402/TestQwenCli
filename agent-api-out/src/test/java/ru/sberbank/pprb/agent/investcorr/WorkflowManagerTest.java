package ru.sberbank.pprb.agent.investcorr;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.sberbank.pprb.agent.investcorr.client.InvestCorrClient;
import ru.sberbank.pprb.agent.investcorr.exception.InvestCorrCallException;
import ru.sberbank.pprb.agent.investcorr.mapper.InvestCorrMapperImpl;
import ru.sberbank.pprb.agent.investcorr.service.InvestCorrAdapter;
import ru.sberbank.pprb.agent.model.dto.operation.OperationContext;
import ru.sberbank.pprb.agent.model.enums.Operation;

/**
 * Проверяет контракт временного менеджера при ошибке corr-клиента: исключение передаётся
 * вызывающему действию машины, а повторный запрос автоматически не выполняется.
 */
class WorkflowManagerTest {
    /**
     * Ошибка транспорта выходит из вызова менеджера тем же типом исключения; клиент вызывается один
     * раз.
     */
    @Test
    void failureEscapesWithoutRetry() {
        InvestCorrClient client = mock(InvestCorrClient.class);
        when(client.submit(any(), any())).thenThrow(new InvestCorrCallException("TIMEOUT"));
        InvestCorrAdapter adapter = new InvestCorrAdapter(client, new InvestCorrMapperImpl());
        OperationContext input =
                new OperationContext(UUID.randomUUID(), "org", "user", "payment", null);
        assertThatThrownBy(() -> adapter.callOperation(Operation.STATUS, input))
                .isInstanceOf(InvestCorrCallException.class);
        verify(client).submit(any(), any());
    }
}
