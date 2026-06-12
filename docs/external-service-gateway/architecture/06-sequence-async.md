# Sequence View. Async Scenarios

Async contract разделяет прием запроса, выполнение upstream и получение результата. Это позволяет быстро вернуть `202 Accepted`, сохранить задачу в durable storage и доставить результат callback-ом или через polling.

В стрелках к `PostgreSQL` имя таблицы указано перед двоеточием, например `ext_request_queue: claim next PENDING task`.
Границы транзакций показаны подсвеченными `rect`-блоками и заметками `TX ... begin/commit`.

## S-ASYNC-01. Submit новой async-задачи

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalAsyncController
    participant Service as ExternalAsyncService
    participant Repo as AsyncTaskRepository
    participant DB as PostgreSQL

    Client->>API: POST /v1/external/async
    API->>Service: submit(request, X-Request-Id)
    Service->>Repo: submit(request, maxAttempts, now)
    rect rgb(238, 246, 255)
        Note over Repo,DB: TX ASYNC SUBMIT begin
        Repo->>DB: ext_request_queue: INSERT status=PENDING
        DB-->>Repo: task row
        Note over Repo,DB: TX ASYNC SUBMIT commit
    end
    Repo-->>Service: AsyncSubmitResult submitted, alreadyExisted=false
    Service-->>API: AsyncSubmitResponse
    API-->>Client: 202 Accepted, taskId, statusUrl
```

Async-задача еще не выполнялась. Ее обработает dispatcher на следующем scheduled tick или раньше, если воркер уже крутит цикл до idle.

## S-ASYNC-02. Idempotent submit той же задачи

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant Service as ExternalAsyncService
    participant Repo as AsyncTaskRepository
    participant DB as PostgreSQL

    Client->>Service: POST /v1/external/async same clientService + externalId
    Service->>Repo: submit(request, maxAttempts, now)
    rect rgb(238, 246, 255)
        Note over Repo,DB: TX ASYNC SUBMIT begin
        Repo->>DB: ext_request_queue: INSERT ON CONFLICT DO NOTHING
        DB-->>Repo: no inserted row
        Repo->>DB: ext_request_queue: SELECT existing by clientService + externalId
        DB-->>Repo: existing task
        Repo->>Repo: compare payload, priority, deliveryMode
        Note over Repo,DB: TX ASYNC SUBMIT commit
    end
    Repo-->>Service: alreadyExisted=true
    Service-->>Client: 202 Accepted, same taskId
```

Идемпотентность async не использует `Idempotency-Key`. Ключом является пара `clientService + externalId`.

## S-ASYNC-03. Idempotency conflict

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalAsyncController
    participant Service as ExternalAsyncService
    participant Repo as AsyncTaskRepository
    participant DB as PostgreSQL
    participant Errors as ExternalGatewayExceptionHandler

    Client->>API: POST /v1/external/async with same key and different payload
    API->>Service: submit(request, requestId)
    Service->>Repo: submit(request, maxAttempts, now)
    rect rgb(238, 246, 255)
        Note over Repo,DB: TX ASYNC SUBMIT begin
        Repo->>DB: ext_request_queue: INSERT ON CONFLICT DO NOTHING
        DB-->>Repo: no inserted row
        Repo->>DB: ext_request_queue: SELECT existing task
        Repo->>Repo: detect conflicting fields
        Note over Repo,DB: TX ASYNC SUBMIT commit
    end
    Repo-->>Service: IDEMPOTENCY_CONFLICT
    Service--xAPI: AsyncIdempotencyConflictException
    API->>Errors: handleGatewayException
    Errors-->>Client: 409 IDEMPOTENCY_CONFLICT
