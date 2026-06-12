# Sequence View. Callback Scenarios

Callback-доставка отделена от upstream-обработки. Это позволяет завершить async-задачу в `DONE`, `DEAD`, `FAILED` или `CANCELLED`, а затем независимо добиваться доставки результата клиенту.

В стрелках к `PostgreSQL` имя таблицы указано перед двоеточием, например `ext_callback_delivery: UPDATE status=DELIVERED`.
Границы транзакций показаны подсвеченными `rect`-блоками и заметками `TX ... begin/commit`.

## S-CALLBACK-01. Успешная callback-доставка

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Callback Scheduler
    participant Dispatcher as CallbackDeliveryDispatcher
    participant Deliveries as CallbackDeliveryRepository
    participant Tasks as AsyncTaskRepository
    participant DB as PostgreSQL
    participant Client as Callback endpoint клиента

    Scheduler->>Dispatcher: dispatchBatch(deliveryBatchSize)
    Dispatcher->>Deliveries: claimNextPending(now)
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK CLAIM begin
        Deliveries->>DB: ext_callback_delivery: UPDATE PENDING/RETRY to DELIVERING, attempts + 1, eventId=new UUID
        DB-->>Deliveries: CallbackDelivery
        Note over Deliveries,DB: TX CALLBACK CLAIM commit
    end
    Deliveries-->>Dispatcher: delivery
    Dispatcher->>Tasks: updateCallbackDeliveryStatus(DELIVERING)
    rect rgb(245, 245, 245)
        Note over Tasks,DB: TX TASK CALLBACK STATUS begin
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=DELIVERING
        Note over Tasks,DB: TX TASK CALLBACK STATUS commit
    end
    Dispatcher->>Client: POST /internal/external-gateway/callbacks
    Client-->>Dispatcher: 200 or 204
    Dispatcher->>Deliveries: markDelivered(deliveryId)
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK FINALIZE begin
        Deliveries->>DB: ext_callback_delivery: UPDATE status=DELIVERED, completed_at=now
        Note over Deliveries,DB: TX CALLBACK FINALIZE commit
    end
    Dispatcher->>Tasks: updateCallbackDeliveryStatus(DELIVERED)
    rect rgb(245, 245, 245)
        Note over Tasks,DB: TX TASK CALLBACK STATUS begin
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=DELIVERED
        Note over Tasks,DB: TX TASK CALLBACK STATUS commit
    end
```

Callback endpoint клиента должен быть идемпотентным по `taskId`, `status` и `eventId`.

## S-CALLBACK-02. Callback endpoint вернул 5xx или timeout, попытки остались

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as CallbackDeliveryDispatcher
    participant Deliveries as CallbackDeliveryRepository
    participant Tasks as AsyncTaskRepository
    participant DB as PostgreSQL
    participant Client as Callback endpoint клиента

    Dispatcher->>Deliveries: claimNextPending(now)
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK CLAIM begin
        Deliveries->>DB: ext_callback_delivery: UPDATE status=DELIVERING, attempts + 1
        Note over Deliveries,DB: TX CALLBACK CLAIM commit
    end
    Deliveries-->>Dispatcher: delivery
    Dispatcher->>Tasks: updateCallbackDeliveryStatus(DELIVERING)
    rect rgb(245, 245, 245)
        Note over Tasks,DB: TX TASK CALLBACK STATUS begin
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=DELIVERING
        Note over Tasks,DB: TX TASK CALLBACK STATUS commit
    end
    Dispatcher->>Client: POST callback
    Client--xDispatcher: HTTP 5xx or timeout
    Dispatcher->>Deliveries: markRetryOrDead(deliveryId, error, retryBackoff)
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK FINALIZE begin
        Deliveries->>DB: ext_callback_delivery: UPDATE status=RETRY, available_at=now+backoff, last_error
        Note over Deliveries,DB: TX CALLBACK FINALIZE commit
    end
    Deliveries-->>Dispatcher: updated delivery
    Dispatcher->>Tasks: updateCallbackDeliveryStatus(RETRY)
    rect rgb(245, 245, 245)
        Note over Tasks,DB: TX TASK CALLBACK STATUS begin
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=RETRY
        Note over Tasks,DB: TX TASK CALLBACK STATUS commit
    end
```

Ошибки callback-доставки не переводят async-задачу из `DONE` или `DEAD` в другой upstream-статус.

