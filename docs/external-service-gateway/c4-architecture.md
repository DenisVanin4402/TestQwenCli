# C4 Architecture View

Документ фиксирует C4-представление текущего `external-service-gateway` в PostgreSQL-варианте: контекст, контейнеры, ключевые компоненты и динамические сценарии. Sequence-диаграммы показывают главных участников процесса; внутренние Java-классы намеренно не раскрываются.

## Level 1. System Context

```mermaid
C4Context
    title External Service Gateway - System Context

    Person_Ext(operator, "Support / Operations", "Monitors requests, queue state and incidents")

    System(invest_pay, "invest-pay", "Client service with its own data model")
    System(user_expertise, "user-expertise", "Client service with its own data model")
    System(gateway, "external-service-gateway", "Internal Spring Boot gateway for throttled and prioritized calls")
    System_Ext(external_service, "External Service", "Third-party service with max 5 concurrent calls")

    Rel(invest_pay, gateway, "Sync and async requests", "HTTP/JSON")
    Rel(user_expertise, gateway, "Sync and async requests", "HTTP/JSON")
    Rel(gateway, invest_pay, "Async result callback", "HTTP callback")
    Rel(gateway, user_expertise, "Async result callback", "HTTP callback")
    Rel(gateway, external_service, "Throttled upstream calls, max 5 in-flight", "HTTP")
    Rel(operator, gateway, "Observes logs, errors and task states")
```

`invest-pay` и `user-expertise` не обращаются к таблицам gateway напрямую. Интеграция идет только через HTTP API gateway и callback-контракт.

## Level 2. Container View

```mermaid
C4Container
    title External Service Gateway - Container View

    System(invest_pay, "invest-pay", "Client service")
    System(user_expertise, "user-expertise", "Client service")
    System_Ext(external_service, "External Service", "Max 5 concurrent calls")

    System_Boundary(gateway_boundary, "external-service-gateway") {
        Container(app, "Gateway Application", "Spring Boot 3 / Java 21", "REST API, slot coordination, async dispatching, callback delivery")
        ContainerDb(pg, "Gateway PostgreSQL", "PostgreSQL", "ext_slots, ext_sync_waiters, ext_request_queue, ext_callback_delivery")
    }

    Rel(invest_pay, app, "POST sync, POST async, GET async result", "HTTP/JSON")
    Rel(user_expertise, app, "POST sync, POST async, GET async result", "HTTP/JSON")
    Rel(app, invest_pay, "POST callback with result/error", "HTTP callback")
    Rel(app, user_expertise, "POST callback with result/error", "HTTP callback")
    Rel(app, pg, "Acquire leases, claim tasks, save results, track callback delivery", "JDBC")
    Rel(app, external_service, "Upstream call after lease acquisition", "HTTP")
```

Глобальный лимит обеспечивается общей PostgreSQL-схемой, а не локальным состоянием инстанса приложения.

## Level 3. Gateway Component View

```mermaid
C4Component
    title External Service Gateway - Component View

    ContainerDb(pg, "Gateway PostgreSQL", "PostgreSQL", "Coordination and persistence")
    System_Ext(external_service, "External Service", "Max 5 concurrent calls")
    System(client_service, "Client Service", "invest-pay / user-expertise")

    Container_Boundary(app, "Gateway Application") {
        Component(sync_api, "Sync API", "Spring MVC", "Accepts sync calls and waits for a SYNC lease")
        Component(async_api, "Async API", "Spring MVC", "Submits tasks and exposes fallback reads, cancel and retry")
        Component(slot_coordination, "Slot Coordination", "Service + JDBC", "Maintains lease-based global slots and sliding sync reserve")
        Component(async_dispatcher, "Async Dispatcher", "Scheduled worker", "Claims async tasks and runs upstream calls")
        Component(callback_dispatcher, "Callback Dispatcher", "Scheduled worker", "Delivers final async results to client services")
        Component(listen_notify, "LISTEN/NOTIFY Worker", "PostgreSQL", "Wakes waiting sync requests when a slot may be free")
        Component(upstream_client, "Upstream Client", "Adapter", "Current code uses simulated upstream response")
    }

    Rel(client_service, sync_api, "POST /v1/external/sync")
    Rel(client_service, async_api, "POST/GET/DELETE/POST retry async")
    Rel(sync_api, slot_coordination, "Acquire SYNC lease")
    Rel(async_api, pg, "Insert/read/update async task")
    Rel(async_dispatcher, pg, "Claim next task with SKIP LOCKED")
    Rel(async_dispatcher, slot_coordination, "Acquire ASYNC lease")
    Rel(slot_coordination, pg, "Read/update ext_slots and ext_sync_waiters")
    Rel(listen_notify, pg, "LISTEN external_gateway_slot_released")
    Rel(slot_coordination, listen_notify, "Wait for release signal in listen_notify mode")
    Rel(sync_api, upstream_client, "Call upstream")
    Rel(async_dispatcher, upstream_client, "Call upstream")
    Rel(upstream_client, external_service, "HTTP")
    Rel(callback_dispatcher, pg, "Claim/update ext_callback_delivery")
    Rel(callback_dispatcher, client_service, "POST callback")
```

Основной инвариант:

```text
totalSlots = 5
targetFreeSyncSlots = 1
asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)
async стартует только если asyncBusy < asyncAllowed и нет живых sync waiters
```

## Dynamic View. Sync Success

```mermaid
sequenceDiagram
    participant Client as Client service
    participant Gateway as Gateway
    participant DB as PostgreSQL
    participant Upstream as External Service

    Client->>Gateway: POST /v1/external/sync
    Gateway->>DB: acquire SYNC lease
    DB-->>Gateway: slot_id + lease_id
    Gateway->>Upstream: upstream call
    Upstream-->>Gateway: result
    Gateway->>DB: release slot
    Gateway-->>Client: 200 SUCCEEDED
```

