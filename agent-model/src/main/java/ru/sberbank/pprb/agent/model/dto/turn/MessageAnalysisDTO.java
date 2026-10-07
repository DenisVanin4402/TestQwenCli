package ru.sberbank.pprb.agent.model.dto.turn;

import lombok.*;
import ru.sberbank.pprb.agent.model.enums.MessageRoute;

/** Маршрут текста и код операции только для единственной команды. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageAnalysisDTO {
    /** Классификация запроса; не событие машины. */
    private MessageRoute route;

    /** Код выбора операции; не событие машины и не согласие на отправку. */
    private String actionCode;
}
