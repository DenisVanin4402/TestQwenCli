# Граф FSM текущего POC

```mermaid
stateDiagram-v2
    [*] --> CHOOSING_REQUEST_TYPE: Создание отсутствующей временной FSM
    CHOOSING_REQUEST_TYPE --> AWAITING_CONFIRM: SELECT / PrepareOperationAction
    CHOOSING_REQUEST_TYPE --> CHOOSING_REQUEST_TYPE: CANCEL / ReportNoPreparationAction
    AWAITING_CONFIRM --> CHOOSING_REQUEST_TYPE: CANCEL / CancelPreparationAction
    AWAITING_CONFIRM --> COMPLETED: CONFIRM / ExecutePreparedOperationAction

    note right of CHOOSING_REQUEST_TYPE
        Первая подготовка требует полного контекста.
        CANCEL требует существующей бизнес-сессии.
        CONFIRM-перехода нет.
    end note
    note right of AWAITING_CONFIRM
        Повторного SELECT нет.
        CONFIRM проверяет активность, контекст,
        preparationNo, отдельный Request-Id,
        точный набор подтверждаемых данных.
    end note
    note right of COMPLETED
        Исходящих переходов нет.
        Новая операция требует новой сессии.
        Успешный ответ только после commit.
    end note
```

COMPLETED — прикладное терминальное состояние; в builder оно включено в набор состояний, а не объявлено через SSM `.end()`. Начальное создание в памяти не означает сохранения бизнес-сессии. Ошибка guard/Action приводит к rollback, а не к отдельному состоянию ERROR.

Отсутствующий переход существующей FSM даёт INVALID_COMMAND или SESSION_COMPLETED, сохраняет новый lastResult/lastRequestId/projectionVersion, но не меняет подготовку или состояние. Guard denial не делает native save. Порядок guards каждого перехода приведён в [§3 спецификации](../technical_specification_current.md#3-программная-схема-операции-и-fsm).
