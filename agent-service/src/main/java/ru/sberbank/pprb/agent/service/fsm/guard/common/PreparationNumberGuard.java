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

/** Связывает согласие с активной подготовкой этой сессии по служебному номеру. */
@Component
public class PreparationNumberGuard implements Guard<SessionState, SessionEvent> {
    /** {@inheritDoc} */
    @Override
    public boolean evaluate(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        SessionTurnInDTO input = execution.getInput();
        List<ConfirmationValueDTO> values = input.getConfirmation();
        if (values == null) return execution.reject(ResultCode.INVALID_CONFIRMATION);
        String number = null;
        int count = 0;
        for (ConfirmationValueDTO value : values) {
            if (value != null && ConfirmationValueDTO.PREPARATION_NO.equals(value.getKey())) {
                count++;
                number = value.getValue();
            }
        }
        if (count != 1 || number == null || !number.matches("[0-9]+")) {
            return execution.reject(ResultCode.INVALID_CONFIRMATION);
        }
        try {
            long parsed = Long.parseLong(number);
            if (parsed <= 0) return execution.reject(ResultCode.INVALID_CONFIRMATION);
            return parsed == snapshot.getPreparation().getPreparationNo()
                    ? true
                    : execution.reject(ResultCode.STALE_PREPARATION);
        } catch (NumberFormatException exception) {
            return execution.reject(ResultCode.INVALID_CONFIRMATION);
        }
    }
}
