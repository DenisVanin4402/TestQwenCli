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

/** Отбрасывает предложение, сохраняя монотонный счётчик подготовок. */
@Component
@RequiredArgsConstructor
public class CancelPreparationAction implements Action<SessionState, SessionEvent> {

    /** {@inheritDoc} */
    @Override
    public void execute(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        snapshot.setPreparation(null);
        snapshot.setLastResult(TurnOutcome.PREPARATION_CANCELLED);
    }
}
