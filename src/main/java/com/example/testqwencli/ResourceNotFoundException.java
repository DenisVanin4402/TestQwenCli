package com.example.testqwencli;

/**
 * Исключение для ошибок «задача не найдена» (404).
 */
class ResourceNotFoundException extends RuntimeException {

    ResourceNotFoundException(String message) {
        super(message);
    }
}
