package ru.sberbank.pprb.agent.service.fsm;

import ru.sberbank.pprb.agent.model.enums.ResultCode;

/**
 * Ошибка доступа к сессии или обработки её машины состояний с кодом для оркестратора. Используется
 * при занятости, несоответствии исходных данных, сбое восстановления или действия; как
 * непроверяемое исключение приводит к откату текущей рабочей транзакции.
 */
public class SessionPersistenceException extends RuntimeException {
    /** Код причины, по которому оркестратор выбирает ответ, не раскрывая пользователю детали БД. */
    private final ResultCode code;

    /** Завершённость сохранённой сессии до неуспешного хода, если восстановление её установило. */
    private final boolean terminal;

    /** Создаёт отказ, понятный прикладному сценарию. */
    public SessionPersistenceException(ResultCode code, String message) {
        super(message);
        this.code = code;
        this.terminal = false;
    }

    /** Создаёт технический отказ с исходной причиной для диагностики. */
    public SessionPersistenceException(ResultCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.terminal = false;
    }

    /**
     * Добавляет установленную при восстановлении завершённость, сохраняя исходную причину отказа.
     */
    public SessionPersistenceException(SessionPersistenceException cause, boolean terminal) {
        super(cause.getMessage(), cause);
        this.code = cause.getCode();
        this.terminal = terminal;
    }

    /** Позволяет вернуть финальный отказ без повторного чтения FSM и без раскрытия её данных. */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Возвращает код причины для подготовки ответа оркестратором и выбора статуса входным
     * адаптером.
     */
    public ResultCode getCode() {
        return code;
    }
}
