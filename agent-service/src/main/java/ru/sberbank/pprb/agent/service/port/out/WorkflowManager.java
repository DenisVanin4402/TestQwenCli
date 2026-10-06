package ru.sberbank.pprb.agent.service.port.out;

import ru.sberbank.pprb.agent.model.dto.operation.OperationContext;
import ru.sberbank.pprb.agent.model.enums.Operation;

/**
 * Граница передачи подтверждённой операции внешнему исполнителю. Вызывается из действия машины
 * состояний и принимает снимок согласованных данных; банковский результат обратно в сценарий не
 * передаёт. В POC реализация выполняет один запрос статуса через corr.
 */
public interface WorkflowManager {
    /**
     * Синхронно передаёт операцию и подтверждённые данные внешнему исполнителю без автоматического
     * повтора. Ошибка передаётся в рабочую транзакцию машины и вызывает откат локальных изменений;
     * уже выполненный внешний эффект таким откатом не отменяется.
     */
    void callOperation(Operation operation, OperationContext context);
}
