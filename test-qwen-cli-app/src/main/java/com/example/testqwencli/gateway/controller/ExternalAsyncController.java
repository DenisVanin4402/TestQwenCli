package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.model.error.ErrorResponse;
import com.example.testqwencli.gateway.services.ExternalAsyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/external/async")
@Tag(name = "Async", description = "Асинхронные задачи внешнего сервиса")
class ExternalAsyncController {

	private final ExternalAsyncService externalAsyncService;

	ExternalAsyncController(ExternalAsyncService externalAsyncService) {
		this.externalAsyncService = externalAsyncService;
	}

	@PostMapping
	@Operation(
			operationId = "submitExternalAsync",
			summary = "Поставить async-задачу на обработку"
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "202",
					description = "Задача принята или уже существовала по idempotency key",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = AsyncSubmitResponse.class))
			),
			@ApiResponse(
					responseCode = "400",
					description = "Некорректный запрос",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			),
			@ApiResponse(
					responseCode = "409",
					description = "Конфликт idempotency key с другим payload",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			)
	})
	ResponseEntity<AsyncSubmitResponse> submit(
			@Valid @RequestBody ExternalAsyncRequest request,
			@Parameter(
					name = "X-Request-Id",
					in = ParameterIn.HEADER,
					description = "Correlation id от вызывающего сервиса",
					schema = @Schema(maxLength = 128)
			)
			@RequestHeader(value = "X-Request-Id", required = false) String requestId
	) {
		return ResponseEntity.accepted().body(externalAsyncService.submit(request, requestId));
	}

	@GetMapping("/{taskId}")
	@Operation(
			operationId = "getExternalAsyncTask",
			summary = "Получить состояние async-задачи"
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Текущее состояние задачи",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = AsyncTask.class))
			),
			@ApiResponse(
					responseCode = "404",
					description = "Задача не найдена",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			)
	})
	AsyncTask getByTaskId(
			@Parameter(
					description = "Внутренний id async-задачи",
					schema = @Schema(type = "integer", format = "int64", minimum = "1")
			)
			@PathVariable long taskId,
			@Parameter(
					name = "X-Request-Id",
					in = ParameterIn.HEADER,
					description = "Correlation id от вызывающего сервиса",
					schema = @Schema(maxLength = 128)
			)
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Parameter(
					name = "X-Client-Service",
					in = ParameterIn.HEADER,
					description = "Временный scope доступа до внедрения service-to-service identity",
					schema = @Schema(minLength = 2, maxLength = 80)
			)
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return externalAsyncService.getByTaskId(taskId, clientService, requestId);
	}

	@GetMapping("/by-external-id/{externalId}")
	@Operation(
			operationId = "getExternalAsyncTaskByExternalId",
			summary = "Получить async-задачу по externalId"
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Текущее состояние задачи",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = AsyncTask.class))
			),
			@ApiResponse(
					responseCode = "404",
					description = "Задача не найдена",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			)
	})
	AsyncTask getByExternalId(
			@Parameter(description = "Идентификатор задачи на стороне вызывающего сервиса")
			@PathVariable UUID externalId,
			@Parameter(
					name = "X-Request-Id",
					in = ParameterIn.HEADER,
					description = "Correlation id от вызывающего сервиса",
					schema = @Schema(maxLength = 128)
			)
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Parameter(
					name = "X-Client-Service",
					in = ParameterIn.HEADER,
					description = "Временный scope доступа до внедрения service-to-service identity",
					schema = @Schema(minLength = 2, maxLength = 80)
			)
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return externalAsyncService.getByExternalId(externalId, clientService, requestId);
	}

	@DeleteMapping("/{taskId}")
	@Operation(
			operationId = "cancelExternalAsyncTask",
			summary = "Отменить async-задачу, если она еще не выполняется"
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Задача отменена или уже была в финальном статусе",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = AsyncTask.class))
			),
			@ApiResponse(
					responseCode = "404",
					description = "Задача не найдена",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			),
			@ApiResponse(
					responseCode = "409",
					description = "Задачу нельзя отменить в текущем статусе",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			)
	})
	AsyncTask cancel(
			@Parameter(
					description = "Внутренний id async-задачи",
					schema = @Schema(type = "integer", format = "int64", minimum = "1")
			)
			@PathVariable long taskId,
			@Parameter(
					name = "X-Request-Id",
					in = ParameterIn.HEADER,
					description = "Correlation id от вызывающего сервиса",
					schema = @Schema(maxLength = 128)
			)
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Parameter(
					name = "X-Client-Service",
					in = ParameterIn.HEADER,
					description = "Временный scope доступа до внедрения service-to-service identity",
					schema = @Schema(minLength = 2, maxLength = 80)
			)
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return externalAsyncService.cancel(taskId, clientService, requestId);
	}

	@PostMapping("/{taskId}/retry")
	@Operation(
			operationId = "retryExternalAsyncTask",
			summary = "Вернуть задачу в обработку вручную"
	)
	@ApiResponses({
			@ApiResponse(
					responseCode = "202",
					description = "Задача возвращена в очередь",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = AsyncTask.class))
			),
			@ApiResponse(
					responseCode = "404",
					description = "Задача не найдена",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			),
			@ApiResponse(
					responseCode = "409",
					description = "Задачу нельзя повторить в текущем статусе",
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = ErrorResponse.class))
			)
	})
	ResponseEntity<AsyncTask> retry(
			@Parameter(
					description = "Внутренний id async-задачи",
					schema = @Schema(type = "integer", format = "int64", minimum = "1")
			)
			@PathVariable long taskId,
			@Parameter(
					name = "X-Request-Id",
					in = ParameterIn.HEADER,
					description = "Correlation id от вызывающего сервиса",
					schema = @Schema(maxLength = 128)
			)
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@Parameter(
					name = "X-Client-Service",
					in = ParameterIn.HEADER,
					description = "Временный scope доступа до внедрения service-to-service identity",
					schema = @Schema(minLength = 2, maxLength = 80)
			)
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return ResponseEntity.accepted().body(externalAsyncService.retry(taskId, clientService, requestId));
	}
}
