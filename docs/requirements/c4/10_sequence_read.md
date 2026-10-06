# Sequence — внутреннее чтение сохранённой сессии

Это `SessionQueryUseCase.find`, используемый внутри приложения и в проверках persistence. HTTP GET сессии не опубликован; браузер этим путём не восстанавливает диалог.

```mermaid
sequenceDiagram
    autonumber
    participant C as Внутренний вызывающий код / тест
    participant O as SessionTurnOrchestrator
    participant E as SessionExecutionService
    participant D as БД / native persister

    C->>O: find(sessionId)
    O->>E: find(sessionId)
    Note over E,D: BEGIN, без readOnly-флага
    E->>D: FOR SHARE NOWAIT
    alt Строка занята писателем
        D-->>E: Конфликт блокировки
        E->>D: ROLLBACK
        E-->>O: SESSION_BUSY
        O-->>C: SessionQueryException
    else Семафор отсутствует
        D-->>E: Empty
        E->>D: COMMIT
        E-->>O: Optional.empty
        O-->>C: Optional.empty
    else Семафор найден
        E->>E: read(session)
        E->>D: exists native
        alt Native отсутствует
            D-->>E: Нет записи
            E->>E: Empty без создания
        else Native существует
            E->>E: Проверить formatId, configuration.create
            E->>D: persister.restore временной SSM
            D-->>E: Сохранённое состояние
            E->>E: Snapshot, StoredSnapshotValidator; finally stop
        end
        E->>D: COMMIT и освобождение блокировки
        E-->>O: Optional SessionSnapshotDTO
        O-->>C: Тот же Optional после транзакции, без renderer
    end
```

Нет ensure, sendEvent, persist, увеличения версии или вызова corr. Несовместимый/повреждённый существующий снимок завершает чтение ошибкой хранения и rollback; empty не подменяет такую ошибку.
