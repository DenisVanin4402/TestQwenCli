package ru.sberbank.pprb.agent.investcorr.config;

/**
 * Закрытый набор сценариев локального corr. Spring Boot связывает значения свойства
 * accepted-response-lost и остальные имена в kebab-case с соответствующими константами.
 */
public enum StubScenario {
    /** Запрос принят без ошибки. */
    ACCEPTED,
    /** Стенд явно отказал в принятии. */
    REJECTED,
    /** Истекло ожидание ответа; результат операции неизвестен. */
    TIMEOUT,
    /** Стенд выполнил операцию, но ответ не дошёл до агента. */
    ACCEPTED_RESPONSE_LOST
}
