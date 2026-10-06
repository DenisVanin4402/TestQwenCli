package ru.sberbank.pprb.agent.model.dto.operation;

import java.util.*;
import lombok.*;
import ru.sberbank.pprb.agent.model.enums.*;

/** Декларация подтверждаемого поля, без исполняемого алгоритма. */
@Value
@AllArgsConstructor
@Builder
public class ConfirmationFieldDTO {
    /** Имя согласуемого параметра; повторы во входе сохраняются до проверки. */
    String key;

    /** Явный источник значения в снимке платежа, без reflection. */
    PaymentField source;

    /** Правило разбора и сравнения значения. */
    ConfirmationValueType valueType;

    /** Подпись поля для отображения, не участвует в сравнении согласия. */
    String description;
}