## S-CALLBACK-03. Callback attempts исчерпаны

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as CallbackDeliveryDispatcher
    participant Deliveries as CallbackDeliveryRepository
    participant Tasks as AsyncTaskRepository
    participant DB as PostgreSQL
    participant Client as Callback endpoint клиента

    Dispatcher->>Deliveries: claimNextPending(now)
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK CLAIM begin
        Deliveries->>DB: ext_callback_delivery: UPDATE RETRY to DELIVERING, attempts=maxAttempts
        Note over Deliveries,DB: TX CALLBACK CLAIM commit
    end
    Deliveries-->>Dispatcher: delivery
    Dispatcher->>Tasks: updateCallbackDeliveryStatus(DELIVERING)
    rect rgb(245, 245, 245)
        Note over Tasks,DB: TX TASK CALLBACK STATUS begin
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=DELIVERING
        Note over Tasks,DB: TX TASK CALLBACK STATUS commit
    end
    Dispatcher->>Client: POST callback
    Client--xDispatcher: timeout or non-2xx
    Dispatcher->>Deliveries: markRetryOrDead(deliveryId, error, retryBackoff)
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK FINALIZE begin
        Deliveries->>DB: ext_callback_delivery: UPDATE status=DEAD, completed_at=now, last_error
        Note over Deliveries,DB: TX CALLBACK FINALIZE commit
    end
    Deliveries-->>Dispatcher: updated delivery
    Dispatcher->>Tasks: updateCallbackDeliveryStatus(DEAD)
    rect rgb(245, 245, 245)
        Note over Tasks,DB: TX TASK CALLBACK STATUS begin
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=DEAD
        Note over Tasks,DB: TX TASK CALLBACK STATUS commit
    end
```

После `DEAD` результат остается доступен через polling API. Для production нужен operational process: алерт, ручное расследование и возможность безопасного повторного уведомления, если такая операция будет добавлена.

## S-CALLBACK-04. Callback URL отсутствует в allow-list

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as ExternalAsyncDispatcher
    participant Planner as CallbackDeliveryPlanner
    participant Config as ExternalGatewayClientsProperties
    participant Deliveries as CallbackDeliveryRepository
    participant Tasks as AsyncTaskRepository
    participant DB as PostgreSQL

    Dispatcher->>Planner: planForFinalTask(task DONE/DEAD, deliveryMode=CALLBACK)
    Planner->>Config: callbackUrl(clientService)
    Config-->>Planner: Optional.empty
    Planner->>Deliveries: createDead(task, "Callback URL не настроен")
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK CREATE DEAD begin
        Deliveries->>DB: ext_callback_delivery: INSERT status=DEAD, callback_url=NULL
        Note over Deliveries,DB: TX CALLBACK CREATE DEAD commit
    end
    Planner->>Tasks: updateCallbackDeliveryStatus(DEAD)
    rect rgb(245, 245, 245)
        Note over Tasks,DB: TX TASK CALLBACK STATUS begin
        Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=DEAD
        Note over Tasks,DB: TX TASK CALLBACK STATUS commit
    end
    Planner-->>Dispatcher: delivery DEAD
```

Gateway не использует произвольный `callbackUrl` из request body. Это защищает от SSRF, но требует заранее настроить `external-gateway.clients.<clientService>.callback-url`.

## S-CALLBACK-05. Recovery зависшей DELIVERING-доставки

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Callback Scheduler
    participant Dispatcher as CallbackDeliveryDispatcher
    participant Deliveries as CallbackDeliveryRepository
    participant Tasks as AsyncTaskRepository
    participant DB as PostgreSQL

    Scheduler->>Dispatcher: recoverTimedOutDeliveries()
    Dispatcher->>Deliveries: recoverTimedOutDeliveries(now-timeout, message, backoff, now)
    rect rgb(238, 246, 255)
        Note over Deliveries,DB: TX CALLBACK RECOVERY begin
        Deliveries->>DB: ext_callback_delivery: UPDATE DELIVERING older than timeout
        alt attempts left
            DB-->>Deliveries: status=RETRY, available_at=now+backoff
        else max attempts reached
            DB-->>Deliveries: status=DEAD, completed_at=now
        end
        Note over Deliveries,DB: TX CALLBACK RECOVERY commit
    end
    loop each recovered delivery
        Dispatcher->>Tasks: updateCallbackDeliveryStatus(recovered status)
        rect rgb(245, 245, 245)
            Note over Tasks,DB: TX TASK CALLBACK STATUS begin
            Tasks->>DB: ext_request_queue: UPDATE callback_delivery_status=recovered status
            Note over Tasks,DB: TX TASK CALLBACK STATUS commit
        end
    end
```

Recovery нужен на случай падения JVM или зависания HTTP client после claim доставки.

## S-CALLBACK-06. Клиент получил duplicate callback

```mermaid
sequenceDiagram
    autonumber
    participant Dispatcher as CallbackDeliveryDispatcher
    participant Client as Callback endpoint клиента
    participant ClientDB as БД сервиса-клиента

    Dispatcher->>Client: POST callback eventId=A, taskId=123, status=DONE
    Client->>ClientDB: insert or upsert processing marker by taskId/status/eventId
    ClientDB-->>Client: processed
    Client-->>Dispatcher: 204 No Content
    Dispatcher->>Client: POST callback retry or duplicate delivery
    Client->>ClientDB: check existing marker/result
    ClientDB-->>Client: already processed
    Client-->>Dispatcher: 200 or 204
```

Это требование к сервисам-клиентам. Gateway выполняет at-least-once callback delivery, а не exactly-once доставку.
