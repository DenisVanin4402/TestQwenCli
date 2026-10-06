package ru.sberbank.pprb.agent.model.enums;

/** Источники подтверждаемых значений в снимке платежа. */
public enum PaymentField {
    /** Название организации плательщика. */
    ORGANIZATION_NAME,
    /** Строковый номер платежа с сохранением ведущих нулей. */
    PAYMENT_NUMBER,
    /** Календарная дата платежа. */
    PAYMENT_DATE,
    /** Название получателя. */
    RECIPIENT_NAME,
    /** Десятичная сумма без потери точности. */
    AMOUNT,
    /** Код валюты платежа. */
    CURRENCY
}
