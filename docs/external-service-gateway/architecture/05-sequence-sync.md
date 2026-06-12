# Sequence View. Sync Scenarios

Sync API выполняет upstream-вызов в рамках исходного HTTP-запроса клиента. Все сценарии ниже разделены намеренно: каждая диаграмма показывает один путь и одну причину завершения.

В стрелках к `PostgreSQL` имя таблицы указано перед двоеточием, например `ext_slots: acquire SYNC lease`.
Границы транзакций показаны подсвеченными `rect`-блоками и заметками `TX ... begin/commit`.

## S-SYNC-01. Успешный sync с немедленным слотом

Диаграмма описывает базовый happy path: слот доступен сразу, upstream отвечает успешно, gateway освобождает слот и возвращает клиенту `200 OK`.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient

    Note over Client,Sync: Прием HTTP-запроса и передача управления sync-сервису.
    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Note over Sync,DB: Немедленный захват свободного SYNC-слота.
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT ACQUIRE begin
        Slots->>DB: ext_slots: acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT ACQUIRE commit
    end
    Slots-->>Sync: SlotLease
    Note over Sync,Upstream: Единственный upstream-вызов выполняется под lease слота.
    Sync->>Upstream: call(externalId, clientService, payload)
    Upstream-->>Sync: result, upstreamStatus=200
    Note over Sync,DB: Финализация: release слота и best-effort запись sync trace.
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
    Note over Sync,Client: Ответ строится из результата upstream, а не из trace-записи.
    Sync-->>API: ExternalSyncResponse SUCCEEDED
    API-->>Client: 200 OK
