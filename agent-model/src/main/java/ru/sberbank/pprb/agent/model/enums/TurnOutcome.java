package ru.sberbank.pprb.agent.model.enums;

/**
 * Предметные исходы обработки сообщения действием машины состояний или отказа в переходе.
 * Сохраняются вместе с состоянием сессии; оркестратор использует их для подготовки текста ответа.
 */
public enum TurnOutcome {
    /**
     * Черновик готов; для отправки требуется отдельное согласие пользователя на показанные данные.
     */
    CONFIRMATION_REQUIRED(ResultCode.CONFIRMATION_REQUIRED),
    /** Пользователь отказался от черновика; подготовка удалена и можно снова выбрать операцию. */
    PREPARATION_CANCELLED(ResultCode.PREPARATION_CANCELLED),
    /** Получен отказ от подготовки, но активного черновика в сессии нет. */
    NO_ACTIVE_PREPARATION(ResultCode.NO_ACTIVE_PREPARATION),
    /**
     * Подтверждённая операция передана менеджеру без ошибки; банковский результат этим не
     * подтверждается.
     */
    REQUEST_SUBMITTED(ResultCode.REQUEST_SUBMITTED),
    /** Сценарий уже завершён, поэтому новая команда не запускает ещё одну внешнюю операцию. */
    SESSION_COMPLETED(ResultCode.SESSION_COMPLETED),
    /** Полученная команда не разрешает переход на текущем шаге сценария. */
    INVALID_COMMAND(ResultCode.INVALID_COMMAND);

    /** Типизированная причина ответа, соответствующая этому сохраняемому исходу. */
    private final ResultCode code;

    /** Связывает исход машины с контрактом прикладного ответа. */
    TurnOutcome(ResultCode code) {
        this.code = code;
    }

    /** Возвращает причину, которую входной адаптер использует при построении своего ответа. */
    public ResultCode getCode() {
        return code;
    }
}
