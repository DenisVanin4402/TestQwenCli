# Sequence — отказы, rollback и внешний эффект

Ветви альтернативны. Транспортный отказ до вызова SessionExecutionService вообще не создаёт рабочую транзакцию. Здесь показаны исходы после нормализации входа и отдельного ensure семафора.

```mermaid
sequenceDiagram
    autonumber
    participant A as ACL-адаптер
    participant O as Оркестратор
    participant E as SessionExecutionService / временная SSM
    participant X as Guards / Action
    participant W as WorkflowManager / corr
    participant D as БД
    participant R as Renderer

    A->>O: Нормализованный ход
    O->>E: process
    E->>D: BEGIN, NOWAIT, restore/create
    E->>X: Проверить переход
    alt Guard отклонил вход
        X-->>E: Типизированный отказ
        E->>E: Stop в finally; persist отсутствует
        E->>D: ROLLBACK
        E-->>O: INVALID_CONFIRMATION / STALE / MISMATCH и др.
        O-->>A: 400 failure после rollback
    else Ошибка Action или транспорта corr
        X->>W: Допущенный вызов операции, если ошибка не возникла раньше
        W-->>X: Исключение; внешний эффект мог состояться
        X-->>E: Ошибка callback
        E->>E: Довести перехваченную ошибку SSM; stop
        E->>D: ROLLBACK
        E-->>O: OPERATION_FAILED
        O-->>A: 500 failure, без автоматического retry
    else Corr принят, затем ошибка persist или stop
        X->>W: Один callOperation
        W-->>X: Принятие, внешний эффект
        X-->>E: REQUEST_SUBMITTED
        E->>D: Попытка native persist
        E->>E: finally stop, ошибка persist/stop до границы
        E->>D: ROLLBACK
        E-->>O: STORAGE_UNAVAILABLE
        O-->>A: 500 failure; локальный успех не подтверждён
    else Corr принят, persist и stop успешны, отказ commit
        X->>W: Один callOperation
        W-->>X: Принятие, внешний эффект
        X-->>E: REQUEST_SUBMITTED
        E->>D: Native persist
        E->>E: Stop
        E->>D: Spring COMMIT
        D-->>O: Исключение транзакционной границы
        O-->>A: 500 failure, STORAGE_UNAVAILABLE
    else Commit успешен, ошибка renderer
        X->>W: Один callOperation
        W-->>X: Принятие
        X-->>E: REQUEST_SUBMITTED
        E->>D: Persist
        E->>E: Stop
        E->>D: COMMIT
        E-->>O: Зафиксированный COMPLETED
        O->>R: render
        R-->>O: Исключение вне try обработки транзакции
        O-->>A: Исключение; ACL failure этим путём не гарантирован
        Note over E,D: COMPLETED сохранён, операция не запускается повторно
    end
```

Для воспроизводимого отказа commit по ограничению PostgreSQL проверяется сохранение последнего committed-снимка. Потеря связи во время commit может оставить неизвестный исход и не моделируется как гарантированный rollback. Ни одна ветвь локальной ошибки не обещает отсутствия эффекта corr.

Отсутствие подходящего перехода — отдельный штатный путь: существующая FSM сохраняет INVALID_COMMAND/SESSION_COMPLETED и обновляет метаданные результата, не меняя состояние/подготовку. Его не следует смешивать с guard denial.