```

| Шаг | Лейбл на диаграмме | Что делает шаг |
| --- | --- | --- |
| 1 | `POST /v1/external/sync` | Клиент отправляет синхронный запрос с payload, `externalId`, `clientService` и HTTP-заголовками трассировки. |
| 2 | `sync(request, headers)` | Controller валидирует HTTP-контракт и передает доменный request в `ExternalSyncService`. |
| 3 | `acquireSyncSlot(owner, waitTimeout)` | Sync-сервис просит `SlotManager` получить SYNC lease для владельца `clientService:externalId`. |
| 4 | `ext_slots: acquire SYNC lease` | Репозиторий атомарно занимает свободный слот и записывает `lease_id`, owner и время lease. |
| 5 | `slot_id + lease_id` | PostgreSQL возвращает идентификаторы, по которым можно безопасно освободить только свой lease. |
| 6 | `SlotLease` | `SlotManager` возвращает сервису объект lease для дальнейшего upstream-вызова и release. |
| 7 | `call(externalId, clientService, payload)` | Gateway вызывает внешний сервис, передавая payload и клиентский контекст. |
| 8 | `result, upstreamStatus=200` | Upstream возвращает успешный результат и HTTP-статус, которые попадут в sync response. |
| 9 | `release(slot_id, lease_id)` | Sync-сервис в `finally` инициирует освобождение слота по паре `slot_id + lease_id`. |
| 10 | `ext_slots: clear lease and NOTIFY external_gateway_slot_released` | PostgreSQL очищает lease и отправляет notification ожидающим sync-запросам. |
| 11 | `ext_request_queue: insert sync trace DONE, delivery_mode=SYNC` | Gateway пишет диагностический trace успешного sync-вызова в общую таблицу request queue. |
| 12 | `ExternalSyncResponse SUCCEEDED` | Сервис формирует успешный доменный ответ с результатом upstream и длительностью вызова. |
| 13 | `200 OK` | Controller возвращает клиенту HTTP 200 с телом sync response. |

Особенности:

- слот освобождается в `finally`;
- sync trace является диагностической записью, а не источником ответа клиенту;
- `Idempotency-Key` передается upstream adapter'у, но gateway не хранит sync result по ключу.

## S-SYNC-02. Альтернативный успешный sync через LISTEN/NOTIFY

Диаграмма описывает успешный sync, когда свободного слота сначала нет, но gateway просыпается по PostgreSQL `NOTIFY` и получает слот до истечения wait timeout.

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

    Note over Client,Sync: Запрос принят как обычный sync-вызов.
    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Note over Sync,DB: Первая попытка не находит свободный слот.
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT TRY begin
        Slots->>DB: ext_slots: try acquire SYNC lease
        DB-->>Slots: no free slot
        Note over Slots,DB: TX SLOT TRY commit
    end
    Note over Slots,DB: Регистрация waiter нужна для приоритета sync над async.
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REGISTER begin
        Slots->>DB: ext_sync_waiters: register sync waiter
        Note over Slots,DB: TX WAITER REGISTER commit
    end
    Note over Slots,Wait: LISTEN/NOTIFY ветка ожидания пробуждает retry без активного polling.
    Slots->>Wait: waitBeforeRetry(signalVersion, pause)
    DB-->>Wait: channel external_gateway_slot_released
    Wait-->>Slots: signal observed
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RETRY begin
        Slots->>DB: ext_slots: retry acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT RETRY commit
    end
    Note over Slots,DB: Waiter снимается после успешного захвата.
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REMOVE begin
        Slots->>DB: ext_sync_waiters: remove sync waiter
        Note over Slots,DB: TX WAITER REMOVE commit
    end
    Slots-->>Sync: SlotLease
    Note over Sync,Upstream: После получения lease путь совпадает с обычным happy path.
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

| Шаг | Лейбл на диаграмме | Что делает шаг |
| --- | --- | --- |
| 1 | `POST /v1/external/sync` | Клиент начинает sync-вызов. |
| 2 | `sync(request, headers)` | Controller передает запрос в sync-сервис. |
| 3 | `acquireSyncSlot(owner, waitTimeout)` | Sync-сервис запускает алгоритм захвата слота с ожиданием до `waitTimeout`. |
| 4 | `ext_slots: try acquire SYNC lease` | База проверяет свободные слоты в первой короткой транзакции. |
| 5 | `no free slot` | Свободного слота нет, поэтому upstream еще не вызывается. |
| 6 | `ext_sync_waiters: register sync waiter` | Gateway регистрирует ожидающий sync-запрос, чтобы async-dispatcher учитывал живой waiter. |
| 7 | `waitBeforeRetry(signalVersion, pause)` | Wait strategy блокирует поток до notification или до очередного крайнего срока ожидания. |
| 8 | `channel external_gateway_slot_released` | PostgreSQL сообщает, что какой-то слот освобожден. |
| 9 | `signal observed` | Wait strategy подтверждает пробуждение и разрешает повторную попытку. |
| 10 | `ext_slots: retry acquire SYNC lease` | `SlotManager` повторно проверяет таблицу слотов, потому что notification не является источником истины. |
| 11 | `slot_id + lease_id` | Повторная попытка успешно получает lease. |
| 12 | `ext_sync_waiters: remove sync waiter` | Waiter удаляется, чтобы не блокировать async-запуски после завершения ожидания. |
| 13 | `SlotLease` | Lease передается sync-сервису. |
| 14 | `upstream call` | Gateway выполняет внешний вызов под занятым слотом. |
| 15 | `result` | Upstream возвращает успешный результат. |
| 16 | `release(slot_id, lease_id)` | Sync-сервис освобождает слот. |
| 17 | `ext_slots: clear lease and NOTIFY external_gateway_slot_released` | База очищает lease и будит другие waiters. |
| 18 | `ext_request_queue: insert sync trace DONE` | Gateway сохраняет диагностический trace успешного sync-вызова. |
| 19 | `ExternalSyncResponse SUCCEEDED` | Sync-сервис возвращает доменный success response. |
| 20 | `200 OK` | Controller отдает клиенту успешный HTTP-ответ. |

Особенности:

- этот путь относится к режиму `external-gateway.slots.sync-acquire-wait-mode=listen_notify`;
- PostgreSQL `NOTIFY` используется только как сигнал проснуться и повторно проверить `ext_slots`;
- источником истины остается таблица слотов.

## S-SYNC-03. Альтернативный успешный sync через polling

Диаграмма описывает успешный sync в режиме polling: gateway не ждет PostgreSQL notification, а периодически повторяет попытку захвата слота.

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

    Note over Client,Sync: Клиентский запрос входит в тот же sync contract.
    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Note over Sync,DB: Первая попытка захвата не находит свободный слот.
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT TRY begin
        Slots->>DB: ext_slots: try acquire SYNC lease
        DB-->>Slots: no free slot
        Note over Slots,DB: TX SLOT TRY commit
    end
    Note over Slots,DB: Waiter фиксирует живое sync-ожидание для async gate.
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REGISTER begin
        Slots->>DB: ext_sync_waiters: register sync waiter
        Note over Slots,DB: TX WAITER REGISTER commit
    end
    Note over Slots,DB: Polling-ветка повторяет проверку до появления слота или timeout.
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
    Note over Slots,DB: Waiter снимается после выхода из цикла ожидания.
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REMOVE begin
        Slots->>DB: ext_sync_waiters: remove sync waiter
        Note over Slots,DB: TX WAITER REMOVE commit
    end
    Slots-->>Sync: SlotLease
    Note over Sync,Upstream: Успешный захват переводит сценарий к upstream-вызову.
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

| Шаг | Лейбл на диаграмме | Что делает шаг |
| --- | --- | --- |
| 1 | `POST /v1/external/sync` | Клиент отправляет sync-запрос. |
| 2 | `sync(request, headers)` | Controller передает управление sync-сервису. |
| 3 | `acquireSyncSlot(owner, waitTimeout)` | Сервис запускает захват слота с ограниченным временем ожидания. |
| 4 | `ext_slots: try acquire SYNC lease` | Первая транзакция пытается занять свободный SYNC-слот. |
| 5 | `no free slot` | Слота нет, поэтому gateway переходит к ожиданию. |
| 6 | `ext_sync_waiters: register sync waiter` | Gateway сохраняет waiter, чтобы async не вытеснял ожидающие sync-запросы. |
| 7 | `waitBeforeRetry(pollInterval)` | Polling strategy делает паузу на configured interval или меньше, если timeout ближе. |
| 8 | `polling pause elapsed` | Пауза завершилась, можно снова проверять `ext_slots`. |
| 9 | `ext_slots: retry acquire SYNC lease` | Gateway повторяет атомарный захват слота. |
| 10 | `no free slot or slot_id + lease_id` | Цикл либо продолжается без слота, либо получает lease и выходит к обработке. |
| 11 | `ext_sync_waiters: remove sync waiter` | Waiter удаляется после завершения ожидания. |
| 12 | `SlotLease` | Успешный lease возвращается sync-сервису. |
| 13 | `upstream call` | Gateway выполняет внешний вызов. |
| 14 | `result` | Upstream возвращает результат. |
| 15 | `release(slot_id, lease_id)` | Сервис освобождает занятый слот. |
| 16 | `ext_slots: clear lease and NOTIFY external_gateway_slot_released` | База очищает lease и отправляет notification для других ожидающих запросов. |
| 17 | `ext_request_queue: insert sync trace DONE` | Gateway сохраняет успешный trace. |
| 18 | `ExternalSyncResponse SUCCEEDED` | Sync-сервис возвращает success response. |
| 19 | `200 OK` | Controller отправляет клиенту HTTP 200. |

Особенности:

- этот путь относится к режиму `external-gateway.slots.sync-acquire-wait-mode=polling`;
- gateway не ждет PostgreSQL notification;
- повторная попытка выполняется после `external-gateway.slots.sync-acquire-poll-interval`.

## S-SYNC-04. Sync slot не получен до wait timeout

Диаграмма описывает отказ без upstream-вызова: gateway не получил слот за отведенное время и возвращает клиенту retryable `429`.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Errors as ExternalGatewayExceptionHandler

    Note over Client,Sync: Запрос принят, но выполнение зависит от доступности sync-слота.
    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Note over Sync,DB: Захват слота начинается с обычной попытки.
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT TRY begin
        Slots->>DB: ext_slots: try acquire SYNC lease
        DB-->>Slots: no free slot
        Note over Slots,DB: TX SLOT TRY commit
    end
    Note over Slots,DB: Waiter регистрируется на время ожидания.
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX WAITER REGISTER begin
        Slots->>DB: ext_sync_waiters: register sync waiter
        Note over Slots,DB: TX WAITER REGISTER commit
    end
    Note over Slots,DB: Ветка timeout повторяет проверки, но не получает lease.
    loop until waitTimeout
        rect rgb(238, 246, 255)
            Note over Slots,DB: TX SLOT RETRY begin
            Slots->>DB: ext_slots: retry acquire SYNC lease
            DB-->>Slots: no free slot
            Note over Slots,DB: TX SLOT RETRY commit
        end
    end
    Note over Slots,DB: Перед отказом gateway убирает waiter и пишет trace.
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
    Note over Sync,Client: Ошибка мапится в retryable HTTP-ответ без обращения к upstream.
    Sync--xAPI: NoSlotAvailableException
    API->>Errors: handleGatewayException
    Errors-->>Client: 429 NO_SLOT_AVAILABLE, Retry-After: 1
```

