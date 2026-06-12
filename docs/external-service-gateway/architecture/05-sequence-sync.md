# Sequence View. Sync Scenarios

Sync API выполняет upstream-вызов в рамках исходного HTTP-запроса клиента. Все сценарии ниже разделены намеренно: каждая диаграмма показывает один путь и одну причину завершения.

В стрелках к `PostgreSQL` имя таблицы указано перед двоеточием, например `ext_slots: acquire SYNC lease`.
Границы транзакций показаны подсвеченными `rect`-блоками и заметками `TX ... begin/commit`.

## S-SYNC-01. Успешный sync с немедленным слотом

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient

    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT ACQUIRE begin
        Slots->>DB: ext_slots: acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT ACQUIRE commit
    end
    Slots-->>Sync: SlotLease
    Sync->>Upstream: call(externalId, clientService, payload)
    Upstream-->>Sync: result, upstreamStatus=200
    Sync->>Slots: release(slot_id, lease_id)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RELEASE begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE commit
    end
    rect rgb(245, 245, 245)
        Note over Sync,DB: TX SYNC TRACE begin
        Sync->>DB: ext_request_queue: insert sync trace DONE, delivery_mode=SYNC
        Note over Sync,DB: TX SYNC TRACE commit
    end
    Sync-->>API: ExternalSyncResponse SUCCEEDED
    API-->>Client: 200 OK
```

Особенности:

- слот освобождается в `finally`;
- sync trace является диагностической записью, а не источником ответа клиенту;
- `Idempotency-Key` передается upstream adapter'у, но gateway не хранит sync result по ключу.

## S-SYNC-02. Альтернативный успешный sync через LISTEN/NOTIFY

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Wait as SyncSlotWaitStrategy
    participant Upstream as ExternalUpstreamClient

    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT TRY begin
        Slots->>DB: ext_slots: try acquire SYNC lease
        DB-->>Slots: no free slot
        Note over Slots,DB: TX SLOT TRY commit
    end
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REGISTER begin
        Slots->>DB: ext_sync_waiters: register sync waiter
        Note over Slots,DB: TX WAITER REGISTER commit
    end
    Slots->>Wait: waitBeforeRetry(signalVersion, pause)
    DB-->>Wait: channel external_gateway_slot_released
    Wait-->>Slots: signal observed
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RETRY begin
        Slots->>DB: ext_slots: retry acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT RETRY commit
    end
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REMOVE begin
        Slots->>DB: ext_sync_waiters: remove sync waiter
        Note over Slots,DB: TX WAITER REMOVE commit
    end
    Slots-->>Sync: SlotLease
    Sync->>Upstream: upstream call
    Upstream-->>Sync: result
    Sync->>Slots: release(slot_id, lease_id)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RELEASE begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE commit
    end
    rect rgb(245, 245, 245)
        Note over Sync,DB: TX SYNC TRACE begin
        Sync->>DB: ext_request_queue: insert sync trace DONE
        Note over Sync,DB: TX SYNC TRACE commit
    end
    Sync-->>API: ExternalSyncResponse SUCCEEDED
    API-->>Client: 200 OK
```

Этот путь относится к режиму `external-gateway.slots.sync-acquire-wait-mode=listen_notify`. PostgreSQL `NOTIFY` используется только как сигнал проснуться и повторно проверить `ext_slots`; источником истины остается таблица слотов.

## S-SYNC-03. Альтернативный успешный sync через polling

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Wait as PollingSyncSlotWaitStrategy
    participant Upstream as ExternalUpstreamClient

    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT TRY begin
        Slots->>DB: ext_slots: try acquire SYNC lease
        DB-->>Slots: no free slot
        Note over Slots,DB: TX SLOT TRY commit
    end
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REGISTER begin
        Slots->>DB: ext_sync_waiters: register sync waiter
        Note over Slots,DB: TX WAITER REGISTER commit
    end
    loop until slot available or waitTimeout
        Slots->>Wait: waitBeforeRetry(pollInterval)
        Wait-->>Slots: polling pause elapsed
        rect rgb(238, 246, 255)
            Note over Slots,DB: TX SLOT RETRY begin
            Slots->>DB: ext_slots: retry acquire SYNC lease
            DB-->>Slots: no free slot or slot_id + lease_id
            Note over Slots,DB: TX SLOT RETRY commit
        end
    end
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REMOVE begin
        Slots->>DB: ext_sync_waiters: remove sync waiter
        Note over Slots,DB: TX WAITER REMOVE commit
    end
    Slots-->>Sync: SlotLease
    Sync->>Upstream: upstream call
    Upstream-->>Sync: result
    Sync->>Slots: release(slot_id, lease_id)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RELEASE begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE commit
    end
    rect rgb(245, 245, 245)
        Note over Sync,DB: TX SYNC TRACE begin
        Sync->>DB: ext_request_queue: insert sync trace DONE
        Note over Sync,DB: TX SYNC TRACE commit
    end
    Sync-->>API: ExternalSyncResponse SUCCEEDED
    API-->>Client: 200 OK
