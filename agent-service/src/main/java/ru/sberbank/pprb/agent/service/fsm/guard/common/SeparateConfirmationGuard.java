package ru.sberbank.pprb.agent.service.fsm.guard.common;

import java.util.*;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.SessionExecutionContext;

/** Согласие должно быть другим сообщением, чем создание конкретной подготовки. */
@Component
public class SeparateConfirmationGuard implements Guard<SessionState, SessionEvent> {
    /** {@inheritDoc} */
    @Override
    public boolean evaluate(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        SessionTurnInDTO input = execution.getInput();
        return input.getRequestId() == null
                        || input.getRequestId()
                                .equals(snapshot.getPreparation().getPreparedRequestId())
                ? execution.reject(ResultCode.INVALID_COMMAND)
                : true;
    }
}
