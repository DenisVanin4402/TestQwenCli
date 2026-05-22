package com.example.testqwencli.gateway.sync.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
		String message = error.getDefaultMessage();
		return message == null ? "Некорректное значение поля" : message;
	}
}
