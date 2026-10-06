package ru.sberbank.pprb.agent.model.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Полный снимок синтетических реквизитов платежа, организации и пользователя, сохранённый при
 * первом выборе запроса статуса. Форма и полнота значений проверены; следующие сообщения сверяются
 * с этим снимком и не заменяют исходные данные. В локальном профиле такая проверка не подтверждает
 * банковские полномочия или принадлежность реального платежа.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedPaymentContextDTO {
    /** Идентификатор организации. */
    private String epkId;

    /** Идентификатор пользователя. */
    private String digitalUserId;

    /** Идентификатор исходного платежа. */
    private String paymentId;

    /** Номер исходного платежа. */
    private String paymentNumber;

    /** Дата исходного платежа. */
    private LocalDate paymentDate;

    /** Сумма исходного платежа. */
    private BigDecimal amount;

    /** Код валюты исходного платежа. */
    private String currency;

    /** Название получателя. */
    private String recipientName;

    /** Название организации плательщика. */
    private String organizationName;
}
