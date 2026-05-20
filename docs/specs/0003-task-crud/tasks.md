# План задач: REST API для управления задачами

## Задачи

### TSK-1: Модель задачи Task (домен)

**REQ:** REQ-1, REQ-8  
**Описание:** Создать класс-модель `Task` с полями: `id` (UUID), `title` (String), `description` (String, nullable), `status` (enum: TODO, IN_PROGRESS, DONE), `createdAt` (Instant), `updatedAt` (Instant). Добавить фабричный метод для создания новой задачи с автогенерацией UUID и дефолтным статусом TODO.  
**Вход:** Спецификация `spec.md` с описанием полей  
**Выход:** Файлы `Task.java` + `TaskStatus.java` — скомпилированные классы  
**Зависимости:** None  
**Оценка сложности:** S

---

### TSK-2: In-memory хранилище задач (TaskRepository)

**REQ:** REQ-8  
**Описание:** Реализовать компонент `TaskRepository` с in-memory хранилищем (`ConcurrentHashMap`) и методами: `save()`, `findById()`, `findAll()`, `deleteById()`. Обеспечить потокобезопасность.  
**Вход:** Модель `Task` из TSK-1  
**Выход:** Файл `TaskRepository.java` — скомпилированный компонент, готовый к dependency injection  
**Зависимости:** TSK-1  
**Оценка сложности:** S

---

### TSK-3: DTO для запросов/ответов и ErrorEnvelope

**REQ:** REQ-1, REQ-4, REQ-6, REQ-7  
**Описание:** Создать DTO-классы:
- `CreateTaskRequest` — body для POST (title, description)
- `UpdateTaskRequest` — body для PUT (title, description, status)
- `TaskResponse` — сериализуемый ответ с ISO-8601 timestamp
- `ErrorEnvelope` — унифицированный формат ошибок: `{error, message, status, timestamp}`

**Вход:** Спецификация полей и форматов из `spec.md` (AC-1…AC-14)  
**Выход:** Файлы `CreateTaskRequest.java`, `UpdateTaskRequest.java`, `TaskResponse.java`, `ErrorEnvelope.java`  
**Зависимости:** TSK-1  
**Оценка сложности:** S

---

### TSK-4: TaskController — создание и чтение (POST + GET list)

**REQ:** REQ-1, REQ-2, REQ-6  
**Описание:** Реализовать `TaskController` с двумя эндпоинтами:
- `POST /api/v1/tasks` — создание задачи (AC-1..AC-4): валидация title (non-null, non-empty, ≤255), status (enum). Возврат 201.
- `GET /api/v1/tasks` — список всех задач (AC-5). Возврат 200 + массив (пустой, если задач нет).

**Вход:** TSK-1 (Task), TSK-2 (TaskRepository), TSK-3 (DTO)  
**Выход:** `TaskController.java` с двумя рабочими эндпоинтами, скомпилированный  
**Зависимости:** TSK-1, TSK-2, TSK-3  
**Оценка сложности:** M

---

### TSK-5: TaskController — получение по ID, обновление, удаление

**REQ:** REQ-3, REQ-4, REQ-5  
**Описание:** Добавить в `TaskController` три эндпоинта:
- `GET /api/v1/tasks/{id}` — по ID (AC-6, AC-7): 200 или 404
- `PUT /api/v1/tasks/{id}` — полное обновление (AC-8, AC-9): 200 с обновлённым updatedAt, createdAt неизменен. Валидация как в POST.
- `DELETE /api/v1/tasks/{id}` — удаление (AC-10, AC-11): 200 с body удалённой задачи или 404

**Вход:** TSK-1, TSK-2, TSK-3, TSK-4  
**Выход:** `TaskController.java` обновлённый с пятью эндпоинтами, скомпилированный  
**Зависимости:** TSK-1, TSK-2, TSK-3, TSK-4  
**Оценка сложности:** M

---

### TSK-6: UUID validation и GlobalExceptionHandler

**REQ:** REQ-7, REQ-6  
**Описание:** Реализовать обработку двух уровней ошибок:
- Валидация UUID-формата в URL (AC-12): не-UUID → 400 с ErrorEnvelope
- `@ControllerAdvice` с `@ExceptionHandler`: MethodArgumentNotValidException → 400, ResourceNotFoundException → 404, IllegalArgumentException → 400. Все ответы — JSON с ErrorEnvelope.

**Вход:** TSK-3 (ErrorEnvelope), TSK-4/TSK-5 (контроллер)  
**Выход:** Файлы `GlobalExceptionHandler.java` (+ опционально `ResourceNotFoundException.java`). Все эндпоинты возвращают единообразные ошибки  
**Зависимости:** TSK-1, TSK-2, TSK-3, TSK-4, TSK-5  
**Оценка сложности:** M

---

### TSK-7: Интеграционные тесты (MockMvc) — полный набор AC

**REQ:** REQ-1..REQ-8  
**Описание:** Создать `TaskControllerIntegrationTest` с MockMvc-тестами, покрывающими все AC:
- AC-1: POST 201 → id, title, status TODO, createdAt/updatedAt
- AC-2..AC-4: POST validation (no title, empty title, title >255, unknown status) → 400
- AC-5: GET list → 200, пустой массив
- AC-6, AC-7: GET by ID → 200 / 404
- AC-8, AC-9: PUT → 200 (createdAt unchanged) / 404
- AC-10, AC-11: DELETE → 200 (body) / 404
- AC-12: non-UUID in path → 400
- AC-13, AC-14: Content-Type и ErrorEnvelope на всех ошибках

**Вход:** Полная реализация TSK-1..TSK-6  
**Выход:** `TaskControllerIntegrationTest.java` — все тесты проходят (`mvn test`)  
**Зависимости:** TSK-1, TSK-2, TSK-3, TSK-4, TSK-5, TSK-6  
**Оценка сложности:** L

---

## Граф зависимостей

```
TSK-1 (Task модель)
  │
  ├─→ TSK-2 (TaskRepository)
  │      │
  ├─→ TSK-3 (DTO + ErrorEnvelope)
  │      │
  │      ├────────────────────┐
  │      ↓                    ↓
  │   TSK-4 (POST + GET list) 
  │      │
  │      ↓
  │   TSK-5 (GET by ID, PUT, DELETE)
  │      │
  │      ↓
  │   TSK-6 (UUID validation + GlobalExceptionHandler)
  │      │
  │      ↓
  └──→ TSK-7 (Интеграционные тесты — все AC)
```

**Параллельные задачи:**
- **TSK-2 и TSK-3** — могут выполняться параллельно после TSK-1. TSK-2 работает с хранилищем, TSK-3 — с DTO. Они независимы.

## Владение и блокировки

| Задача | Ответственный | Статус | Блокеры |
|--------|---------------|--------|---------|
| TSK-1 | implementer | planned | none |
| TSK-2 | implementer | planned | TSK-1 |
| TSK-3 | implementer | planned | TSK-1 |
| TSK-4 | implementer | planned | TSK-1, TSK-2, TSK-3 |
| TSK-5 | implementer | planned | TSK-4 |
| TSK-6 | implementer | planned | TSK-5 |
| TSK-7 | reviewer / implementer | planned | TSK-1..TSK-6 |
