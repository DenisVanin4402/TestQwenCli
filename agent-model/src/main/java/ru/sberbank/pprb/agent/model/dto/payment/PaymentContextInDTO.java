package ru.sberbank.pprb.agent.model.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Явно переданные поля контекста; null означает отсутствие значения. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentContextInDTO {
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
