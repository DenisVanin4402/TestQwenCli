package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.generated.openapi.asyncapi.api.V1Api;
import com.example.testqwencli.generated.openapi.asyncapi.model.AsyncSubmitResponseDTO;
import com.example.testqwencli.generated.openapi.asyncapi.model.AsyncTaskDTO;
import com.example.testqwencli.generated.openapi.asyncapi.model.ExternalAsyncRequestDTO;
import com.example.testqwencli.gateway.controller.mapper.ExternalAsyncOpenApiMapper;
import com.example.testqwencli.gateway.services.ExternalAsyncService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExternalAsyncController implements V1Api {

	private final ExternalAsyncService externalAsyncService;

	private final ExternalAsyncOpenApiMapper mapper;

	ExternalAsyncController(ExternalAsyncService externalAsyncService, ExternalAsyncOpenApiMapper mapper) {
		this.externalAsyncService = externalAsyncService;
		this.mapper = mapper;
	}

	@Override
	public ResponseEntity<AsyncSubmitResponseDTO> submitExternalAsync(
			ExternalAsyncRequestDTO request,
			String requestId
	) {
		var response = externalAsyncService.submit(mapper.toDomain(request), requestId);
		return ResponseEntity.accepted().body(mapper.toOpenApi(response));
	}

	@Override
	public ResponseEntity<AsyncTaskDTO> getExternalAsyncTask(
			Long taskId,
			String requestId,
			String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return ResponseEntity.ok(mapper.toOpenApi(externalAsyncService.getByTaskId(taskId, clientService, requestId)));
	}

	@Override
	public ResponseEntity<AsyncTaskDTO> getExternalAsyncTaskByExternalId(
			UUID externalId,
			String requestId,
			String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return ResponseEntity.ok(mapper.toOpenApi(externalAsyncService.getByExternalId(externalId, clientService,
				requestId)));
	}

	@Override
	public ResponseEntity<AsyncTaskDTO> cancelExternalAsyncTask(
			Long taskId,
			String requestId,
			String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return ResponseEntity.ok(mapper.toOpenApi(externalAsyncService.cancel(taskId, clientService, requestId)));
	}

	@Override
	public ResponseEntity<AsyncTaskDTO> retryExternalAsyncTask(
			Long taskId,
			String requestId,
			String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return ResponseEntity.accepted().body(mapper.toOpenApi(externalAsyncService.retry(taskId, clientService,
				requestId)));
	}
}
