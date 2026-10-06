package ru.sberbank.pprb.agent.common.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Общие правила преобразования внешних API-моделей во внутренние данные приложения и обратно.
 * MapStruct создаёт преобразователи как Spring-компоненты; забытое поле результата останавливает
 * компиляцию, чтобы изменение контракта не приводило к незаметной потере данных.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MappingConfig {}