```

Конфликт возможен при различии `payload`, `priority` или `deliveryMode`.

## S-ASYNC-04. Dispatch success с callback mode

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Async Scheduler
    participant Dispatcher as ExternalAsyncDispatcher
    participant Tasks as AsyncTaskRepository
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient
    participant Planner as CallbackDeliveryPlanner
    participant CallbackRepo as CallbackDeliveryRepository

    Scheduler->>Dispatcher: dispatchBatch(batchSize)
    rect rgb(238, 246, 255)
        Note over Dispatcher,DB: TX ASYNC PROCESSING begin
        Dispatcher->>Tasks: executeInProcessingTransaction(dispatchOnce)
        Tasks->>DB: ext_request_queue: claim next PENDING FOR UPDATE SKIP LOCKED
        DB-->>Tasks: task IN_PROGRESS
        Tasks-->>Dispatcher: AsyncTaskClaim
        Dispatcher->>Slots: tryAcquireAsyncSlot(owner, taskId)
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW begin
        Slots->>DB: ext_sync_waiters + ext_slots: check live waiters and sync reserve
        DB-->>Slots: ASYNC slot lease
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW commit
        Slots-->>Dispatcher: SlotLease
        Note over Dispatcher,DB: TX ASYNC PROCESSING stays open during upstream call
        Dispatcher->>Upstream: call(task payload)
        Upstream-->>Dispatcher: result
        Dispatcher->>Tasks: complete(taskId, result)
        Tasks->>DB: ext_request_queue: UPDATE status=DONE, result, finished_at
        Tasks-->>Dispatcher: final task
        Dispatcher->>Planner: planForFinalTask(final task)
        Planner->>CallbackRepo: createPending(task, allow-list callbackUrl)
        CallbackRepo->>DB: ext_callback_delivery: INSERT status=PENDING
        Planner->>Tasks: updateCallbackDeliveryStatus(PENDING)
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=PENDING
        Dispatcher->>Slots: release(slot_id, lease_id)
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW commit
        Note over Dispatcher,DB: TX ASYNC PROCESSING commit
    end
```

На этом сценарий upstream-задачи завершен. Доставка callback выполняется отдельным dispatcher-ом и описана в [07-sequence-callback.md](07-sequence-callback.md).

## S-ASYNC-05. Dispatch success с polling mode

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as ExternalAsyncDispatcher
    participant Tasks as AsyncTaskRepository
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient
    participant Planner as CallbackDeliveryPlanner

    rect rgb(238, 246, 255)
        Note over Dispatcher,DB: TX ASYNC PROCESSING begin
        Dispatcher->>Tasks: claimNextPending
        Tasks->>DB: ext_request_queue: UPDATE PENDING to IN_PROGRESS
        DB-->>Tasks: task deliveryMode=POLLING
        Dispatcher->>Slots: tryAcquireAsyncSlot
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW begin
        Slots->>DB: ext_sync_waiters + ext_slots: check gate and acquire ASYNC lease
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW commit
        Slots-->>Dispatcher: SlotLease
        Note over Dispatcher,DB: TX ASYNC PROCESSING stays open during upstream call
        Dispatcher->>Upstream: call
        Upstream-->>Dispatcher: result
        Dispatcher->>Tasks: complete(taskId, result)
        Tasks->>DB: ext_request_queue: UPDATE status=DONE
        Dispatcher->>Planner: planForFinalTask(final task)
        Planner->>Tasks: updateCallbackDeliveryStatus(NOT_REQUIRED)
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=NOT_REQUIRED
        Planner-->>Dispatcher: no callback delivery
        Dispatcher->>Slots: release(slot_id, lease_id)
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW commit
        Note over Dispatcher,DB: TX ASYNC PROCESSING commit
    end
```

Клиент получает результат через polling endpoint.

## S-ASYNC-06. Async slot недоступен после claim

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as ExternalAsyncDispatcher
    participant Tasks as AsyncTaskRepository
    participant Slots as SlotManager
    participant DB as PostgreSQL

    rect rgb(238, 246, 255)
        Note over Dispatcher,DB: TX ASYNC PROCESSING begin
        Dispatcher->>Tasks: claimNextPending
        Tasks->>DB: ext_request_queue: UPDATE PENDING to IN_PROGRESS, attempts + 1
        DB-->>Tasks: claimed task
        Dispatcher->>Slots: tryAcquireAsyncSlot(owner, taskId)
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW begin
        Slots->>DB: ext_sync_waiters + ext_slots: count live waiters and busy slots
        DB-->>Slots: no ASYNC slot allowed
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW commit
        Slots-->>Dispatcher: Optional.empty
        Dispatcher->>Tasks: returnClaimToPending(taskId)
        Tasks->>DB: ext_request_queue: UPDATE IN_PROGRESS to PENDING, attempts - 1, available_at=now
        Note over Dispatcher,DB: TX ASYNC PROCESSING commit
    end
    Dispatcher-->>Dispatcher: dispatchOnce returns false
```

