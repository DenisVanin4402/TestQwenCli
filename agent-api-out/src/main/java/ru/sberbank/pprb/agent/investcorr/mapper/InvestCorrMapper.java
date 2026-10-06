package ru.sberbank.pprb.agent.investcorr.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.sberbank.pprb.agent.common.mapper.MappingConfig;
import ru.sberbank.pprb.agent.investcorr.generated.model.StatusRequest;
import ru.sberbank.pprb.agent.model.dto.operation.OperationContext;

/**
 * Преобразует снимок подтверждённой операции во временный HTTP-контракт corr. Используется
 * менеджером POC при отправке запроса статуса; данных из изменяемого черновика не перечитывает.
 */
@Mapper(config = MappingConfig.class)
public interface InvestCorrMapper {
    /**
     * Переносит принадлежность платежа и задаёт операцию запроса статуса. UUID подтверждённой
     * операции передаётся в существующем поле clientRequestId; отдельный идентификатор для того же
     * внешнего обращения не создаётся.
     */
    @Mapping(target = "operation", constant = "status")
    @Mapping(target = "clientRequestId", source = "operationId")
    StatusRequest toRequest(OperationContext context);
}
