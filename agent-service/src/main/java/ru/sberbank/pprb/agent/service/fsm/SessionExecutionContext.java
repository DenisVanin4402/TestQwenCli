package ru.sberbank.pprb.agent.service.fsm;

import lombok.Getter;
import org.springframework.statemachine.StateContext;
import ru.sberbank.pprb.agent.model.dto.session.SessionSnapshotDTO;
import ru.sberbank.pprb.agent.model.dto.turn.SessionTurnInDTO;
import ru.sberbank.pprb.agent.model.enums.*;
import ru.sberbank.pprb.agent.service.fsm.definition.OperationDefinition;

/** Данные одного синхронного события. Передаются в message header и не сохраняются в FSM. */
@Getter
public final class SessionExecutionContext {
    /** Технический ключ данных текущего события; не ключ сохраняемого ExtendedState. */
    static final String HEADER = "sessionExecution";

    /** Нормализованный вход текущего сообщения. */
    private final SessionTurnInDTO input;

    /** Рабочие предметные данные этой машины после восстановления. */
    SessionSnapshotDTO snapshot;

    /** Компоненты выбранной или ранее подготовленной операции. */
    OperationDefinition operation;

    /** Причина первого штатного отказа guard. */
    ResultCode rejectionCode;

    /** Ошибка callback, которую SSM могла перехватить. */
    Throwable error;

    /** Все проверки перехода разрешили исполнение действия. */
    boolean admitted;

    /** Action вернулся успешно, его результат разрешено сохранять. */
    boolean actionCompleted;

    /** Завершённость до события; не теряется при rollback. */
    boolean restoredTerminal;

    /** Создаёт данные одного сообщения; снимок устанавливается после native restore. */
    SessionExecutionContext(SessionTurnInDTO input) {
        this.input = input;
    }

    /** Получает данные текущего сообщения без singleton-состояния в guards/actions. */
    public static SessionExecutionContext from(StateContext<SessionState, SessionEvent> context) {
        return (SessionExecutionContext) context.getMessageHeader(HEADER);
    }

    /** Сохраняет предметную причину первого отказа и возвращает штатный результат guard. */
    public boolean reject(ResultCode code) {
        if (rejectionCode == null) rejectionCode = code;
        return false;
    }
}
