package ru.sberbank.pprb.agent.service.fsm.action;

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

/** Создаёт подготовку и независимый снимок согласуемых значений по декларативной схеме. */
@Component
@RequiredArgsConstructor
public class PrepareOperationAction implements Action<SessionState, SessionEvent> {
    private final PreparationMapper mapper;

    /** {@inheritDoc} */
    @Override
    public void execute(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        SessionTurnInDTO input = execution.getInput();
        OperationSpecDTO spec =
                execution.getOperation() == null ? null : execution.getOperation().getSpec();
        if (snapshot.getContext() == null)
            snapshot.setContext(mapper.toTrusted(input.getContext()));
        long number = Math.incrementExact(snapshot.getLastPreparationNo());
        TrustedPaymentContextDTO payment = mapper.copyPayment(snapshot.getContext());
        List<ConfirmationValueDTO> values = new ArrayList<ConfirmationValueDTO>();
        for (ConfirmationFieldDTO field : spec.getConfirmationFields()) {
            values.add(new ConfirmationValueDTO(field.getKey(), value(payment, field.getSource())));
        }
        values.add(
                new ConfirmationValueDTO(
                        ConfirmationValueDTO.PREPARATION_NO, Long.toString(number)));
        snapshot.setPreparation(
                PreparationDTO.builder()
                        .preparationId(UUID.randomUUID())
                        .preparationNo(number)
                        .preparedRequestId(input.getRequestId())
                        .operation(spec.getOperation())
                        .context(payment)
                        .confirmationSnapshot(values)
                        .build());
        snapshot.setLastPreparationNo(number);
        snapshot.setLastResult(TurnOutcome.CONFIRMATION_REQUIRED);
    }

    /** Извлечение и форматирование принадлежат action, а не конфигурации или mapper. */
    private String value(TrustedPaymentContextDTO payment, PaymentField source) {
        return switch (source) {
            case ORGANIZATION_NAME -> payment.getOrganizationName();
            case PAYMENT_NUMBER -> payment.getPaymentNumber();
            case PAYMENT_DATE -> payment.getPaymentDate().toString();
            case RECIPIENT_NAME -> payment.getRecipientName();
            case AMOUNT -> payment.getAmount().toPlainString();
            case CURRENCY -> payment.getCurrency();
        };
    }
}
