package com.example.testqwencli.gateway.controller.mapper;

import com.example.testqwencli.generated.openapi.asyncapi.model.AsyncSubmitResponseDTO;
import com.example.testqwencli.generated.openapi.asyncapi.model.AsyncTaskDTO;
import com.example.testqwencli.generated.openapi.asyncapi.model.ExternalAsyncDeliveryModeDTO;
import com.example.testqwencli.generated.openapi.asyncapi.model.ExternalAsyncRequestDTO;
import com.example.testqwencli.generated.openapi.asyncapi.model.ExternalAsyncResponseDeliveryModeDTO;
import com.example.testqwencli.generated.openapi.asyncapi.model.TaskErrorDTO;
import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.async.TaskError;
import com.example.testqwencli.gateway.model.async.enums.AsyncDeliveryMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Преобразует generated async DTO внешнего HTTP-контракта в доменные модели gateway.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ExternalAsyncOpenApiMapper {

	ExternalAsyncRequest toDomain(ExternalAsyncRequestDTO source);

	AsyncSubmitResponseDTO toOpenApi(AsyncSubmitResponse source);

	AsyncTaskDTO toOpenApi(AsyncTask source);

	TaskErrorDTO toOpenApi(TaskError source);

	default AsyncDeliveryMode toDomain(ExternalAsyncDeliveryModeDTO deliveryMode) {
		if (deliveryMode == null) {
			return null;
		}
		return AsyncDeliveryMode.valueOf(deliveryMode.name());
	}

	default ExternalAsyncResponseDeliveryModeDTO toOpenApi(AsyncDeliveryMode deliveryMode) {
		if (deliveryMode == null) {
			return null;
		}
		if (deliveryMode == AsyncDeliveryMode.SYNC) {
			throw new IllegalArgumentException("deliveryMode=SYNC не публикуется через внешний async API");
		}
		return ExternalAsyncResponseDeliveryModeDTO.fromValue(deliveryMode.name());
	}

	default OffsetDateTime toOpenApi(Instant instant) {
		if (instant == null) {
			return null;
		}
		return instant.atOffset(ZoneOffset.UTC);
	}
}
