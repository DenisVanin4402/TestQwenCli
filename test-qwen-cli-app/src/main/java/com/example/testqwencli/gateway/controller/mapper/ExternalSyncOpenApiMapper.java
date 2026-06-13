package com.example.testqwencli.gateway.controller.mapper;

import com.example.testqwencli.generated.openapi.sync.model.ExternalSyncRequestDTO;
import com.example.testqwencli.generated.openapi.sync.model.ExternalSyncResponseDTO;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import com.example.testqwencli.gateway.model.sync.ExternalSyncResponse;
import com.example.testqwencli.gateway.model.sync.enums.ExternalSyncStatus;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Преобразует generated sync DTO внешнего HTTP-контракта в доменные модели gateway.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExternalSyncOpenApiMapper {

	ExternalSyncRequest toDomain(ExternalSyncRequestDTO source);

	ExternalSyncResponseDTO toOpenApi(ExternalSyncResponse source);

	default ExternalSyncResponseDTO.StatusEnum toOpenApi(ExternalSyncStatus status) {
		if (status == null) {
			return null;
		}
		return ExternalSyncResponseDTO.StatusEnum.fromValue(status.name());
	}
}