| Шаг | Лейбл на диаграмме | Что делает шаг |
| --- | --- | --- |
| 1 | `POST /v1/external/sync` | Клиент отправляет sync-запрос. |
| 2 | `sync(request, headers)` | Controller вызывает sync-сервис. |
| 3 | `acquireSyncSlot(owner, waitTimeout)` | Сервис пытается получить слот до истечения configured timeout. |
| 4 | `ext_slots: try acquire SYNC lease` | Первая проверка таблицы слотов не находит свободный lease. |
| 5 | `no free slot` | Gateway понимает, что upstream нельзя вызывать сейчас. |
| 6 | `ext_sync_waiters: register sync waiter` | Waiter фиксируется для честной очередности между sync и async. |
| 7 | `ext_slots: retry acquire SYNC lease` | Gateway повторяет проверку доступности слота в цикле ожидания. |
| 8 | `no free slot` | Очередная попытка также не получает lease; цикл продолжается до timeout. |
| 9 | `ext_sync_waiters: remove sync waiter` | Waiter удаляется, чтобы не оставлять ложный sync backlog. |
| 10 | `Optional.empty` | `SlotManager` сообщает, что слот получить не удалось. |
| 11 | `ext_request_queue: insert sync trace FAILED, code=NO_SLOT_AVAILABLE, attempts=0` | Gateway пишет диагностическую запись отказа без upstream-попытки. |
| 12 | `NoSlotAvailableException` | Sync-сервис выбрасывает доменную ошибку отсутствия слота. |
| 13 | `handleGatewayException` | Exception handler преобразует ошибку в HTTP-контракт. |
| 14 | `429 NO_SLOT_AVAILABLE, Retry-After: 1` | Клиент получает retryable ответ с подсказкой, когда повторить запрос. |

