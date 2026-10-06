package ru.sberbank.pprb.agent.service.port.in;

import java.util.Optional;
import java.util.UUID;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;

/** Чтение предметного снимка без обработки события и внешнего эффекта. */
public interface SessionQueryUseCase {
    /** Возвращает согласованный снимок существующей сессии после транзакционного чтения. */
    Optional<SessionSnapshotDTO> find(UUID sessionId);
}
