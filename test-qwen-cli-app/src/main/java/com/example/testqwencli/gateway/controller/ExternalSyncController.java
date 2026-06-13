package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.generated.openapi.sync.api.V1Api;
import com.example.testqwencli.generated.openapi.sync.model.ExternalSyncRequestDTO;
import com.example.testqwencli.generated.openapi.sync.model.ExternalSyncResponseDTO;
import com.example.testqwencli.gateway.controller.mapper.ExternalSyncOpenApiMapper;
import com.example.testqwencli.gateway.model.sync.ExternalSyncHeaders;
import com.example.testqwencli.gateway.services.ExternalSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExternalSyncController implements V1Api {

	private final ExternalSyncService externalSyncService;

	private final ExternalSyncOpenApiMapper mapper;

	ExternalSyncController(ExternalSyncService externalSyncService, ExternalSyncOpenApiMapper mapper) {
		this.externalSyncService = externalSyncService;
		this.mapper = mapper;
	}

	@Override
	public ResponseEntity<ExternalSyncResponseDTO> callExternalSync(
			ExternalSyncRequestDTO request,
			String requestId,
			String idempotencyKey
	) {
		var response = externalSyncService.sync(mapper.toDomain(request),
				new ExternalSyncHeaders(requestId, idempotencyKey));
		return ResponseEntity.ok(mapper.toOpenApi(response));
	}
}
