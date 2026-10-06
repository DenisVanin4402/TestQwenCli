package ru.sberbank.pprb.agent.model.entity.persistence.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Постоянная техническая запись для блокировки работы с одной сессией и проверки формата её
 * сохранённого состояния. Создаётся отдельной короткой транзакцией и остаётся после отката
 * обработки сообщения. Наличие записи не означает ни занятость сессии, ни наличие самой машины.
 */
@Entity
@Table(name = "agent_session")
@Getter
@Setter
@NoArgsConstructor
public class SessionEntity {
    /**
     * UUID сессии и уникальный ключ строки, которую блокирует транзакция перед чтением или
     * изменением машины состояний. Все сообщения одной сессии используют эту же запись.
     */
    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /**
     * Идентификатор поддерживаемого формата сохранённой машины. Движок проверяет его до
     * десериализации, чтобы не пытаться восстановить несовместимый снимок.
     */
    @Column(name = "format_id", nullable = false)
    private String formatId;
}