Это не считается upstream-попыткой. Задача возвращается в очередь без backoff, а `attempts` компенсируется.

## S-ASYNC-07. Transient upstream failure, попытки остались

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as ExternalAsyncDispatcher
    participant Tasks as AsyncTaskRepository
    participant Slots as SlotManager
    participant Upstream as ExternalUpstreamClient
    participant DB as PostgreSQL

    rect rgb(238, 246, 255)
        Note over Dispatcher,DB: TX ASYNC PROCESSING begin
        Dispatcher->>Tasks: claimNextPending
        Tasks->>DB: ext_request_queue: claim next PENDING task
        Tasks-->>Dispatcher: task IN_PROGRESS
        Dispatcher->>Slots: tryAcquireAsyncSlot
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW begin
        Slots->>DB: ext_sync_waiters + ext_slots: check gate and acquire ASYNC lease
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW commit
        Slots-->>Dispatcher: SlotLease
        Note over Dispatcher,DB: TX ASYNC PROCESSING stays open during upstream call
        Dispatcher->>Upstream: call
        Upstream--xDispatcher: transient RuntimeException
        Dispatcher->>Tasks: failTransient(taskId, message, retryBackoff)
        Tasks->>DB: ext_request_queue: UPDATE status=PENDING, available_at=now+backoff, last_error
        Dispatcher->>Slots: release(slot_id, lease_id)
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW commit
        Note over Dispatcher,DB: TX ASYNC PROCESSING commit
    end
```

Callback delivery не создается, потому что задача еще не в финальном статусе.

## S-ASYNC-08. Transient upstream failure, попытки исчерпаны

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as ExternalAsyncDispatcher
    participant Tasks as AsyncTaskRepository
    participant Slots as SlotManager
    participant Upstream as ExternalUpstreamClient
    participant Planner as CallbackDeliveryPlanner
    participant DB as PostgreSQL

    rect rgb(238, 246, 255)
        Note over Dispatcher,DB: TX ASYNC PROCESSING begin
        Dispatcher->>Tasks: claimNextPending
        Tasks->>DB: ext_request_queue: claim next PENDING task
        Tasks-->>Dispatcher: task IN_PROGRESS, attempts=maxAttempts
        Dispatcher->>Slots: tryAcquireAsyncSlot
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW begin
        Slots->>DB: ext_sync_waiters + ext_slots: check gate and acquire ASYNC lease
        Note over Slots,DB: TX SLOT ACQUIRE REQUIRES_NEW commit
        Slots-->>Dispatcher: SlotLease
        Note over Dispatcher,DB: TX ASYNC PROCESSING stays open during upstream call
        Dispatcher->>Upstream: call
        Upstream--xDispatcher: transient RuntimeException
        Dispatcher->>Tasks: failTransient(taskId, message, retryBackoff)
        Tasks->>DB: ext_request_queue: UPDATE status=DEAD, error=UPSTREAM_TRANSIENT_FAILURE, retryable=true
        Tasks-->>Dispatcher: final task DEAD
        Dispatcher->>Planner: planForFinalTask(DEAD)
        Planner->>DB: ext_callback_delivery: create callback delivery if deliveryMode=CALLBACK
        Planner->>DB: ext_request_queue: update callback_delivery_status
        Dispatcher->>Slots: release(slot_id, lease_id)
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE REQUIRES_NEW commit
        Note over Dispatcher,DB: TX ASYNC PROCESSING commit
    end
```

`DEAD` задача может быть возвращена вручную через retry endpoint, если `retryable=true`.

