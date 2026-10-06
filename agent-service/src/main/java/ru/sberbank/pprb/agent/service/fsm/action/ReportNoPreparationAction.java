package ru.sberbank.pprb.agent.service.fsm.action;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.SessionExecutionContext;

/** Сообщает об отсутствии подготовки в уже созданной бизнес-сессии. */
@Component
@RequiredArgsConstructor
public class ReportNoPreparationAction implements Action<SessionState, SessionEvent> {

    /** {@inheritDoc} */
    @Override
    public void execute(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        snapshot.setLastResult(TurnOutcome.NO_ACTIVE_PREPARATION);
    }
}
