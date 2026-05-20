package com.example.testqwencli;

import java.time.Instant;
import java.util.UUID;

/**
 * Модель задачи (Task).
 * Поля: id, title, description, status, createdAt, updatedAt.
 */
class Task {

    private final UUID id;
    private String title;
    private String description;
    private TaskStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Фабричный метод: создать новую задачу с автогенерацией UUID
     * и дефолтным статусом TODO.
     */
    static Task create(String title, String description) {
        return new Task(UUID.randomUUID(), title, description, TaskStatus.TODO);
    }

    /**
     * Копирование с новым updatedAt (для возврата при обновлении/удалении).
     */
    Task copy() {
        Task copy = new Task(this.id, this.title, this.description, this.status);
        copy.updatedAt = this.updatedAt;
        return copy;
    }

    // Приватный конструктор
    private Task(UUID id, String title, String description, TaskStatus status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // --- Геттеры ---

    UUID getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    TaskStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    // --- Сеттеры (для обновления) ---

    void setTitle(String title) {
        this.title = title;
    }

    void setDescription(String description) {
        this.description = description;
    }

    void setStatus(TaskStatus status) {
        this.status = status;
    }

    void touch() {
        this.updatedAt = Instant.now();
    }
}
