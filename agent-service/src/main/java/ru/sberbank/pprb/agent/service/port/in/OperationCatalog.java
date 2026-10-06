package ru.sberbank.pprb.agent.service.port.in;

import java.util.*;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.enums.Operation;
import ru.sberbank.pprb.agent.model.enums.SessionState;

/** Читаемый каталог действий; не раскрывает исполняемые компоненты. */
public interface OperationCatalog {
    /** Все зарегистрированные операции, без утверждения об их доступности на текущем шаге. */
    List<OperationSpecDTO> registeredOperations();

    /** Возвращает схему сохранённой операции для представления ответа. */
    Optional<OperationSpecDTO> operationSpec(Operation operation);

    /** Разрешает action_code выбора без выполнения бизнес-проверок. */
    Optional<OperationSpecDTO> resolve(String actionCode);

    /**
     * Возвращает саджесты из SELECT-привязок данного шага; окончательный допуск проверяют guards.
     */
    List<OperationSpecDTO> choices(SessionState state);
}
