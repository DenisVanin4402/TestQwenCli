package ru.sberbank.pprb.agent.service.port.out;

import ru.sberbank.pprb.agent.model.dto.turn.ReferenceAnswerDTO;

/** Отвечает по знаниям без сессии, истории, банковского контекста и побочных действий. */
public interface ReferenceAnswerProvider {
    ReferenceAnswerDTO answer(String question);
}
