# Data And State View

PostgreSQL schema `external_gateway` является production source of truth для lease-слотов, async-очереди, sync trace и callback-доставки. Memory repository mode полезен для локальной проверки, но не должен использоваться для production-кластера.

## ER-диаграмма

```mermaid
erDiagram
    EXT_SLOTS {
        int slot_id PK
        uuid lease_id
        varchar owner
        varchar kind
        timestamp acquired_at
        timestamp expires_at
        varchar task_id
    }

    EXT_SYNC_WAITERS {
        uuid waiter_id PK
        varchar owner
        timestamp registered_at
        timestamp expires_at
    }

    EXT_REQUEST_QUEUE {
        bigint id PK
        uuid external_id
        varchar client_service
        varchar priority
        int priority_weight
        varchar delivery_mode
        varchar status
        varchar callback_delivery_status
        jsonb payload
        jsonb result
        jsonb error
        int attempts
        int max_attempts
        timestamp created_at
        timestamp available_at
        timestamp started_at
        timestamp finished_at
        timestamp updated_at
        text last_error
        boolean retryable
    }

    EXT_CALLBACK_DELIVERY {
        uuid delivery_id PK
        bigint task_id FK
        text callback_url
        varchar status
        jsonb payload
        int attempts
        int max_attempts
        timestamp created_at
        timestamp available_at
        timestamp started_at
        timestamp completed_at
        text last_error
    }

    EXT_REQUEST_QUEUE ||--o| EXT_CALLBACK_DELIVERY : "task_id"
```

## Таблицы

### `ext_slots`

Хранит фиксированный пул слотов `1..5`. Слот свободен, если `lease_id IS NULL`. Занятый слот содержит `lease_id`, `owner`, `kind`, `acquired_at`, `expires_at` и, для async, `task_id`.

Ключевые правила:

- `kind` может быть `SYNC`, `ASYNC` или `NULL`;
- release и heartbeat допускаются только при совпадении `slot_id + lease_id`;
- истекший lease может быть переиспользован следующим acquire или очищен reaper-ом;
- `task_id` заполняется для `ASYNC`, чтобы dashboard мог связать слот с задачей.

### `ext_sync_waiters`

Хранит короткоживущие записи sync-запросов, которые ждут слот. Наличие live waiter блокирует старт новых async-задач. Запись удаляется в `finally` после завершения ожидания.

### `ext_request_queue`

Используется для двух типов записей:

- async-задачи с `delivery_mode IN ('CALLBACK', 'POLLING')`;
- sync trace с `delivery_mode='SYNC'`.

Async idempotency реализована частичным уникальным индексом:

```text
UNIQUE (client_service, external_id)
WHERE delivery_mode IN ('CALLBACK', 'POLLING')
```

Это позволяет сохранять несколько sync trace с тем же `external_id`, но не позволяет создать две async-задачи для одной пары `clientService + externalId`.

### `ext_callback_delivery`

Отдельная очередь доставки callback. Для одной async-задачи допускается не более одной записи callback delivery. Callback delivery имеет собственный статус и attempts, потому что доставка результата клиенту является отдельной надежностной задачей и не должна менять upstream-статус.

## State machine: слот

```mermaid
stateDiagram-v2
    [*] --> FREE
    FREE --> SYNC_LEASED: acquireSyncSlot
    FREE --> ASYNC_LEASED: acquireAsyncSlot
    SYNC_LEASED --> FREE: release(slotId, leaseId)
    ASYNC_LEASED --> FREE: release(slotId, leaseId)
    SYNC_LEASED --> FREE: reapExpiredLeases
    ASYNC_LEASED --> FREE: reapExpiredLeases
    SYNC_LEASED --> SYNC_LEASED: heartbeat(slotId, leaseId)
    ASYNC_LEASED --> ASYNC_LEASED: heartbeat(slotId, leaseId)
```

## State machine: async task

```mermaid
stateDiagram-v2
    [*] --> PENDING: submit
    PENDING --> PENDING: idempotent submit same payload
    PENDING --> CANCELLED: cancel
    PENDING --> IN_PROGRESS: claimNextPending
    IN_PROGRESS --> DONE: upstream success
    IN_PROGRESS --> PENDING: transient failure, attempts left
    IN_PROGRESS --> DEAD: transient failure, max attempts reached
    FAILED --> PENDING: manual retry if retryable
    DEAD --> PENDING: manual retry if retryable
    DONE --> [*]
    CANCELLED --> [*]
    DEAD --> [*]
    FAILED --> [*]
```

В PostgreSQL-режиме `IN_PROGRESS` при штатной обработке находится внутри processing transaction. Если JVM падает до финального обновления, row-lock и изменения задачи откатываются, а committed ASYNC lease освобождается по TTL или reaper-ом.

## State machine: callback delivery

```mermaid
stateDiagram-v2
    [*] --> PENDING: plan callback
    [*] --> DEAD: no allow-list callback URL
    PENDING --> DELIVERING: claimNextPending
    RETRY --> DELIVERING: claimNextPending after backoff
    DELIVERING --> DELIVERED: client 2xx
    DELIVERING --> RETRY: timeout or 5xx, attempts left
    DELIVERING --> DEAD: timeout or 5xx, max attempts reached
    DELIVERING --> RETRY: recovery timeout, attempts left
    DELIVERING --> DEAD: recovery timeout, max attempts reached
    DELIVERED --> [*]
    DEAD --> [*]
```

## Индексы и порядок обработки

| Объект | Индекс / порядок | Назначение |
| --- | --- | --- |
| `ext_slots` | `idx_ext_slots_busy_kind_expires` | Быстрый подсчет занятых слотов и cleanup. |
| `ext_sync_waiters` | `idx_ext_sync_waiters_expires_at` | Быстрое удаление истекших waiters. |
| `ext_request_queue` | `idx_ext_request_queue_claim` | Claim async задач по `priority_weight DESC, available_at ASC, id ASC`. |
| `ext_request_queue` | `idx_ext_request_queue_external_id` | Lookup по `externalId`. |
| `ext_callback_delivery` | `idx_ext_callback_delivery_claim` | Claim callback delivery по `available_at ASC, created_at ASC, delivery_id ASC`. |

## Data retention

В текущей реализации retention/archival policy явно не задана. Для production требуется определить:

- срок хранения sync trace;
- срок хранения финальных async-задач;
- срок хранения `DELIVERED` callback delivery;
- отдельный retention для `DEAD`, `FAILED`, `CANCELLED`;
- правила удаления с учетом поддержки расследований и аудита.

Без retention `ext_request_queue` и `ext_callback_delivery` будут расти бесконечно.