Особенности:

- сценарий считается retryable;
- клиент может повторить sync-вызов после `Retry-After`;
- из-за отсутствия сохраненной sync-idempotency повтор может привести к новому upstream-вызову, если предыдущий вызов успел стартовать в другом сценарии.

## S-SYNC-05. Upstream timeout

Диаграмма описывает ошибку после успешного захвата слота: upstream не ответил вовремя, слот освобождается, клиент получает `504`.

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

    Note over Client,Sync: Запрос принят и передан в sync-сервис.
    Client->>API: POST /v1/external/sync
    API->>Sync: sync(request, headers)
    Note over Sync,DB: Слот успешно занят до upstream-вызова.
    Sync->>Slots: acquireSyncSlot(owner, waitTimeout)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT ACQUIRE begin
        Slots->>DB: ext_slots: acquire SYNC lease
        DB-->>Slots: slot_id + lease_id
        Note over Slots,DB: TX SLOT ACQUIRE commit
    end
    Slots-->>Sync: SlotLease
    Note over Sync,Upstream: Ветка ошибки возникает во время внешнего вызова.
    Sync->>Upstream: call(...)
    Upstream--xSync: UpstreamTimeoutException
    Note over Sync,DB: Даже при timeout release слота выполняется до ответа клиенту.
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
    Note over Sync,Client: Timeout мапится в gateway error response.
    Sync--xAPI: UpstreamTimeoutException
    API->>Errors: handleGatewayException
    Errors-->>Client: 504 UPSTREAM_TIMEOUT
