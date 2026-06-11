package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.gateway.model.sync.ExternalSyncHeaders;
import com.example.testqwencli.gateway.model.sync.ExternalSyncRequest;
import com.example.testqwencli.gateway.model.sync.ExternalSyncResponse;
import com.example.testqwencli.gateway.model.error.ErrorResponse;
import com.example.testqwencli.gateway.services.ExternalSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/external")
@Tag(name = "Sync", description = "Синхронные вызовы внешнего сервиса")
class ExternalSyncController {

	private final ExternalSyncService externalSyncService;

	ExternalSyncController(ExternalSyncService externalSyncService) {
		this.externalSyncService = externalSyncService;
	}

	@PostMapping("/sync")
	@Operation(
			operationId = "callExternalSync",
			summary = "Выполнить синхронный вызов внешнего сервиса"
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Внешний сервис успешно обработал запрос",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ExternalSyncResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Некорректный запрос",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			),
			@ApiResponse(
					responseCode = "429",
					description = "Gateway не смог получить слот в рамках sync SLA",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			),
			@ApiResponse(
					responseCode = "503",
					description = "Внешний сервис или gateway временно недоступен",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			),
			@ApiResponse(
					responseCode = "504",
					description = "Истекло время ожидания ответа внешнего сервиса",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			)
	})
	ExternalSyncResponse sync(
			@Valid @RequestBody ExternalSyncRequest request,
			@Parameter(
					name = "X-Request-Id",
					in = ParameterIn.HEADER,
					description = "Correlation id от вызывающего сервиса",
					schema = @Schema(maxLength = 128)
			)
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Parameter(
					name = "Idempotency-Key",
					in = ParameterIn.HEADER,
					description = "Ключ идемпотентности для upstream adapter'а",
					schema = @Schema(minLength = 8, maxLength = 128)
			)
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
	) {
		return externalSyncService.sync(request, new ExternalSyncHeaders(requestId, idempotencyKey));
	}
}
