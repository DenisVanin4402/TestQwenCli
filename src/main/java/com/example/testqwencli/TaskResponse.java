package com.example.testqwencli;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO — сериализуемый ответ TaskResponse с ISO-8601 timestamp.
 * Преобразует Task → JSON-совместимый ответ.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class TaskResponse {

    private final String id;
    private final String title;
    private final String description;
    private final String status;
    private final String createdAt;
    private final String updatedAt;

    TaskResponse(Task task) {
        this.id = task.getId().toString();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getStatus().name();
        this.createdAt = task.getCreatedAt().toString();
        this.updatedAt = task.getUpdatedAt().toString();
    }

    String getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    String getStatus() {
        return status;
    }

    String getCreatedAt() {
        return createdAt;
    }

    String getUpdatedAt() {
        return updatedAt;
    }
}
