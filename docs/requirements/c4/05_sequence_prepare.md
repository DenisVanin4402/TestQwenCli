# Sequence — подготовка STATUS

Оба POST-входа используют эту последовательность. Показан допустимый SELECT; отказы вынесены в [диаграмму ошибок](08_sequence_failures.md).

```mermaid
sequenceDiagram
    autonumber
    participant C as ACL-клиент
    participant A as Controller / AclInputAdapter
    participant O as SessionTurnOrchestrator
    participant E as SessionExecutionService
    participant S as SessionSemaphore
    participant N as StateMachine / SSM
    participant G as Guards / PrepareOperationAction
    participant D as БД / native persister
    participant R as SessionResponseRenderer

    C->>A: request/status + UUID заголовков + metadata
    A->>A: Проверить конверт, resolve STATUS, map input
    A->>O: handle(input, sessionId:requestId)
    O->>E: ensureSemaphore(sessionId)
    E->>S: ensure (REQUIRES_NEW)
    S->>D: Найти или вставить agent_session с status-v3
    D-->>S: Commit короткой транзакции
    S-->>E: Семафор существует
    E-->>O: Готово
    O->>E: process(input) через Spring proxy
    Note over E,D: BEGIN рабочей JPA-транзакции
    E->>D: FOR UPDATE NOWAIT по sessionId
    D-->>E: Заблокированная строка
    E->>D: Проверить наличие native
    E->>E: Проверить formatId, configuration.create(sessionId)
    alt Native отсутствует
        E->>N: startReactively: CHOOSING_REQUEST_TYPE
    else Сохранён шаг выбора после CANCEL
        E->>D: persister.restore того же экземпляра SSM
        D-->>E: Восстановлен контекст и счётчик
        E->>E: Извлечь snapshot, StoredSnapshotValidator.verify
    end
    E->>N: sendEvent SELECT, SessionExecutionContext в header
    N->>G: Guard.evaluate: InitialContext, ContextConsistency
    G-->>N: true, дополнительные guards STATUS отсутствуют
    N->>G: PrepareOperationAction.execute(StateContext)
    G->>G: Копия контекста, новый UUID, номер + 1, snapshot
    G-->>N: CONFIRMATION_REQUIRED
    N-->>E: complete, tracked отметил завершение action
    E->>E: Проверить ошибки/отказ, обновить snapshot и ExtendedState
    E->>D: Native persist того же экземпляра
    E->>N: finally: stopReactively().block()
    E->>D: Spring COMMIT, освобождение блокировки
    E-->>O: Snapshot после успешного commit
    O->>R: render(snapshot)
    R-->>O: Текст и paymentNumber + preparationNo
    O-->>A: TurnOutDTO
    A-->>C: 200 propose, state CONFIRMATION, final_message=false
```

WorkflowManager при подготовке не вызывается. Предложение строится из возвращённого снимка, без повторного чтения и без записи готового текста в FSM.
