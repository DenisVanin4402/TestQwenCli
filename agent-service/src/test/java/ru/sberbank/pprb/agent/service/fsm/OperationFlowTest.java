package ru.sberbank.pprb.agent.service.fsm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.action.*;
import ru.sberbank.pprb.agent.service.fsm.definition.OperationDefinition;
import ru.sberbank.pprb.agent.service.fsm.guard.common.*;

/** Реальные алгоритмы подготовки и сравнения используют одну декларативную схему. */
class OperationFlowTest {
    @Test
    /** Конструктор и builder сохраняют схему независимо от изменений исходного списка. */
    void keepsConfirmationSchemaImmutable() {
        var field =
                new ConfirmationFieldDTO(
                        "paymentNumber",
                        PaymentField.PAYMENT_NUMBER,
                        ConfirmationValueType.STRING,
                        "Номер платежа");
        var fields = new ArrayList<>(List.of(field));
        var constructed =
                new OperationSpecDTO(
                        Operation.STATUS, "status", "Статус", "Статус", "status.proposal", fields);
        var built =
                OperationSpecDTO.builder()
                        .operation(Operation.STATUS)
                        .actionCode("status")
                        .title("Статус")
                        .messageText("Статус")
                        .proposalTemplateKey("status.proposal")
                        .confirmationFields(fields)
                        .build();
        fields.clear();
        for (var spec : List.of(constructed, built)) {
            assertThat(spec.getConfirmationFields()).containsExactly(field);
            assertThatThrownBy(() -> spec.getConfirmationFields().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    /**
     * Одна схема формирует и проверяет снимок; дата строга, сумма сравнивается численно, новый
     * номер исключает старое согласие.
     */
    void preparesIndependentSnapshotAndUsesAlternativeSchema() {
        var fields =
                List.of(
                        new ConfirmationFieldDTO(
                                "paymentNumber",
                                PaymentField.PAYMENT_NUMBER,
                                ConfirmationValueType.STRING,
                                "Номер"),
                        new ConfirmationFieldDTO(
                                "paymentDate",
                                PaymentField.PAYMENT_DATE,
                                ConfirmationValueType.DATE,
                                "Дата"),
                        new ConfirmationFieldDTO(
                                "amount",
                                PaymentField.AMOUNT,
                                ConfirmationValueType.DECIMAL,
                                "Сумма"));
        var spec =
                new OperationSpecDTO(
                        Operation.STATUS, "status", "Статус", "Статус", "status.proposal", fields);
        var input = new SessionTurnInDTO();
        input.setRequestId(UUID.randomUUID());
        input.setContext(
                new PaymentContextInDTO(
                        "org",
                        "user",
                        "payment",
                        "0042",
                        LocalDate.of(2026, 10, 3),
                        new BigDecimal("12500.00"),
                        "RUB",
                        "Получатель",
                        "Организация"));
        var snapshot = new SessionSnapshotDTO();
        var prepare = new PrepareOperationAction(Mappers.getMapper(PreparationMapper.class));
        prepare.execute(context(snapshot, input, spec));
        assertThat(snapshot.getPreparation().getPreparedRequestId())
                .isEqualTo(input.getRequestId());
        input.getContext().setPaymentNumber("999");
        var confirmation = new SessionTurnInDTO();
        confirmation.setRequestId(UUID.randomUUID());
        confirmation.setConfirmation(
                new ArrayList<>(
                        List.of(
                                new ConfirmationValueDTO("amount", "12500.0"),
                                new ConfirmationValueDTO("preparationNo", "1"),
                                new ConfirmationValueDTO("paymentDate", "2026-10-03"),
                                new ConfirmationValueDTO("paymentNumber", "0042"))));
        var number = new PreparationNumberGuard();
        var data = new ConfirmationDataGuard();
        assertThat(rejection(number, snapshot, confirmation, spec)).isEmpty();
        assertThat(rejection(data, snapshot, confirmation, spec)).isEmpty();
        confirmation.getConfirmation().get(3).setValue("42");
        assertThat(rejection(data, snapshot, confirmation, spec))
                .contains(ResultCode.CONFIRMATION_MISMATCH);
        confirmation.getConfirmation().get(3).setValue("0042");
        confirmation.getConfirmation().get(2).setValue("2026-02-30");
        assertThat(rejection(data, snapshot, confirmation, spec))
                .contains(ResultCode.INVALID_CONFIRMATION);
        new CancelPreparationAction().execute(context(snapshot, input, null));
        prepare.execute(context(snapshot, input, spec));
        assertThat(snapshot.getPreparation().getPreparationNo()).isEqualTo(2);
        assertThat(rejection(number, snapshot, confirmation, spec))
                .contains(ResultCode.STALE_PREPARATION);
        snapshot.setLastRequestId(UUID.randomUUID());
        assertThat(rejection(new SeparateConfirmationGuard(), snapshot, input, spec))
                .contains(ResultCode.INVALID_COMMAND);
    }

    private StateContext<SessionState, SessionEvent> context(
            SessionSnapshotDTO snapshot, SessionTurnInDTO input, OperationSpecDTO spec) {
        var execution = new SessionExecutionContext(input);
        execution.snapshot = snapshot;
        if (spec != null)
            execution.operation =
                    new OperationDefinition(spec, c -> {}, c -> {}, List.of(), List.of());
        StateContext<SessionState, SessionEvent> context = mock(StateContext.class);
        when(context.getMessageHeader(SessionExecutionContext.HEADER)).thenReturn(execution);
        return context;
    }

    private Optional<ResultCode> rejection(
            Guard<SessionState, SessionEvent> guard,
            SessionSnapshotDTO snapshot,
            SessionTurnInDTO input,
            OperationSpecDTO spec) {
        var context = context(snapshot, input, spec);
        boolean accepted = guard.evaluate(context);
        var code = SessionExecutionContext.from(context).getRejectionCode();
        assertThat(accepted).isEqualTo(code == null);
        return Optional.ofNullable(code);
    }
}
