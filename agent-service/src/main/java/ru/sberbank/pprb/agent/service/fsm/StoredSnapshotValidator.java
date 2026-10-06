package ru.sberbank.pprb.agent.service.fsm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.dto.payment.*;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.port.in.OperationCatalog;

/**
 * Проверяет только целостность сохранённых данных. Новый вход и допуск перехода сюда не поступают.
 */
@Component
@RequiredArgsConstructor
public class StoredSnapshotValidator {
    private final OperationCatalog flow;

    /**
     * Требует согласованную структуру сохранённой подготовки, контекста, номера и схемы; дефект
     * означает ошибку хранения.
     */
    public void verify(
            TrustedPaymentContextDTO payment,
            PreparationDTO preparation,
            SessionState state,
            long number) {
        verifyPayment(payment);
        if (state == SessionState.CHOOSING_REQUEST_TYPE) {
            if (preparation != null)
                throw new IllegalStateException("Шаг выбора содержит подготовку");
            return;
        }
        if (preparation == null
                || preparation.getPreparationId() == null
                || preparation.getPreparedRequestId() == null
                || preparation.getPreparationNo() != number
                || preparation.getOperation() == null) {
            throw new IllegalStateException("Сохранённая подготовка повреждена");
        }
        verifyPayment(preparation.getContext());
        TrustedPaymentContextDTO copy = preparation.getContext();
        if (!Objects.equals(payment.getEpkId(), copy.getEpkId())
                || !Objects.equals(payment.getDigitalUserId(), copy.getDigitalUserId())
                || !Objects.equals(payment.getPaymentId(), copy.getPaymentId())
                || !Objects.equals(payment.getPaymentNumber(), copy.getPaymentNumber())
                || !Objects.equals(payment.getPaymentDate(), copy.getPaymentDate())
                || payment.getAmount().compareTo(copy.getAmount()) != 0
                || !Objects.equals(payment.getCurrency(), copy.getCurrency())
                || !Objects.equals(payment.getRecipientName(), copy.getRecipientName())
                || !Objects.equals(payment.getOrganizationName(), copy.getOrganizationName())) {
            throw new IllegalStateException("Сохранённые копии контекста расходятся");
        }
        boolean confirmed =
                preparation.getConfirmedRequestId() != null
                        && preparation.getConfirmedAt() != null
                        && !preparation
                                .getConfirmedRequestId()
                                .equals(preparation.getPreparedRequestId());
        boolean unconfirmed =
                preparation.getConfirmedRequestId() == null && preparation.getConfirmedAt() == null;
        if ((state == SessionState.COMPLETED && !confirmed)
                || (state == SessionState.AWAITING_CONFIRM && !unconfirmed)) {
            throw new IllegalStateException("Согласие не соответствует шагу FSM");
        }
        OperationSpecDTO spec =
                flow.operationSpec(preparation.getOperation())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Сохранённая операция не зарегистрирована"));
        if (preparation.getConfirmationSnapshot() == null)
            throw new IllegalStateException("Нет снимка подтверждения");
        Map<String, String> values = new HashMap<String, String>();
        for (ConfirmationValueDTO value : preparation.getConfirmationSnapshot()) {
            if (value == null
                    || value.getKey() == null
                    || value.getValue() == null
                    || value.getValue().isBlank()
                    || values.putIfAbsent(value.getKey(), value.getValue()) != null) {
                throw new IllegalStateException("Повреждён состав снимка подтверждения");
            }
        }
        if (!Long.toString(number).equals(values.remove(ConfirmationValueDTO.PREPARATION_NO))
                || values.size() != spec.getConfirmationFields().size())
            throw new IllegalStateException("Неверный номер или схема снимка");
        for (ConfirmationFieldDTO field : spec.getConfirmationFields()) {
            String value = values.remove(field.getKey());
            if (value == null) throw new IllegalStateException("Схема снимка изменилась");
            switch (field.getValueType()) {
                case DATE -> LocalDate.parse(value);
                case DECIMAL -> new BigDecimal(value);
                case STRING -> {}
            }
        }
        if (!values.isEmpty()) throw new IllegalStateException("Лишние поля снимка");
    }

    /** Проверяет обязательные поля сохранённого платежа; это не проверка нового metadata. */
    private void verifyPayment(TrustedPaymentContextDTO payment) {
        if (payment == null
                || payment.getPaymentDate() == null
                || payment.getAmount() == null
                || Stream.of(
                                payment.getEpkId(),
                                payment.getDigitalUserId(),
                                payment.getPaymentId(),
                                payment.getPaymentNumber(),
                                payment.getCurrency(),
                                payment.getRecipientName(),
                                payment.getOrganizationName())
                        .anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalStateException("Сохранённый платёж неполон");
        }
    }
}
