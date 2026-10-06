package ru.sberbank.pprb.agent.model.dto.operation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Изменения реквизитов для будущей операции уточнения платежа. В контекст операции включаются
 * только изменяемые реквизиты: null означает отсутствие изменения, а пустая строка не означает
 * очистку значения. В запросе статуса эта модель не используется.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailsChangesDTO {
    /** Новый ИНН получателя; null означает, что ИНН менять не требуется. */
    private String recipientInn;

    /** Новый номер счёта получателя; null означает, что счёт менять не требуется. */
    private String recipientAccount;

    /** Новое наименование получателя; null означает, что наименование менять не требуется. */
    private String recipientName;

    /** Новый текст назначения платежа; null означает, что назначение менять не требуется. */
    private String paymentPurpose;
}
