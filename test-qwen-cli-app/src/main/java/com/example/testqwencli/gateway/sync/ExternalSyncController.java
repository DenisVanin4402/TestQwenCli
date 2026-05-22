package com.example.testqwencli.gateway.sync;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/external")
class ExternalSyncController {

	private final ExternalSyncService externalSyncService;

	ExternalSyncController(ExternalSyncService externalSyncService) {
		this.externalSyncService = externalSyncService;
	}

	@PostMapping("/sync")
	ExternalSyncResponse sync(
			@Valid @RequestBody ExternalSyncRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
	) {
		return externalSyncService.sync(request, new ExternalSyncHeaders(requestId, idempotencyKey));
	}
}