```

Этот путь относится к режиму `external-gateway.slots.sync-acquire-wait-mode=polling`. Gateway не ждет PostgreSQL notification и повторяет попытку после `external-gateway.slots.sync-acquire-poll-interval`.

## S-SYNC-04. Sync slot не получен до wait timeout

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Errors as ExternalGatewayExceptionHandler

    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT TRY begin
        Slots->>DB: ext_slots: try acquire SYNC lease
        DB-->>Slots: no free slot
        Note over Slots,DB: TX SLOT TRY commit
    end
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REGISTER begin
        Slots->>DB: ext_sync_waiters: register sync waiter
        Note over Slots,DB: TX WAITER REGISTER commit
    end
    loop until waitTimeout
        rect rgb(238, 246, 255)
            Note over Slots,DB: TX SLOT RETRY begin
            Slots->>DB: ext_slots: retry acquire SYNC lease
            DB-->>Slots: no free slot
            Note over Slots,DB: TX SLOT RETRY commit
        end
    end
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REMOVE begin
        Slots->>DB: ext_sync_waiters: remove sync waiter
        Note over Slots,DB: TX WAITER REMOVE commit
    end
    Slots-->>Sync: Optional.empty
    rect rgb(245, 245, 245)
        Note over Sync,DB: TX SYNC TRACE begin
        Sync->>DB: ext_request_queue: insert sync trace FAILED, code=NO_SLOT_AVAILABLE, attempts=0
        Note over Sync,DB: TX SYNC TRACE commit
    end
    Sync--xAPI: NoSlotAvailableException
    API->>Errors: handleGatewayException
    Errors-->>Client: 429 NO_SLOT_AVAILABLE, Retry-After: 1
```

Этот сценарий считается retryable. Клиент может повторить sync-вызов после `Retry-After`, но из-за отсутствия сохраненной sync-idempotency повтор может привести к новому upstream-вызову, если предыдущий вызов успел стартовать в другом сценарии.

## S-SYNC-05. Upstream timeout

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient
    participant Errors as ExternalGatewayExceptionHandler

    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT ACQUIRE begin
        Slots->>DB: ext_slots: acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT ACQUIRE commit
    end
    Slots-->>Sync: SlotLease
    Sync->>Upstream: call(...)
    Upstream--xSync: UpstreamTimeoutException
    Sync->>Slots: release(slot_id, lease_id)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RELEASE begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE commit
    end
    rect rgb(245, 245, 245)
        Note over Sync,DB: TX SYNC TRACE begin
        Sync->>DB: ext_request_queue: insert sync trace FAILED, code=UPSTREAM_TIMEOUT, attempts=1
        Note over Sync,DB: TX SYNC TRACE commit
    end
    Sync--xAPI: UpstreamTimeoutException
    API->>Errors: handleGatewayException
    Errors-->>Client: 504 UPSTREAM_TIMEOUT
```

Timeout upstream не оставляет слот занятым. Ответ retryable, но безопасность повторного sync-вызова зависит от идемпотентности upstream.

## S-SYNC-06. Client timeout или disconnect после захвата слота

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient

    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT ACQUIRE begin
        Slots->>DB: ext_slots: acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT ACQUIRE commit
    end
    Slots-->>Sync: SlotLease
    Client--xAPI: HTTP client timeout / disconnect
    Sync->>Upstream: upstream call continues in server thread
    Upstream-->>Sync: result or error
    Sync->>Slots: release(slot_id, lease_id)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RELEASE begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE commit
    end
    rect rgb(245, 245, 245)
        Note over Sync,DB: TX SYNC TRACE begin
        Sync->>DB: ext_request_queue: insert sync trace best-effort
        Note over Sync,DB: TX SYNC TRACE commit or rollback on persistence error
    end
    API--xClient: response cannot be delivered
```

Production-вывод:

- gateway должен гарантировать release слота независимо от состояния клиентского соединения;
- клиентский retry может создать повторный upstream-вызов, потому что sync result не хранится по `Idempotency-Key`;
- для критичных операций нужно либо внедрить sync idempotency storage, либо переводить их в async contract.

## S-SYNC-07. Ошибка записи sync trace после успешного upstream

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient
    participant Log as Application Log

    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT ACQUIRE begin
        Slots->>DB: ext_slots: acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT ACQUIRE commit
    end
    Slots-->>Sync: SlotLease
    Sync->>Upstream: upstream call
    Upstream-->>Sync: result
    Sync->>Slots: release(slot_id, lease_id)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RELEASE begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE commit
    end
    rect rgb(245, 245, 245)
        Note over Sync,DB: TX SYNC TRACE begin
        Sync->>DB: ext_request_queue: insert sync trace DONE
        DB--xSync: persistence error
        Note over Sync,DB: TX SYNC TRACE rollback
    end
    Sync->>Log: warn "Не удалось сохранить trace sync-запроса"
    Sync-->>API: ExternalSyncResponse SUCCEEDED
    API-->>Client: 200 OK
```

Trace write является best-effort наблюдаемостью. Потеря trace не должна превращать успешный upstream-вызов в клиентскую ошибку.
