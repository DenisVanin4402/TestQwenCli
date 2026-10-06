package ru.sberbank.pprb.agent.service.fsm.definition;

import java.util.*;
import lombok.Getter;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.guard.Guard;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;
import ru.sberbank.pprb.agent.model.enums.SessionState;

/** Неизменяемая регистрация схемы и обработчиков операции. */
@Getter
public final class OperationDefinition {
    /** Неизменяемые данные схемы и представления операции. */
    private final OperationSpecDTO spec;

    /** Алгоритм создания предложения этой операции. */
    private final Action<SessionState, SessionEvent> prepareAction;

    /** Алгоритм исполнения после допуска подтверждения. */
    private final Action<SessionState, SessionEvent> executeAction;

    /** Дополнительные проверки подготовки в порядке регистрации. */
    private final List<Guard<SessionState, SessionEvent>> prepareGuards;

    /** Дополнительные проверки исполнения после общих guards. */
    private final List<Guard<SessionState, SessionEvent>> executeGuards;

    /** Сохраняет неизменяемую схему, ссылки на обработчики и неизменяемые копии списков guards. */
    public OperationDefinition(
            OperationSpecDTO spec,
            Action<SessionState, SessionEvent> prepareAction,
            Action<SessionState, SessionEvent> executeAction,
            List<Guard<SessionState, SessionEvent>> prepareGuards,
            List<Guard<SessionState, SessionEvent>> executeGuards) {
        this.spec = Objects.requireNonNull(spec);
        this.prepareAction = Objects.requireNonNull(prepareAction);
        this.executeAction = Objects.requireNonNull(executeAction);
        this.prepareGuards = List.copyOf(prepareGuards);
        this.executeGuards = List.copyOf(executeGuards);
    }
}
