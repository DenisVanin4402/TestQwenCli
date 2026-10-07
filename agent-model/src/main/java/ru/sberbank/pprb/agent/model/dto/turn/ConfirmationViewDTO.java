package ru.sberbank.pprb.agent.model.dto.turn;

import java.util.List;
import lombok.*;

/** Представление подтверждения после транзакции, не сохраняемое в FSM. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationViewDTO {
    private String title;
    private List<ConfirmationDisplayFieldDTO> fields;
    private String question;
}
