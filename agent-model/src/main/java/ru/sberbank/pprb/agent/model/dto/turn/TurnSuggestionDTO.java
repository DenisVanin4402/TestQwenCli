package ru.sberbank.pprb.agent.model.dto.turn;

import lombok.Value;
import ru.sberbank.pprb.agent.model.enums.SuggestionKind;

/** Готовое действие текущего ответа; код нужен только для команды. */
@Value
public class TurnSuggestionDTO {
    String text;
    String actionCode;
    SuggestionKind kind;
}
