package ru.sberbank.pprb.agent.db.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import ru.sberbank.pprb.agent.model.entity.persistence.session.SessionEntity;

/**
 * Читает постоянную техническую строку сессии и блокирует её до доступа к машине состояний. Одна
 * блокировка защищает шаг сценария и все предметные данные, сохранённые штатным механизмом Spring
 * Statemachine; освобождается при завершении транзакции.
 */
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    /**
     * Получает исключительную блокировку строки перед обработкой сообщения. На PostgreSQL
     * используется FOR UPDATE NOWAIT: занятая строка вызывает ошибку без ожидания её освобождения.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select s from SessionEntity s where s.sessionId = :id")
    Optional<SessionEntity> getAndLock(@Param("id") UUID id);

    /**
     * Получает блокировку чтения перед восстановлением снимка для GET-запроса. На PostgreSQL
     * используется FOR SHARE NOWAIT: параллельная запись исключается до завершения чтения, а уже
     * занятая писателем строка вызывает ошибку.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select s from SessionEntity s where s.sessionId = :id")
    Optional<SessionEntity> getForRead(@Param("id") UUID id);
}
