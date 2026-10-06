package ru.sberbank.pprb.agent.common.acl;

import java.util.Set;

/**
 * Имена полей усечённого ACL-профиля, общие для входного адаптера и локальных примеров. Это
 * транспортный контракт: переименование Java-поля не должно менять значения этих ключей.
 */
public final class AclProfile {
    /** Версия входящего и исходящего ACL-сообщения первого этапа. */
    public static final String VERSION = "1.6";

    /** Организация в нормализованной карте реквизитов. */
    public static final String EPK_ID = "epkId";

    /** Пользователь в нормализованной карте реквизитов. */
    public static final String DIGITAL_USER_ID = "digitalUserId";

    /** Идентификатор платежа в additional_info. */
    public static final String PAYMENT_ID = "paymentId";

    /** Номер платежа в additional_info. */
    public static final String PAYMENT_NUMBER = "paymentNumber";

    /** ISO-дата платежа в additional_info. */
    public static final String PAYMENT_DATE = "paymentDate";

    /** Десятичная сумма платежа в additional_info. */
    public static final String AMOUNT = "amount";

    /** Код валюты в additional_info. */
    public static final String CURRENCY = "currency";

    /** Название получателя в additional_info. */
    public static final String RECIPIENT_NAME = "recipientName";

    /** Название плательщика в additional_info. */
    public static final String ORGANIZATION_NAME = "organizationName";

    /** Только эти параметры additional_info относятся к платежу текущего профиля. */
    public static final Set<String> PAYMENT_KEYS =
            Set.of(
                    PAYMENT_ID,
                    PAYMENT_NUMBER,
                    PAYMENT_DATE,
                    AMOUNT,
                    CURRENCY,
                    RECIPIENT_NAME,
                    ORGANIZATION_NAME);

    /** Класс задаёт контракт и не имеет изменяемого состояния. */
    private AclProfile() {}
}
