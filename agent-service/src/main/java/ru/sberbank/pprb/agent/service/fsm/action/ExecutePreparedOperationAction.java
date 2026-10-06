package ru.sberbank.pprb.agent.service.fsm.action;

import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.PreparationMapper;
import ru.sberbank.pprb.agent.service.fsm.SessionExecutionContext;
import ru.sberbank.pprb.agent.service.port.out.WorkflowManager;

/** Фиксирует согласие и один раз исполняет операцию с сохранёнными серверными данными. */
@Component
@RequiredArgsConstructor
public class ExecutePreparedOperationAction implements Action<SessionState, SessionEvent> {
    private final PreparationMapper mapper;
    private final WorkflowManager workflow;

    /** {@inheritDoc} */
    @Override
    public void execute(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        SessionTurnInDTO input = execution.getInput();
        PreparationDTO preparation = snapshot.getPreparation();
        preparation.setConfirmedRequestId(input.getRequestId());
        preparation.setConfirmedAt(Instant.now());
        workflow.callOperation(preparation.getOperation(), mapper.toOperation(preparation));
        snapshot.setLastResult(TurnOutcome.REQUEST_SUBMITTED);
    }
}
