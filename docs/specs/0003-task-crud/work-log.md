# Work Log: Task Management CRUD API

Формат записи: `YYYY-MM-DD HH:MM — {фаза} — {действие} — {результат}`

## Записи

- `2026-05-18 00:00` — Implementation — TSK-1: Созданы TaskStatus.java (enum: TODO, IN_PROGRESS, DONE) — OK
- `2026-05-18 00:01` — Implementation — TSK-1: Создан Task.java (UUID, title, description, status, createdAt, updatedAt) с фабричным методом create() — compile OK
- `2026-05-18 00:02` — Implementation — TSK-2: Создан TaskRepository.java (ConcurrentHashMap, потокобезопасный) — compile OK
- `2026-05-18 00:03` — Implementation — TSK-3: Созданы DTO (CreateTaskRequest, UpdateTaskRequest, TaskResponse, ErrorEnvelope) — compile OK
- `2026-05-18 00:04` — Implementation — TSK-4: Создан TaskController с POST /api/v1/tasks и GET /api/v1/tasks — compile OK
- `2026-05-18 00:05` — Implementation — TSK-5: Добавлены GET by ID, PUT, DELETE — compile OK
- `2026-05-18 00:06` — Implementation — TSK-6: Создан GlobalExceptionHandler (+ ResourceNotFoundException, UUID validation) — compile OK
- `2026-05-18 00:07` — Implementation — TSK-7: Созданы интеграционные тесты — mvn test OK
- `2026-05-18 00:08` — Verification — mvn clean compile, mvn test — все тесты прошли
