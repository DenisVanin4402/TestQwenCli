package ru.sberbank.pprb.agent.model.dto.operation;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Снимок данных подтверждённой операции, который действие машины состояний передаёт менеджеру
 * внешних операций. Менеджер использует эти данные для вызова банковского сервиса; последующие
 * изменения черновика не должны менять уже подтверждённую операцию.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationContext {
    /**
     * UUID одной подтверждённой внешней операции, совпадающий с идентификатором её подготовки.
     * Передаётся в запрос corr и его ключ идемпотентности; это не номер попытки вызова.
     */
    private UUID operationId;

    /** Идентификатор организации, которой принадлежит платёж из подтверждённой подготовки. */
    private String epkId;

    /** Идентификатор пользователя, от имени которого выполняется подтверждённая операция. */
    private String digitalUserId;

    /** Идентификатор платежа, для которого пользователь подтвердил внешнюю операцию. */
    private String paymentId;

    /**
     * Подтверждённые изменения реквизитов для будущей операции уточнения платежа. При запросе
     * статуса значение отсутствует; наличие поля не включает уточнение реквизитов в сценарий POC.
     */
    private PaymentDetailsChangesDTO changes;
}
