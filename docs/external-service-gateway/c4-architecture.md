# C4 Architecture View

Документ фиксирует C4-представление `external-service-gateway`: от контекста системы до внутренних компонентов gateway. Диаграммы отражают текущие архитектурные решения:

- отдельный gateway-сервис между доменными сервисами и внешним сервисом;
- общий лимит `5 concurrent calls`;
- скользящий sync reserve;
- async callback в сервис-клиент;
- PostgreSQL как координатор слотов, очереди и callback delivery.

## Level 1. System Context

```mermaid
C4Context
    title External Service Gateway - System Context

    Person_Ext(operator, "Support / Operations", "Monitors queues, dead tasks, callbacks and incidents")

    System(invest_pay, "invest-pay", "Domain service with its own database schema")
    System(user_expertise, "user-expertise", "Domain service with its own database schema")
    System(gateway, "external-service-gateway", "Internal Spring Boot gateway for prioritized and throttled calls")
    System_Ext(external_service, "External Service", "Third-party service with max 5 concurrent calls")

    Rel(invest_pay, gateway, "Sync and async requests", "HTTP/JSON")
    Rel(user_expertise, gateway, "Sync and async requests", "HTTP/JSON")
    Rel(gateway, invest_pay, "Async result callback", "HTTP callback")
    Rel(gateway, user_expertise, "Async result callback", "HTTP callback")
    Rel(gateway, external_service, "Throttled upstream calls, max 5 in-flight", "HTTP")
    Rel(operator, gateway, "Observes metrics, logs and task states")
```

Ключевой смысл контекста: `invest-pay` и `user-expertise` не делят общую схему БД. Они интегрируются только через API gateway. Все прямые вызовы внешнего сервиса должны быть удалены или запрещены сетевой политикой.

## Level 2. Container View

```mermaid
C4Container
    title External Service Gateway - Container View

    System(invest_pay, "invest-pay", "Client service")
    System(user_expertise, "user-expertise", "Client service")
    System_Ext(external_service, "External Service", "Max 5 concurrent calls")

    System_Boundary(gateway_boundary, "external-service-gateway") {
        Container(api_app, "Gateway Application", "Spring Boot", "REST API, slot management, queue dispatching, callback delivery")
        ContainerDb(pg, "Gateway PostgreSQL", "PostgreSQL", "Slots, async queue, sync waiters, callback delivery, audit")
    }

    Rel(invest_pay, api_app, "POST /v1/external/sync, POST /v1/external/async, GET async result", "HTTP/JSON")
    Rel(user_expertise, api_app, "POST /v1/external/sync, POST /v1/external/async, GET async result", "HTTP/JSON")
    Rel(api_app, invest_pay, "POST callback with result/error", "HTTP callback")
    Rel(api_app, user_expertise, "POST callback with result/error", "HTTP callback")
    Rel(api_app, pg, "Acquire slots, claim tasks, save results, track callbacks", "JDBC")
    Rel(api_app, external_service, "Upstream calls", "HTTP")
```

`Gateway Application` может быть запущен в нескольких инстансах. Глобальный лимит обеспечивается не локальным пулом потоков, а общей PostgreSQL-схемой gateway.

Если два датацентра/плеча не имеют общего координатора, глобальный лимит `5` невозможен без отдельного соглашения о квотах, например `3 + 2`.

## Level 3. Gateway Component View

```mermaid
C4Component
    title External Service Gateway - Component View

    ContainerDb(pg, "Gateway PostgreSQL", "PostgreSQL", "Coordination and persistence")
    System_Ext(external_service, "External Service", "Max 5 concurrent calls")
    System(client_service, "Client Service", "invest-pay / user-expertise")

    Container_Boundary(app, "Gateway Application") {
        Component(sync_api, "Sync API", "Spring MVC", "Accepts sync calls and waits for a slot")
        Component(async_api, "Async API", "Spring MVC", "Accepts async tasks and exposes fallback result reads")
        Component(slot_manager, "Slot Manager", "Service", "Maintains lease-based global slots and sliding sync reserve")
        Component(queue_repo, "Queue Repository", "JDBC", "Persists async tasks and claims work with SKIP LOCKED")
        Component(dispatcher, "Async Dispatcher", "Scheduled worker + NOTIFY listener", "Starts async work only when priority policy allows it")
        Component(upstream_client, "Upstream Client", "HTTP client", "Calls external service with timeout, retry and circuit breaker policy")
        Component(callback_delivery, "Callback Delivery", "Worker", "Sends async result callbacks and retries delivery")
        Component(reaper, "Reaper", "Scheduled worker", "Restores stale leases, stuck tasks and stuck callbacks")
        Component(metrics, "Observability", "Micrometer + structured logs", "Exports metrics and logs for operations")
    }

    Rel(client_service, sync_api, "Sync request")
    Rel(client_service, async_api, "Async submit / fallback result read")
    Rel(sync_api, slot_manager, "Acquire SYNC slot")
    Rel(async_api, queue_repo, "Insert task")
    Rel(dispatcher, queue_repo, "Claim next task")
    Rel(dispatcher, slot_manager, "Acquire ASYNC slot by dynamic reserve")
    Rel(slot_manager, pg, "Read/update ext_slots and ext_sync_waiters")
    Rel(queue_repo, pg, "Read/update ext_request_queue")
    Rel(dispatcher, upstream_client, "Execute async upstream call")
    Rel(sync_api, upstream_client, "Execute sync upstream call")
    Rel(upstream_client, external_service, "HTTP")
    Rel(callback_delivery, client_service, "Callback with result Map<String,String>")
    Rel(callback_delivery, pg, "Read/update ext_callback_delivery")
    Rel(reaper, pg, "Recover stale records")
    Rel(metrics, pg, "Read operational gauges")
```

