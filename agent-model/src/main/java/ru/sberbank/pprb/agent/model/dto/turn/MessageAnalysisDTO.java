package ru.sberbank.pprb.agent.model.dto.turn;

import lombok.*;

/** Результат разбора: зарегистрированный код операции либо null, если команда не распознана. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageAnalysisDTO {
    /** Код выбора операции; не событие машины и не согласие на отправку. */
    private String actionCode;
}
