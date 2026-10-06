package ru.sberbank.pprb.agent.model.dto.payment;

import java.util.*;
import lombok.*;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.enums.*;

/** Сохранённая или полученная пара подтверждения; входные дубли не теряются. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmationValueDTO {
    /** Общий ключ идентичности предложения внутри сессии. */
    public static final String PREPARATION_NO = "preparationNo";

    /** Имя согласуемого параметра; повторы во входе сохраняются до проверки. */
    private String key;

    /** Строковое значение параметра; отсутствие во входе не заменяется значением по умолчанию. */
    private String value;
}