```

| Шаг | Лейбл на диаграмме | Что делает шаг |
| --- | --- | --- |
| 1 | `POST /v1/external/sync` | Клиент отправляет sync-запрос. |
| 2 | `sync(request, headers)` | Controller передает запрос сервису. |
| 3 | `acquireSyncSlot(owner, waitTimeout)` | Сервис получает слот перед upstream-вызовом. |
| 4 | `ext_slots: acquire SYNC lease` | PostgreSQL занимает слот для sync-владельца. |
| 5 | `slot_id + lease_id` | База возвращает lease-идентификаторы. |
| 6 | `SlotLease` | `SlotManager` возвращает lease сервису. |
| 7 | `call(...)` | Gateway вызывает внешний сервис. |
| 8 | `UpstreamTimeoutException` | Upstream call завершается timeout-исключением. |
| 9 | `release(slot_id, lease_id)` | Sync-сервис освобождает слот в `finally`. |
| 10 | `ext_slots: clear lease and NOTIFY external_gateway_slot_released` | База очищает lease и будит ожидателей. |
| 11 | `ext_request_queue: insert sync trace FAILED, code=UPSTREAM_TIMEOUT, attempts=1` | Gateway пишет trace failed-вызова с одной upstream-попыткой. |
| 12 | `UpstreamTimeoutException` | Ошибка пробрасывается из sync-сервиса в controller. |
| 13 | `handleGatewayException` | Exception handler строит HTTP-ошибку gateway. |
| 14 | `504 UPSTREAM_TIMEOUT` | Клиент получает timeout-ответ. |

Особенности:

- timeout upstream не оставляет слот занятым;
- ответ retryable;
- безопасность повторного sync-вызова зависит от идемпотентности upstream.

## S-SYNC-06. Client timeout или disconnect после захвата слота

Диаграмма описывает ситуацию, когда клиентское соединение закрывается после захвата слота, а серверный поток продолжает upstream-вызов и освобождает ресурс.

```mermaid
sequenceDiagram
    autonumber
    participant Client as Сервис-клиент
    participant API as ExternalSyncController
    participant Sync as ExternalSyncService
    participant Slots as SlotManager
    participant DB as PostgreSQL
    participant Upstream as ExternalUpstreamClient

    Note over Client,Sync: Запрос стартует штатно и успевает занять слот.
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
    Note over Client,Upstream: Ветка disconnect влияет на доставку ответа, но не отменяет серверную работу автоматически.
    Client--xAPI: HTTP client timeout / disconnect
    Sync->>Upstream: upstream call continues in server thread
    Upstream-->>Sync: result or error
    Note over Sync,DB: Gateway все равно финализирует слот и trace best-effort.
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
    Note over API,Client: Итоговый HTTP-ответ уже невозможно доставить клиенту.
    API--xClient: response cannot be delivered
