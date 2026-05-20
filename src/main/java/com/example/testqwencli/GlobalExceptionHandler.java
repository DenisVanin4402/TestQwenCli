package com.example.testqwencli;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Глобальный обработчик исключений.
 * Обеспечивает единый формат ошибок (AC-12, AC-13, AC-14) и валидацию UUID в URL.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * ValidationException → 400 (AC-2, AC-3, AC-4)
     */
    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ErrorEnvelope> handleValidation(ValidationException e) {
        log.warn("ValidationException: {}", e.getMessage());
        ErrorEnvelope envelope = new ErrorEnvelope("ValidationError", e.getMessage(), HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(envelope);
    }

    /**
     * ResourceNotFoundException → 404 (AC-7, AC-9, AC-11)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorEnvelope> handleNotFound(ResourceNotFoundException e) {
        log.warn("ResourceNotFoundException: {}", e.getMessage());
        ErrorEnvelope envelope = new ErrorEnvelope("NotFound", e.getMessage(), HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope);
    }

    /**
     * IllegalArgumentException → 400.
     * Обрабатывает ошибки парсинга UUID из URL (AC-12).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException e) {
        if (e.getMessage() != null && e.getMessage().contains("path variable")) {
            log.warn("Invalid path variable: {}", e.getMessage());
            ErrorEnvelope envelope = new ErrorEnvelope("BadParameter",
                    "Неверный формат UUID: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value());
            return ResponseEntity.badRequest().body(envelope);
        }
        log.warn("IllegalArgumentException: {}", e.getMessage());
        ErrorEnvelope envelope = new ErrorEnvelope("BadParameter",
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(envelope);
    }

    /**
     * MethodArgumentTypeMismatchException → 400.
     * Обрабатывает не-UUID значения в path variable (AC-12).
     * UUID conversion failure produces this exception.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        if (UUID.class.equals(e.getRequiredType())) {
            log.warn("Invalid UUID in path: {}", e.getValue());
            ErrorEnvelope envelope = new ErrorEnvelope("BadParameter",
                    "Неверный формат UUID: " + e.getName(),
                    HttpStatus.BAD_REQUEST.value());
            return ResponseEntity.badRequest().body(envelope);
        }
        log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
        ErrorEnvelope envelope = new ErrorEnvelope("BadParameter",
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(envelope);
    }

    /**
     * HttpMessageNotReadableException → 400.
     * Обрабатывает невалидный JSON (AC-14).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "BadRequest");
        body.put("message", "Тело запроса некорректно");
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Любое необработанное исключение → 500.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorEnvelope> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        ErrorEnvelope envelope = new ErrorEnvelope("InternalError",
                "Внутренняя ошибка сервера",
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(envelope);
    }
}
