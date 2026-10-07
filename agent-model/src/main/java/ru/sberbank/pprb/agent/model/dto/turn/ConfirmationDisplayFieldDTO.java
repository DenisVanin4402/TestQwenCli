package ru.sberbank.pprb.agent.model.dto.turn;

import lombok.*;

/** Готовая подпись и текст реквизита; не параметр согласия. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationDisplayFieldDTO {
    private String label;
    private String value;
}
