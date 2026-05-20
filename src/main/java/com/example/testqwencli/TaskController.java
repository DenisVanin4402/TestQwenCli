package com.example.testqwencli;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST контроллер для управления задачами.
 * Endpoints: POST, GET list, GET by ID, PUT, DELETE /api/v1/tasks
 */
@RestController
@RequestMapping("/api/v1/tasks")
class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    private static final int MAX_TITLE_LENGTH = 255;

    private final TaskRepository repository;

    TaskController(TaskRepository repository) {
        this.repository = repository;
    }

    /**
     * POST /api/v1/tasks — создание новой задачи.
     * Возвращает 201 при успехе, 400 при ошибке валидации.
     */
    @PostMapping
    ResponseEntity<TaskResponse> createTask(@RequestBody CreateTaskRequest request) {
        // Валидация title (AC-2, AC-3)
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            log.warn("POST /api/v1/tasks: title пустой или отсутствует");
            throw new ValidationException("Title не может быть пустым");
        }
        if (request.getTitle().length() > MAX_TITLE_LENGTH) {
            log.warn("POST /api/v1/tasks: title длиннее {} символов", MAX_TITLE_LENGTH);
            throw new ValidationException("Title не может быть длиннее " + MAX_TITLE_LENGTH + " символов");
        }

        // Валидация status (AC-4)
        TaskStatus status = TaskStatus.TODO;
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                status = TaskStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("POST /api/v1/tasks: неизвестный status '{}'", request.getStatus());
                throw new ValidationException("Неизвестный статус: " + request.getStatus());
            }
        }

        // Создание задачи
        Task task = Task.create(request.getTitle(), request.getDescription());
        task.setStatus(status);
        repository.save(task);
        log.info("POST /api/v1/tasks: создана задача {}", task.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TaskResponse(task));
    }

    /**
     * GET /api/v1/tasks — получить список всех задач.
     * Возвращает 200 и массив (пустой, если задач нет).
     */
    @GetMapping
    ResponseEntity<List<TaskResponse>> listTasks() {
        log.info("GET /api/v1/tasks: получено {} задач", repository.findAll().size());
        List<TaskResponse> responses = repository.findAll().stream()
                .map(TaskResponse::new)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/v1/tasks/{id} — получить задачу по ID.
     * Возвращает 200 или 404.
     */
    @GetMapping("/{id}")
    ResponseEntity<TaskResponse> getTaskById(@PathVariable UUID id) {
        Task task = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("GET /api/v1/tasks/{}: задача не найдена", id);
                    return new ResourceNotFoundException("Задача не найдена: " + id);
                });
        log.info("GET /api/v1/tasks/{}: задача найдена", id);
        return ResponseEntity.ok(new TaskResponse(task));
    }

    /**
     * PUT /api/v1/tasks/{id} — полное обновление задачи.
     * Возвращает 200 с обновлённой задачей (createdAt неизменен) или 404.
     */
    @PutMapping("/{id}")
    ResponseEntity<TaskResponse> updateTask(@PathVariable UUID id, @RequestBody UpdateTaskRequest request) {
        // Валидация title (AC-8, AC-9)
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            log.warn("PUT /api/v1/tasks/{}: title пустой или отсутствует", id);
            throw new ValidationException("Title не может быть пустым");
        }
        if (request.getTitle().length() > MAX_TITLE_LENGTH) {
            log.warn("PUT /api/v1/tasks/{}: title длиннее {} символов", id, MAX_TITLE_LENGTH);
            throw new ValidationException("Title не может быть длиннее " + MAX_TITLE_LENGTH + " символов");
        }

        // Валидация status
        TaskStatus status;
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            try {
                status = TaskStatus.valueOf(request.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("PUT /api/v1/tasks/{}: неизвестный status '{}'", id, request.getStatus());
                throw new ValidationException("Неизвестный статус: " + request.getStatus());
            }
        } else {
            log.warn("PUT /api/v1/tasks/{}: status пустой или отсутствует", id);
            throw new ValidationException("Status не может быть пустым");
        }

        Task task = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("PUT /api/v1/tasks/{}: задача не найдена", id);
                    return new ResourceNotFoundException("Задача не найдена: " + id);
                });

        // Обновление полей
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(status);
        task.touch();
        repository.save(task);

        log.info("PUT /api/v1/tasks/{}: задача обновлена", id);
        return ResponseEntity.ok(new TaskResponse(task));
    }

    /**
     * DELETE /api/v1/tasks/{id} — удаление задачи.
     * Возвращает 200 с JSON удалённой задачи или 404.
     */
    @DeleteMapping("/{id}")
    ResponseEntity<TaskResponse> deleteTask(@PathVariable UUID id) {
        Task task = repository.deleteById(id)
                .orElseThrow(() -> {
                    log.warn("DELETE /api/v1/tasks/{}: задача не найдена", id);
                    return new ResourceNotFoundException("Задача не найдена: " + id);
                });

        log.info("DELETE /api/v1/tasks/{}: задача удалена", id);
        return ResponseEntity.ok(new TaskResponse(task));
    }
}
