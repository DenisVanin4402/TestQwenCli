package ru.sberbank.pprb.agent.service.fsm;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.sberbank.pprb.agent.common.mapper.MappingConfig;
import ru.sberbank.pprb.agent.model.dto.operation.OperationContext;
import ru.sberbank.pprb.agent.model.dto.payment.PreparationDTO;
import ru.sberbank.pprb.agent.model.dto.payment.TrustedPaymentContextDTO;

/**
 * Создаёт отдельный снимок показанного платежа и вход менеджера из подтверждённой подготовки. UUID,
 * номер и момент согласия определяет Action; mapper только сопоставляет именованные поля.
 */
@Mapper(config = MappingConfig.class)
public interface PreparationMapper {
    /** Структурная копия проверенного входного контекста. */
    TrustedPaymentContextDTO toTrusted(
            ru.sberbank.pprb.agent.model.dto.payment.PaymentContextInDTO context);

    /** Копирует реквизиты по именам, не разделяя изменяемый DTO между сессией и подготовкой. */
    @Named("copyPayment")
    TrustedPaymentContextDTO copyPayment(TrustedPaymentContextDTO context);

    /** Передаёт менеджеру UUID и привязку именно подтверждённой подготовки. */
    @Mapping(target = "operationId", source = "preparationId")
    @Mapping(target = "epkId", source = "context.epkId")
    @Mapping(target = "digitalUserId", source = "context.digitalUserId")
    @Mapping(target = "paymentId", source = "context.paymentId")
    @Mapping(target = "changes", ignore = true)
    OperationContext toOperation(PreparationDTO preparation);
}
