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

/** Требует полный исходный платёж только при первом выборе операции. */
@Component
public class InitialContextGuard implements Guard<SessionState, SessionEvent> {
    /** {@inheritDoc} */
    @Override
    public boolean evaluate(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        SessionTurnInDTO input = execution.getInput();
        if (snapshot.getContext() != null) return true;
        PaymentContextInDTO context = input.getContext();
        if (context == null
                || context.getPaymentDate() == null
                || context.getAmount() == null
                || java.util.stream.Stream.of(
                                context.getEpkId(),
                                context.getDigitalUserId(),
                                context.getPaymentId(),
                                context.getPaymentNumber(),
                                context.getCurrency(),
                                context.getRecipientName(),
                                context.getOrganizationName())
                        .anyMatch(value -> value == null || value.isBlank())) {
            return execution.reject(ResultCode.INVALID_REQUEST);
        }
        return true;
    }
}
