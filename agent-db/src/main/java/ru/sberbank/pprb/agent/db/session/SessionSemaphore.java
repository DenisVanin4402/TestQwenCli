package ru.sberbank.pprb.agent.db.session;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sberbank.pprb.agent.db.repository.SessionRepository;
import ru.sberbank.pprb.agent.model.entity.persistence.session.SessionEntity;

/**
 * Регистрирует постоянную строку блокировки отдельно от обработки сообщения пользователя. После
 * отката рабочей транзакции строка остаётся, даже если машина состояний ещё ни разу не была
 * сохранена. Само наличие строки не означает, что сессия занята.
 */
@Component
@RequiredArgsConstructor
public class SessionSemaphore {
    /**
     * Идентификатор поддерживаемого формата сценария и его сериализованных данных. Записывается при
     * регистрации сессии и проверяется перед восстановлением сохранённой машины.
     */
    public static final String FORMAT_ID = "status-v3";

    /** Хранилище постоянных строк сессий с уникальным ключом по идентификатору сессии. */
    private final SessionRepository sessions;

    /**
     * Создаёт отсутствующую строку и фиксирует её отдельной транзакцией до возврата вызывающему
     * коду. Конкурентную вставку ограничивает уникальный ключ; её конфликт обрабатывается
     * вызывающим кодом после отката этой транзакции. Существующая строка и её формат не изменяются.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensure(UUID sessionId) {
        if (!sessions.existsById(sessionId)) {
            SessionEntity session = new SessionEntity();
            session.setSessionId(sessionId);
            session.setFormatId(FORMAT_ID);
            sessions.saveAndFlush(session);
        }
    }
}
