package ru.sberbank.pprb.agent.model.dto.turn;

import java.util.List;
import lombok.*;

/** Проверенная справка и метки использованных разделов; не сохраняется в сессии. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceAnswerDTO {
    private Boolean found;
    private String answer;
    private List<String> sourceIds;
}
