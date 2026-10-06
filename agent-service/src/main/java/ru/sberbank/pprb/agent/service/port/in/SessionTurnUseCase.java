package ru.sberbank.pprb.agent.service.port.in;

import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.dto.turn.TurnOutDTO;

/** Общий пользовательский ход для локального ACL и будущей поверхности GA. */
public interface SessionTurnUseCase {
    /**
     * Обрабатывает намерение на сохранённом состоянии без зависимости от транспорта.
     *
     * @param input данные пользовательского сообщения
     * @param idempotencyKey отдельный ключ будущей библиотеки в формате sessionId:requestId; оба
     *     UUID записаны канонически. До подключения библиотеки повтор входа не подавляется.
     */
    TurnOutDTO handle(SessionTurnInDTO input, String idempotencyKey);
}
