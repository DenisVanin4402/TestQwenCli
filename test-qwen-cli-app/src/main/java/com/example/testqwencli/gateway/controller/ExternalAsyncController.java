package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.gateway.model.async.AsyncSubmitResponse;
import com.example.testqwencli.gateway.model.async.AsyncTask;
import com.example.testqwencli.gateway.model.async.ExternalAsyncRequest;
import com.example.testqwencli.gateway.services.ExternalAsyncService;
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
class ExternalAsyncController {

	private final ExternalAsyncService externalAsyncService;

	ExternalAsyncController(ExternalAsyncService externalAsyncService) {
		this.externalAsyncService = externalAsyncService;
	}

	@PostMapping
	ResponseEntity<AsyncSubmitResponse> submit(
			@Valid @RequestBody ExternalAsyncRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId
	) {
		return ResponseEntity.accepted().body(externalAsyncService.submit(request, requestId));
	}

	@GetMapping("/{taskId}")
	AsyncTask getByTaskId(
			@PathVariable long taskId,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return externalAsyncService.getByTaskId(taskId, clientService, requestId);
	}

	@GetMapping("/by-external-id/{externalId}")
	AsyncTask getByExternalId(
			@PathVariable UUID externalId,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return externalAsyncService.getByExternalId(externalId, clientService, requestId);
	}

	@DeleteMapping("/{taskId}")
	AsyncTask cancel(
			@PathVariable long taskId,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return externalAsyncService.cancel(taskId, clientService, requestId);
	}

	@PostMapping("/{taskId}/retry")
	ResponseEntity<AsyncTask> retry(
			@PathVariable long taskId,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "X-Client-Service", required = false) String clientService
	) {
		// TODO: заменить X-Client-Service на реальную service-to-service identity после внедрения аутентификации.
		return ResponseEntity.accepted().body(externalAsyncService.retry(taskId, clientService, requestId));
	}
}
