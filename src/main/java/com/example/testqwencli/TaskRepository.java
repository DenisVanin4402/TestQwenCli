package com.example.testqwencli;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory хранилище задач, потокобезопасное (ConcurrentHashMap).
 */
@Component
class TaskRepository {

    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

    /**
     * Сохранить задачу (создание или обновление).
     */
    Task save(Task task) {
        tasks.put(task.getId(), task);
        return task;
    }

    /**
     * Найти задачу по ID. Возвращает Optional.empty(), если не найдена.
     */
    Optional<Task> findById(UUID id) {
        return Optional.ofNullable(tasks.get(id));
    }

    /**
     * Получить всё список задач.
     */
    List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    /**
     * Удалить задачу по ID. Возвращает Optional.empty(), если не найдена.
     */
    Optional<Task> deleteById(UUID id) {
        return Optional.ofNullable(tasks.remove(id));
    }
}
