# Sequence — конкуренция и повторная инициализация

NOWAIT действует на рабочий захват строки. Предварительная конкурентная вставка семафора — отдельная короткая транзакция и может ожидать уникальный ключ.

```mermaid
sequenceDiagram
    autonumber
    participant A as Ход A / SessionExecutionService
    participant B as Ход B / SessionExecutionService
    participant D as БД
    participant W as WorkflowManager

    A->>D: ensure в REQUIRES_NEW; COMMIT семафора S
    B->>D: ensure S; строка уже существует
    A->>D: BEGIN A, FOR UPDATE NOWAIT S
    D-->>A: Блокировка получена
    A->>A: Restore, guards, Action
    opt Подтверждение
        A->>W: Один синхронный callOperation
        Note over A,D: Блокировка S удерживается во время вызова
    end
    B->>D: BEGIN B, FOR UPDATE NOWAIT S
    D-->>B: Конфликт блокировки немедленно
    B->>D: ROLLBACK B
    B-->>B: SESSION_BUSY → HTTP 400, без очереди и retry
    A->>D: Persist, затем COMMIT после stop
    Note over A,D: Блокировка освобождена, строка S остаётся
```

Следующий сценарий независим: первая инициализация завершилась ошибкой, а native-запись ещё не была зафиксирована.

```mermaid
sequenceDiagram
    autonumber
    participant A as Первый допустимый ход
    participant B as Следующий допустимый ход
    participant D as БД

    A->>D: Создать семафор S, отдельный COMMIT
    A->>D: BEGIN, NOWAIT S, native отсутствует
    A->>A: Create FSM, ошибка и stop
    A->>D: ROLLBACK рабочей транзакции
    Note over A,D: S существует; native FSM отсутствует
    B->>D: Ensure S, затем BEGIN и NOWAIT S
    B->>D: Проверить status-v3 и отсутствие native
    B->>B: Create FSM, допустимый SELECT и подготовка
    B->>D: Native persist, stop, COMMIT
    B-->>B: Успешное предложение после commit
```

Другой sessionId использует другую строку блокировки. Повреждённая либо несовместимая существующая FSM не считается отсутствующей и не пересоздаётся.
