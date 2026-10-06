package ru.sberbank.pprb.agent.service.fsm.guard.common;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Проверяет точный набор и значения согласия по схеме и сохранённому предложению. */
@Component
public class ConfirmationDataGuard implements Guard<SessionState, SessionEvent> {
    /** {@inheritDoc} */
    @Override
    public boolean evaluate(StateContext<SessionState, SessionEvent> stateContext) {
        SessionExecutionContext execution = SessionExecutionContext.from(stateContext);
        SessionSnapshotDTO snapshot = execution.getSnapshot();
        SessionTurnInDTO input = execution.getInput();
        OperationSpecDTO spec =
                execution.getOperation() == null ? null : execution.getOperation().getSpec();
        if (input.getConfirmation() == null)
            return execution.reject(ResultCode.INVALID_CONFIRMATION);
        Map<String, String> supplied = new HashMap<>();
        for (ConfirmationValueDTO parameter : input.getConfirmation()) {
            if (parameter == null
                    || parameter.getKey() == null
                    || parameter.getValue() == null
                    || parameter.getValue().isBlank()
                    || supplied.putIfAbsent(parameter.getKey(), parameter.getValue()) != null) {
                return execution.reject(ResultCode.INVALID_CONFIRMATION);
            }
        }
        supplied.remove(ConfirmationValueDTO.PREPARATION_NO);
        if (supplied.size() != spec.getConfirmationFields().size()
                || !supplied.keySet()
                        .equals(
                                spec.getConfirmationFields().stream()
                                        .map(ConfirmationFieldDTO::getKey)
                                        .collect(java.util.stream.Collectors.toSet()))) {
            return execution.reject(ResultCode.INVALID_CONFIRMATION);
        }
        Map<String, String> expected = new HashMap<>();
        snapshot.getPreparation()
                .getConfirmationSnapshot()
                .forEach(value -> expected.put(value.getKey(), value.getValue()));
        // Сначала проверяется формат всего набора, чтобы ошибка формы не зависела от порядка полей.
        Map<String, Object> parsed = new HashMap<>();
        try {
            for (ConfirmationFieldDTO field : spec.getConfirmationFields()) {
                parsed.put(
                        field.getKey(), parse(supplied.get(field.getKey()), field.getValueType()));
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return execution.reject(ResultCode.INVALID_CONFIRMATION);
        }
        for (ConfirmationFieldDTO field : spec.getConfirmationFields()) {
            Object left = parsed.get(field.getKey());
            Object right = parse(expected.get(field.getKey()), field.getValueType());
            boolean matches =
                    left instanceof BigDecimal decimal
                            ? decimal.compareTo((BigDecimal) right) == 0
                            : left.equals(right);
            if (!matches) return execution.reject(ResultCode.CONFIRMATION_MISMATCH);
        }
        return true;
    }

    /**
     * Разбирает одно значение по семантике поля; неверный формат входа превращается в
     * типизированный отказ вызывающим check.
     */
    private Object parse(String value, ConfirmationValueType type) {
        return switch (type) {
            case STRING -> value;
            case DATE -> LocalDate.parse(value, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            case DECIMAL -> new BigDecimal(value);
        };
    }
}