## S-ASYNC-09. Polling успешного результата

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalAsyncController
    participant Service as ExternalAsyncService
    participant Repo as AsyncTaskRepository
    participant DB as PostgreSQL

    Client->>API: GET /v1/external/async/{taskId}
    API->>Service: getByTaskId(taskId, X-Client-Service)
    Service->>Repo: findByTaskId(taskId, optional clientService scope)
    Repo->>DB: ext_request_queue: SELECT task WHERE id=:taskId AND delivery_mode IN async modes
    DB-->>Repo: DONE task with result
    Repo-->>Service: AsyncTask
    Service-->>API: AsyncTask
    API-->>Client: 200 OK, status=DONE, result
```

Если `X-Client-Service` передан, lookup ограничен этим сервисом. Если не передан, текущая реализация не ограничивает lookup по клиенту. Это временное ограничение до внедрения service identity.

## S-ASYNC-10. Cancel pending-задачи

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalAsyncController
    participant Service as ExternalAsyncService
    participant Repo as AsyncTaskRepository
    participant DB as PostgreSQL

    Client->>API: DELETE /v1/external/async/{taskId}
    API->>Service: cancel(taskId, X-Client-Service)
    Service->>Repo: cancel(taskId, clientServiceScope, now)
    rect rgb(238, 246, 255)
        Note over Repo,DB: TX ASYNC CANCEL begin
        Repo->>DB: ext_request_queue: SELECT task FOR UPDATE
        DB-->>Repo: status=PENDING
        Repo->>DB: ext_request_queue: UPDATE status=CANCELLED, error=TASK_CANCELLED
        DB-->>Repo: updated task
        Note over Repo,DB: TX ASYNC CANCEL commit
    end
    Repo-->>Service: updated
    Service-->>API: AsyncTask CANCELLED
    API-->>Client: 200 OK
```

Повторная отмена уже `CANCELLED` задачи идемпотентна.

## S-ASYNC-11. Cancel conflict для выполняемой задачи

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalAsyncController
    participant Service as ExternalAsyncService
    participant Repo as AsyncTaskRepository
    participant DB as PostgreSQL
    participant Errors as ExternalGatewayExceptionHandler

    Client->>API: DELETE /v1/external/async/{taskId}
    API->>Service: cancel(taskId, X-Client-Service)
    Service->>Repo: cancel(taskId, scope, now)
    rect rgb(238, 246, 255)
        Note over Repo,DB: TX ASYNC CANCEL begin
        Repo->>DB: ext_request_queue: SELECT task FOR UPDATE
        DB-->>Repo: status=IN_PROGRESS or final status
        Note over Repo,DB: TX ASYNC CANCEL commit
    end
    Repo-->>Service: CONFLICT
    Service--xAPI: AsyncTaskStateConflictException
    API->>Errors: handleGatewayException
    Errors-->>Client: 409 state conflict
```

Задача не отменяется после старта upstream-вызова.

## S-ASYNC-12. Manual retry для retryable DEAD/FAILED

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalAsyncController
    participant Service as ExternalAsyncService
    participant Repo as AsyncTaskRepository
    participant DB as PostgreSQL

    Client->>API: POST /v1/external/async/{taskId}/retry
    API->>Service: retry(taskId, X-Client-Service)
    Service->>Repo: retry(taskId, scope, now)
    rect rgb(238, 246, 255)
        Note over Repo,DB: TX ASYNC MANUAL RETRY begin
        Repo->>DB: ext_request_queue: SELECT task FOR UPDATE
        DB-->>Repo: status=DEAD, retryable=true
        Repo->>DB: ext_request_queue: UPDATE status=PENDING, attempts=0, available_at=now, error=NULL
        DB-->>Repo: updated task
        Note over Repo,DB: TX ASYNC MANUAL RETRY commit
    end
    Repo-->>Service: AsyncTask PENDING
    Service-->>API: AsyncTask PENDING
    API-->>Client: 202 Accepted
```

Manual retry не меняет `externalId` и не создает новую задачу. Следующая обработка пойдет через обычный dispatcher path.
