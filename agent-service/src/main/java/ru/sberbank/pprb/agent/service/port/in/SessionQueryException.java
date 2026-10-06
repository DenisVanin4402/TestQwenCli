package ru.sberbank.pprb.agent.service.port.in;

import ru.sberbank.pprb.agent.model.enums.ResultCode;

/**
 * Сообщает входному адаптеру, почему сохранённую сессию нельзя прочитать. Отсутствие сессии
 * возвращается отдельно через Optional; ошибка не подменяет его пустым результатом.
 */
public class SessionQueryException extends RuntimeException {
    /** Код отказа чтения, по которому адаптер выбирает ответ своего канала. */
    private final ResultCode code;

    /** Сохраняет причину для диагностики, не раскрывая входному API типы DB-адаптера. */
    public SessionQueryException(ResultCode code, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
    }

    /** Возвращает SESSION_BUSY для занятого семафора либо STORAGE_UNAVAILABLE для ошибки чтения. */
    public ResultCode getCode() {
        return code;
    }
}
