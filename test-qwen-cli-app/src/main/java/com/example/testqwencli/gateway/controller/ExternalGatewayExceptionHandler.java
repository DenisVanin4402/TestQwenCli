package com.example.testqwencli.gateway.controller;

import com.example.testqwencli.gateway.exception.ExternalGatewayException;
import com.example.testqwencli.gateway.model.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.TreeMap;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(basePackages = "com.example.testqwencli.gateway")
public class ExternalGatewayExceptionHandler {

	private static final String REQUEST_ID_HEADER = "X-Request-Id";

	@ExceptionHandler(ExternalGatewayException.class)
	public ResponseEntity<ErrorResponse> handleGatewayException(ExternalGatewayException exception) {
		HttpHeaders headers = new HttpHeaders();
		if (exception.status() == HttpStatus.TOO_MANY_REQUESTS) {
			headers.set(HttpHeaders.RETRY_AFTER, "1");
		}
		ErrorResponse response = new ErrorResponse(exception.code(), exception.getMessage(), exception.retryable(),
				exception.requestId(), exception.details());
		return new ResponseEntity<>(response, headers, exception.status());
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ErrorResponse handleValidationException(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		return new ErrorResponse("VALIDATION_ERROR", "Запрос не прошел валидацию", false,
				request.getHeader(REQUEST_ID_HEADER), validationDetails(exception));
	}

	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ErrorResponse handleUnreadableMessage(HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return new ErrorResponse("INVALID_REQUEST", "Некорректный JSON или тип поля в запросе", false,
				request.getHeader(REQUEST_ID_HEADER), Map.of("reason", "Тело запроса не соответствует контракту"));
	}

	private Map<String, Object> validationDetails(MethodArgumentNotValidException exception) {
		Map<String, String> fields = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.collect(Collectors.toMap(FieldError::getField, this::fieldMessage, (left, right) -> left,
						TreeMap::new));
		return Map.of("fields", fields);
	}

	private String fieldMessage(FieldError error) {
		String generatedMessage = generatedRequestFieldMessage(error);
		if (generatedMessage != null) {
			return generatedMessage;
		}
		String message = error.getDefaultMessage();
		return message == null ? "Некорректное значение поля" : message;
	}

	private String generatedRequestFieldMessage(FieldError error) {
		String field = error.getField();
		String code = error.getCode();
		if ("NotNull".equals(code)) {
			return switch (field) {
				case "externalId" -> "externalId обязателен";
				case "payload" -> "payload обязателен";
				case "priority" -> "priority обязателен";
				case "clientService" -> "clientService обязателен";
				default -> null;
			};
		}
		if ("Size".equals(code) && "clientService".equals(field)) {
			if (error.getRejectedValue() instanceof String value && value.isBlank()) {
				return "clientService обязателен";
			}
			return "clientService должен содержать от 2 до 80 символов";
		}
		if ("Pattern".equals(code) && "clientService".equals(field)) {
			return "clientService обязателен";
		}
		return null;
	}
}
