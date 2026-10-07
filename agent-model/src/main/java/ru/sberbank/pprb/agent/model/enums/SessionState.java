package ru.sberbank.pprb.agent.model.enums;

/**
 * Шаги обращения за статусом платежа. Состояние определяет, может ли пользователь подготовить
 * запрос, подтвердить показанный черновик или должен начать новую сессию.
 */
public enum SessionState {
    /** Активного черновика нет; пользователь может выбрать запрос статуса и начать подготовку. */
    CHOOSING_REQUEST_TYPE(SessionStateCategory.INITIAL),
    /**
     * Черновик подготовлен и показан пользователю; отправка ожидает отдельного согласия на него.
     */
    AWAITING_CONFIRM(SessionStateCategory.INTERMEDIATE),
    /**
     * Подтверждённая операция успешно передана менеджеру, и результат сохранён. Сценарий завершён;
     * для новой операции нужна новая сессия. Банковский результат в этом сценарии не ожидается.
     */
    COMPLETED(SessionStateCategory.TERMINAL);

    private final SessionStateCategory category;

    SessionState(SessionStateCategory category) {
        this.category = category;
    }

    /** Явная категория каждого шага для общей навигации. */
    public SessionStateCategory getCategory() {
        return category;
    }
}
