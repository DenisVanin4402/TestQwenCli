package ru.sberbank.pprb.agent.model.dto.turn;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.sberbank.pprb.agent.model.dto.payment.ConfirmationValueDTO;
import ru.sberbank.pprb.agent.model.dto.payment.PaymentContextInDTO;
import ru.sberbank.pprb.agent.model.enums.Operation;
import ru.sberbank.pprb.agent.model.enums.SessionEvent;

/** Вход общего сценария, независимый от ACL и каталога примеров. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class SessionTurnInDTO {
    /** Идентификатор сессии. */
    private UUID sessionId;

    /** Корреляция пользовательского хода. */
    private UUID requestId;

    /** Общее намерение пользователя. */
    private SessionEvent event;

    /** Явно переданные значения исходного платежа. */
    private PaymentContextInDTO context;

    /** Ошибка входного адаптера, запрещающая обработку. */
    private ResultDTO validationError;

    /** Выбор операции только для SELECT. */
    private Operation operation;

    /** Пары согласия с сохранением дублей и пустых значений. */
    private List<ConfirmationValueDTO> confirmation;

    /** Текущий текст клиента, если вход не содержит явного действия. */
    private String userInput;
}