Главный инвариант Slot Manager:

```text
totalSlots = 5
targetFreeSyncSlots = 1
asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)
```

Async может стартовать только если:

```text
asyncBusy < asyncAllowed
и нет живых sync waiters
```

## Dynamic View. Sync Request

```mermaid
sequenceDiagram
    participant Client as invest-pay / user-expertise
    participant API as Gateway Sync API
    participant Slots as Slot Manager
    participant DB as PostgreSQL
    participant Upstream as External Service

    Client->>API: POST /v1/external/sync
    API->>DB: register sync waiter
    API->>Slots: acquire sync slot, max 5
    Slots->>DB: lease free slot with lease_id
    DB-->>Slots: slot acquired
    Slots-->>API: slot lease
    API->>DB: remove sync waiter
    API->>Upstream: HTTP call
    Upstream-->>API: response
    API->>Slots: release slot by slot_id + lease_id
    Slots->>DB: clear lease
    API-->>Client: 200 result
```

Если слот не получен до `syncWaitTimeout`, gateway удаляет sync waiter и возвращает `429`. Код `503` используется для недоступности gateway или координатора лимитов, а не как обычный ответ на исчерпание sync SLA.

## Dynamic View. Async Request With Callback

```mermaid
sequenceDiagram
    participant Client as user-expertise
    participant API as Gateway Async API
    participant Dispatcher as Async Dispatcher
    participant Slots as Slot Manager
    participant DB as PostgreSQL
    participant Upstream as External Service
    participant Callback as Callback Delivery

    Client->>API: POST /v1/external/async deliveryMode=CALLBACK
    API->>DB: insert task PENDING
    API->>DB: NOTIFY external_gateway_queue
    API-->>Client: 202 taskId

    Dispatcher->>DB: claim next PENDING task
    Dispatcher->>Slots: acquire async slot by sliding reserve
    Slots->>DB: check syncBusy, asyncBusy, sync waiters
    DB-->>Slots: async lease allowed
    Slots-->>Dispatcher: slot lease

    Dispatcher->>Upstream: HTTP call
    Upstream-->>Dispatcher: response
    Dispatcher->>DB: mark task DONE and create callback delivery PENDING atomically
    Dispatcher->>Slots: release slot

    Callback->>DB: claim callback delivery
    Callback->>Client: POST /internal/external-gateway/callbacks
    Client-->>Callback: 200 OK
    Callback->>DB: mark callback DELIVERED
```

Перевод async-задачи в финальный статус и создание записи callback delivery должны быть атомарными: одна транзакция в PostgreSQL или transactional outbox. Иначе рестарт gateway между этими действиями может оставить финальную задачу без доставки callback.

Если callback не доставлен, `Callback Delivery` переводит доставку в retry с backoff. Результат задачи остается доступен через gateway fallback API.

## Dynamic View. Async Fallback Result Read

```mermaid
sequenceDiagram
    participant Client as user-expertise
    participant API as Gateway Async API
    participant DB as PostgreSQL

    Client->>API: GET /v1/external/async/{taskId}
    API->>DB: select task by taskId and clientService from authenticated identity
    DB-->>API: status, result, callbackDeliveryStatus
    API-->>Client: AsyncTask
```

Fallback чтение не требует общей БД между сервисами. `user-expertise` обращается к gateway по API, а gateway читает собственную схему.

## Deployment Notes

```mermaid
C4Deployment
    title External Service Gateway - Deployment View

    Deployment_Node(dc, "Cluster / two legs", "Docker / Kubernetes-like environment") {
        Deployment_Node(app_nodes, "Gateway runtime", "2+ instances") {
            Container(api_1, "Gateway instance #1", "Spring Boot")
            Container(api_2, "Gateway instance #2", "Spring Boot")
        }
        Deployment_Node(db_node, "Database", "PostgreSQL") {
            ContainerDb(pg, "Gateway PostgreSQL", "PostgreSQL")
        }
    }

    System_Ext(external_service, "External Service", "Max 5 concurrent calls")
    System(client_services, "Client services", "invest-pay, user-expertise")

    Rel(client_services, api_1, "Requests")
    Rel(client_services, api_2, "Requests")
    Rel(api_1, pg, "JDBC")
    Rel(api_2, pg, "JDBC")
    Rel(api_1, external_service, "HTTP")
    Rel(api_2, external_service, "HTTP")
```

Все gateway-инстансы должны использовать один логический координатор слотов. Если PostgreSQL раздельный по плечам, лимит `5` превращается в сумму локальных лимитов и перестает быть глобальным.
