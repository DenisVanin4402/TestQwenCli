package ru.sberbank.pprb.agent.model.dto.turn;

import java.util.List;
import java.util.UUID;
import lombok.*;
import ru.sberbank.pprb.agent.model.dto.operation.OperationSpecDTO;
import ru.sberbank.pprb.agent.model.dto.payment.ConfirmationParameterDTO;

/** Готовый ответ сценария после транзакции. Не сохраняется в native-состоянии машины. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnOutDTO {
    /** Идентификатор входящего сообщения для корреляции ответа. */
    private UUID requestId;

    /** Предметный код и подготовленный текст результата. */
    private ResultDTO result;

    /** Достоверно установленная завершённость, в том числе при контролируемом отказе. */
    private boolean terminal;

    /** Согласуемые значения с подписями из сохранённого предложения. */
    @Builder.Default private List<ConfirmationParameterDTO> confirmation = List.of();

    /** Доступные действия для саджестов ACL. */
    @Builder.Default private List<OperationSpecDTO> availableOperations = List.of();
}