```

| Шаг | Лейбл на диаграмме | Что делает шаг |
| --- | --- | --- |
| 1 | `POST /v1/external/sync` | Клиент отправляет sync-запрос. |
| 2 | `sync(request, headers)` | Controller вызывает sync-сервис. |
| 3 | `acquireSyncSlot(owner, waitTimeout)` | Сервис запрашивает lease слота. |
| 4 | `ext_slots: acquire SYNC lease` | PostgreSQL занимает свободный SYNC-слот. |
| 5 | `slot_id + lease_id` | База возвращает lease-идентификаторы. |
| 6 | `SlotLease` | Lease передается sync-сервису. |
| 7 | `HTTP client timeout / disconnect` | Клиент перестает ждать ответ или закрывает соединение. |
| 8 | `upstream call continues in server thread` | Серверный поток продолжает выполнение, если приложение явно не отменило работу. |
| 9 | `result or error` | Upstream возвращает результат или ошибку, но клиент уже недоступен. |
| 10 | `release(slot_id, lease_id)` | Gateway освобождает слот независимо от состояния клиентского соединения. |
| 11 | `ext_slots: clear lease and NOTIFY external_gateway_slot_released` | База очищает lease и сообщает ожидателям. |
| 12 | `ext_request_queue: insert sync trace best-effort` | Gateway пытается сохранить диагностический итог; ошибка записи не меняет уже произошедший upstream-вызов. |
| 13 | `response cannot be delivered` | HTTP-ответ не доставляется, потому что клиентское соединение закрыто. |

Особенности:

- gateway должен гарантировать release слота независимо от состояния клиентского соединения;
- клиентский retry может создать повторный upstream-вызов, потому что sync result не хранится по `Idempotency-Key`;
- для критичных операций нужно либо внедрить sync idempotency storage, либо переводить их в async contract.

## S-SYNC-07. Ошибка записи sync trace после успешного upstream

Диаграмма описывает best-effort характер sync trace: upstream-вызов уже успешен, поэтому ошибка записи trace логируется, но клиент получает `200 OK`.

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

    Note over Client,Sync: Основной sync-вызов проходит штатно.
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
    Note over Sync,DB: Слот освобождается до записи диагностического trace.
    Sync->>Slots: release(slot_id, lease_id)
    rect rgb(238, 246, 255)
        Note over Slots,DB: TX SLOT RELEASE begin
        Slots->>DB: ext_slots: clear lease and NOTIFY external_gateway_slot_released
        Note over Slots,DB: TX SLOT RELEASE commit
    end
    Note over Sync,Log: Ветка ошибки trace не меняет успешный бизнес-результат.
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

| Шаг | Лейбл на диаграмме | Что делает шаг |
| --- | --- | --- |
| 1 | `POST /v1/external/sync` | Клиент отправляет sync-запрос. |
| 2 | `sync(request, headers)` | Controller передает запрос в sync-сервис. |
| 3 | `acquireSyncSlot(owner, waitTimeout)` | Сервис получает слот. |
| 4 | `ext_slots: acquire SYNC lease` | PostgreSQL занимает SYNC-слот. |
| 5 | `slot_id + lease_id` | База возвращает lease-идентификаторы. |
| 6 | `SlotLease` | Lease передается сервису. |
| 7 | `upstream call` | Gateway выполняет внешний вызов. |
| 8 | `result` | Upstream успешно возвращает результат. |
| 9 | `release(slot_id, lease_id)` | Сервис освобождает слот. |
| 10 | `ext_slots: clear lease and NOTIFY external_gateway_slot_released` | База очищает lease и будит waiters. |
| 11 | `ext_request_queue: insert sync trace DONE` | Gateway пытается записать успешный trace. |
| 12 | `persistence error` | Хранилище trace возвращает ошибку, транзакция trace откатывается. |
| 13 | `warn "Не удалось сохранить trace sync-запроса"` | Ошибка фиксируется в application log как потеря наблюдаемости. |
| 14 | `ExternalSyncResponse SUCCEEDED` | Sync-сервис сохраняет успешный клиентский результат. |
| 15 | `200 OK` | Controller возвращает клиенту HTTP 200. |

Особенности:

- trace write является best-effort наблюдаемостью;
- потеря trace не должна превращать успешный upstream-вызов в клиентскую ошибку;
- для расследований нужен application log, потому что строка trace может отсутствовать.
