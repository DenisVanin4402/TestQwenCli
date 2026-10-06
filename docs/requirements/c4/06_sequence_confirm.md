# Sequence — подтверждение и завершение

Предусловие: сохранена AWAITING_CONFIRM. Вход возвращает paymentNumber/preparationNo активного предложения и новый Request-Id.

```mermaid
sequenceDiagram
    autonumber
    participant C as ACL-клиент
    participant A as AclInputAdapter / mapper
    participant O as SessionTurnOrchestrator
    participant E as SessionExecutionService
    participant N as StateMachine / SSM
    participant G as Штатные Guard-классы
    participant X as ExecutePreparedOperationAction
    participant W as InvestCorrAdapter / InvestCorrClient
    participant I as Внешний corr или локальный stub
    participant D as БД / native persister
    participant R as Renderer

    C->>A: accept_propose + state CONFIRMATION
    A->>O: event=CONFIRM и список значений без дедупликации
    O->>E: ensureSemaphore, затем process
    Note over E,D: ensure завершён отдельно; BEGIN рабочей транзакции
    E->>D: FOR UPDATE NOWAIT
    E->>E: Проверка formatId, configuration.create
    E->>D: persister.restore того же экземпляра SSM
    D-->>E: AWAITING_CONFIRM и сохранённая подготовка
    E->>E: Извлечь и проверить snapshot
    E->>N: sendEvent CONFIRM, SessionExecutionContext в header
    N->>G: CONFIRM: active → context → number → separate → data
    G-->>N: true, дополнительные guards STATUS отсутствуют
    N->>X: execute(StateContext), данные из header
    X->>X: Записать confirmedRequestId и confirmedAt
    X->>W: callOperation(STATUS, серверный OperationContext)
    W->>I: Один submit, operationId в clientRequestId и ключе
    I-->>W: ACCEPTED и непустой reference
    W-->>X: void, без банковского результата
    X->>X: lastResult = REQUEST_SUBMITTED
    X-->>N: Action завершён
    N-->>E: complete, tracked отметил завершение action
    E->>E: Проверить ошибки/отказ; COMPLETED; обновить ExtendedState
    E->>D: Native persist
    E->>N: finally: stopReactively().block()
    E->>D: Spring COMMIT
    E-->>O: Зафиксированный snapshot
    O->>R: render
    R-->>O: Текст передачи запроса
    O-->>A: Успех и terminal=true
    A-->>C: 200 inform, status_code=200, final_message=true
```

Внешний вызов выполняется под блокировкой до commit. Локальный rollback не отменяет его эффект. Автоматического retry и ожидания callback нет; при ошибке действует [отдельная последовательность](08_sequence_failures.md).
