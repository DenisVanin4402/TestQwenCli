package ru.sberbank.pprb.agent.model.enums;

/** Семантика значения подтверждения. */
public enum ConfirmationValueType {
    /** Точное строковое сравнение, без нормализации номера. */
    STRING,
    /** Строгая календарная ISO-дата. */
    DATE,
    /** Численное сравнение десятичной суммы без учёта масштаба. */
    DECIMAL
}
