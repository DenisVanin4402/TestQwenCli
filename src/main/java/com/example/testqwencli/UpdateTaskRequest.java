package com.example.testqwencli;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO — тело запроса PUT /api/v1/tasks/{id}.
 * Полное обновление: title, description, status обязательны для передачи.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class UpdateTaskRequest {

    private String title;
    private String description;
    private String status;

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    String getStatus() {
        return status;
    }
}
