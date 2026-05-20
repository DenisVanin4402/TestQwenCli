package com.example.testqwencli;

/**
 * Исключение для ошибок валидации входных данных.
 */
class ValidationException extends RuntimeException {

    ValidationException(String message) {
        super(message);
    }
}
