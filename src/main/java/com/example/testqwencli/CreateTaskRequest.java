package com.example.testqwencli;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO — тело запроса POST /api/v1/tasks.
 * title — обязательное, description — опциональное, status — опциональное.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class CreateTaskRequest {

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
