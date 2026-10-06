package ru.sberbank.pprb.agent.model.dto.payment;

import java.util.*;
import lombok.*;
import ru.sberbank.pprb.agent.model.dto.operation.*;
import ru.sberbank.pprb.agent.model.enums.*;

/** Параметр подготовленного представления ACL. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmationParameterDTO {
    /** Имя согласуемого параметра; повторы во входе сохраняются до проверки. */
    private String key;

    /** Строковое значение параметра; отсутствие во входе не заменяется значением по умолчанию. */
    private String value;

    /** Подпись поля для отображения, не участвует в сравнении согласия. */
    private String description;
}
