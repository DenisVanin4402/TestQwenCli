package ru.sberbank.pprb.agent.investcorr.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.sberbank.pprb.agent.investcorr.client.InvestCorrClient;
import ru.sberbank.pprb.agent.investcorr.exception.InvestCorrCallException;
import ru.sberbank.pprb.agent.investcorr.generated.model.StatusResponse;
import ru.sberbank.pprb.agent.investcorr.mapper.InvestCorrMapper;
import ru.sberbank.pprb.agent.model.dto.operation.OperationContext;
import ru.sberbank.pprb.agent.model.enums.Operation;
import ru.sberbank.pprb.agent.service.port.out.WorkflowManager;

/**
 * Временная реализация менеджера внешних операций для POC. Преобразует подтверждённый запрос
 * статуса в один вызов corr и проверяет ответ о его принятии, не возвращая банковский результат в
 * сценарий. Ошибка вызова или ответа прерывает действие машины и откатывает локальную транзакцию.
 */
@Service
@RequiredArgsConstructor
public class InvestCorrAdapter implements WorkflowManager {
    /** Единственный клиент, выбранный конфигурацией: Feign или локальный стенд. */
    private final InvestCorrClient client;

    /** Преобразует снимок подтверждённой операции в запрос временного HTTP-контракта corr. */
    private final InvestCorrMapper mapper;

    /**
     * Передаёт один запрос статуса с идентификатором подтверждённой операции в качестве ключа.
     * Другие операции и изменения реквизитов отклоняются до вызова клиента. Успех означает
     * получение корректного подтверждения принятия запроса; повторов при ошибке нет.
     */
    @Override
    public void callOperation(Operation operation, OperationContext context) {
        if (operation != Operation.STATUS || context.getChanges() != null) {
            throw new IllegalArgumentException("В POC поддерживается только STATUS без changes");
        }
        StatusResponse response =
                client.submit(context.getOperationId().toString(), mapper.toRequest(context));
        if (response == null
                || !"ACCEPTED".equals(response.getOutcome())
                || response.getReference() == null
                || response.getReference().isBlank()) {
            throw new InvestCorrCallException("INVALID_OR_REJECTED_RESPONSE");
        }
    }
}