## Dynamic View. Sync No Slot

```mermaid
sequenceDiagram
    participant Client as Client service
    participant Gateway as Gateway
    participant DB as PostgreSQL
    participant Timer as Timeout

    Client->>Gateway: POST /v1/external/sync
    Gateway->>DB: acquire SYNC lease
    DB-->>Gateway: no slot
    Gateway->>DB: register sync waiter
    loop until sync timeout
        Timer-->>Gateway: wait interval
        Gateway->>DB: retry acquire SYNC lease
        DB-->>Gateway: no slot
    end
    Gateway->>DB: remove sync waiter
    Gateway-->>Client: 429 NO_SLOT_AVAILABLE
```

## Dynamic View. Sync LISTEN/NOTIFY Fallback

```mermaid
sequenceDiagram
    participant Client as Client service
    participant Gateway as Gateway
    participant DB as PostgreSQL
    participant Listener as LISTEN worker
    participant Timer as Fallback timer
    participant Upstream as External Service

    Client->>Gateway: POST /v1/external/sync
    Gateway->>DB: acquire SYNC lease
    DB-->>Gateway: no slot
    Gateway->>DB: register sync waiter
    Gateway->>Listener: wait for NOTIFY
    Note over DB,Listener: NOTIFY is missed or listener reconnects
    Timer-->>Gateway: fallback interval elapsed
    Gateway->>DB: retry acquire SYNC lease
    DB-->>Gateway: slot_id + lease_id
    Gateway->>DB: remove sync waiter
    Gateway->>Upstream: upstream call
    Upstream-->>Gateway: result
    Gateway->>DB: release slot
    Gateway-->>Client: 200 SUCCEEDED
```

## Dynamic View. Sync Upstream Failure

```mermaid
sequenceDiagram
    participant Client as Client service
    participant Gateway as Gateway
    participant DB as PostgreSQL
    participant Upstream as External Service

    Client->>Gateway: POST /v1/external/sync
    Gateway->>DB: acquire SYNC lease
    DB-->>Gateway: slot_id + lease_id
    Gateway->>Upstream: upstream call
    Upstream--xGateway: timeout / interrupted / transport error
    Gateway->>DB: release slot
    Gateway-->>Client: 5xx error response
```

## Dynamic View. Async Success With Callback

```mermaid
sequenceDiagram
    participant Client as Client service
    participant Gateway as Gateway
    participant Dispatcher as Async dispatcher
    participant DB as PostgreSQL
    participant Upstream as External Service
    participant Callback as Callback dispatcher

    Client->>Gateway: POST /v1/external/async
    Gateway->>DB: insert task PENDING
    Gateway-->>Client: 202 taskId
    Dispatcher->>DB: claim task and acquire ASYNC lease
    DB-->>Dispatcher: task + lease
    Dispatcher->>Upstream: upstream call
    Upstream-->>Dispatcher: result
    Dispatcher->>DB: mark DONE, create callback delivery, release slot
    Callback->>DB: claim callback delivery
    Callback->>Client: POST callback
    Client-->>Callback: 200/204
    Callback->>DB: mark DELIVERED
```

## Dynamic View. Async Slot Unavailable

```mermaid
sequenceDiagram
    participant Dispatcher as Async dispatcher
    participant DB as PostgreSQL

    Dispatcher->>DB: claim next PENDING task
    Dispatcher->>DB: acquire ASYNC lease by sync reserve
    DB-->>Dispatcher: no slot allowed
    Dispatcher->>DB: return task to PENDING
```

## Dynamic View. Async Upstream Failure

```mermaid
sequenceDiagram
    participant Dispatcher as Async dispatcher
    participant DB as PostgreSQL
    participant Upstream as External Service

    Dispatcher->>DB: claim task and acquire ASYNC lease
    DB-->>Dispatcher: task + lease
    Dispatcher->>Upstream: upstream call
    Upstream--xDispatcher: timeout / runtime error
    alt attempts left
        Dispatcher->>DB: PENDING with retry backoff
    else max attempts reached
        Dispatcher->>DB: DEAD with error and callback delivery if needed
    end
    Dispatcher->>DB: release slot
```

## Dynamic View. Callback Failure

```mermaid
sequenceDiagram
    participant Client as Client service
    participant Callback as Callback dispatcher
    participant DB as PostgreSQL
    participant Timer as Retry timer

    Callback->>DB: claim callback delivery
    Callback->>Client: POST /internal/external-gateway/callbacks
    Client--xCallback: timeout / 5xx
    alt attempts left
        Callback->>DB: mark RETRY with backoff
        Timer-->>Callback: retry interval elapsed
    else max attempts reached
        Callback->>DB: mark DEAD
    end
```

## Dynamic View. Async Fallback Read

```mermaid
sequenceDiagram
    participant Client as Client service
    participant Gateway as Gateway
    participant DB as PostgreSQL

    Client->>Gateway: GET /v1/external/async/{taskId}
    Gateway->>DB: select task by taskId and optional X-Client-Service
    DB-->>Gateway: task state, result, error
    Gateway-->>Client: AsyncTask
```

## Deployment Notes

```mermaid
C4Deployment
    title External Service Gateway - Deployment View

    Deployment_Node(cluster, "Cluster / two legs", "Docker / Kubernetes-like environment") {
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

Все gateway-инстансы должны использовать один PostgreSQL-координатор. Если PostgreSQL разделен по плечам, лимит `5` превращается в сумму локальных лимитов и перестает быть глобальным.
