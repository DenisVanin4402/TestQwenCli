package com.example.testqwencli;

import java.time.Instant;

/**
 * DTO — унифицированный формат ошибок.
 * {"error", "message", "status", "timestamp"}
 */
class ErrorEnvelope {

    private final String error;
    private final String message;
    private final int status;
    private final String timestamp;

    ErrorEnvelope(String error, String message, int status) {
        this.error = error;
        this.message = message;
        this.status = status;
        this.timestamp = Instant.now().toString();
    }

    String getError() {
        return error;
    }

    String getMessage() {
        return message;
    }

    int getStatus() {
        return status;
    }

    String getTimestamp() {
        return timestamp;
    }
}
