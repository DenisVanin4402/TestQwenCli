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

/** Проверяет только переданные реквизиты относительно доверенного контекста. */
@Component
public class ContextConsistencyGuard implements Guard<SessionState, SessionEvent> {
    /** {@inheritDoc} */
    @Override
    public boolean evaluate(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        SessionTurnInDTO input = execution.getInput();
        TrustedPaymentContextDTO saved = snapshot.getContext();
        PaymentContextInDTO supplied = input.getContext();
        if (saved == null || supplied == null) return true;
        boolean matches =
                matches(supplied.getEpkId(), saved.getEpkId())
                        && matches(supplied.getDigitalUserId(), saved.getDigitalUserId())
                        && matches(supplied.getPaymentId(), saved.getPaymentId())
                        && matches(supplied.getPaymentNumber(), saved.getPaymentNumber())
                        && matches(supplied.getPaymentDate(), saved.getPaymentDate())
                        && (supplied.getAmount() == null
                                || supplied.getAmount().compareTo(saved.getAmount()) == 0)
                        && matches(supplied.getCurrency(), saved.getCurrency())
                        && matches(supplied.getRecipientName(), saved.getRecipientName())
                        && matches(supplied.getOrganizationName(), saved.getOrganizationName());
        return matches ? true : execution.reject(ResultCode.CONTEXT_MISMATCH);
    }

    /** Пропущенное поле не меняет контекст; переданное должно точно совпадать с сохранённым. */
    private boolean matches(Object supplied, Object saved) {
        return supplied == null || Objects.equals(supplied, saved);
    }
}
