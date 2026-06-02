package com.example.testqwencli.gateway.exception;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class ExternalGatewayException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final boolean retryable;
	private final String requestId;
	private final Map<String, Object> details;

	public ExternalGatewayException(
			HttpStatus status,
			String code,
			String message,
			boolean retryable,
			String requestId,
			Map<String, Object> details
	) {
		super(message);
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.code = Objects.requireNonNull(code, "code must not be null");
		this.retryable = retryable;
		this.requestId = requestId;
		this.details = details == null ? null : Map.copyOf(details);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public boolean retryable() {
		return retryable;
	}

	public String requestId() {
		return requestId;
	}

	public Map<String, Object> details() {
		return details;
	}
}
