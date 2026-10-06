# Sequence — отказ, новая подготовка и старое согласие

Предусловие: активное предложение N. Сокращённый участник «обработка» объединяет оркестратор, SessionExecutionService и временную SSM; каждый ход включает отдельный ensure и рабочую транзакцию по [сценарию подготовки](05_sequence_prepare.md).

```mermaid
sequenceDiagram
    autonumber
    participant C as ACL-клиент
    participant A as AclInputAdapter
    participant P as Обработка хода / SSM
    participant G as Guards / actions
    participant D as БД

    C->>A: reject_propose, state можно опустить
    A->>P: event=CANCEL
    P->>D: BEGIN, lock, restore подготовки N
    P->>G: CANCEL: ExistingSession, ContextConsistency
    G-->>P: Допуск CancelPreparationAction
    P->>G: Удалить preparation, сохранить lastPreparationNo=N
    P->>D: Persist CHOOSING_REQUEST_TYPE, stop, COMMIT
    P-->>A: PREPARATION_CANCELLED после commit
    A-->>C: 200 inform и suggestions/status

    C->>A: request/status
    A->>P: event=SELECT, operation=STATUS
    P->>D: BEGIN, lock, restore
    P->>G: SELECT guards, затем PrepareOperationAction
    G-->>P: Новый UUID, номер N+1, новый снимок
    P->>D: Persist AWAITING_CONFIRM, stop, COMMIT
    P-->>A: CONFIRMATION_REQUIRED
    A-->>C: 200 propose с preparationNo=N+1

    C->>A: accept_propose со старым preparationNo=N
    A->>P: event=CONFIRM и старый state
    P->>D: BEGIN, lock, restore N+1
    P->>G: ActivePreparation, ContextConsistency, PreparationNumber
    G-->>P: STALE_PREPARATION, цепочка остановлена
    Note over P,G: SeparateConfirmation/Data и Action не выполняются
    P->>D: Stop и ROLLBACK без native persist
    P-->>A: Ошибка после завершения транзакции
    A-->>C: 400 failure, без suggestions
    Note over P,D: Подготовка N+1 и её согласие не изменены; corr не вызван
```

Тот же номер платежа в двух предложениях не делает их одним согласием. Новый номер подготовки отличает предложения после CANCEL. UI после failure не предлагает автоматического повтора, хотя подготовка сохраняется на сервере.
