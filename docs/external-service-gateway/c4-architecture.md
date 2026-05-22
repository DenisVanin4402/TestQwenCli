# Архитектурное представление C4

Документ фиксирует C4-представление `external-service-gateway`: от контекста системы до внутренних компонентов шлюза. Диаграммы отражают текущие архитектурные решения:

- отдельный сервис-шлюз между доменными сервисами и внешним сервисом;
- общий лимит `5` одновременных вызовов;
- скользящий sync-резерв;
- async-обратный вызов в сервис-клиент;
- PostgreSQL как координатор слотов, очереди и доставки обратных вызовов.

## Уровень 1. Контекст системы

```mermaid
C4Context
    title Шлюз внешнего сервиса - контекст системы

    Person_Ext(operator, "Поддержка / эксплуатация", "Следит за очередями, задачами DEAD, обратными вызовами и инцидентами")

    System(invest_pay, "invest-pay", "Доменный сервис со своей схемой БД")
    System(user_expertise, "user-expertise", "Доменный сервис со своей схемой БД")
    System(gateway, "external-service-gateway", "Внутренний Spring Boot шлюз для приоритизированных вызовов с лимитом")
    System_Ext(external_service, "Внешний сервис", "Сторонний сервис с лимитом 5 одновременных вызовов")

    Rel(invest_pay, gateway, "Sync- и async-запросы", "HTTP/JSON")
    Rel(user_expertise, gateway, "Sync- и async-запросы", "HTTP/JSON")
    Rel(gateway, invest_pay, "Обратный вызов с async-результатом", "HTTP")
    Rel(gateway, user_expertise, "Обратный вызов с async-результатом", "HTTP")
    Rel(gateway, external_service, "Вызовы с ограничением, максимум 5 одновременно", "HTTP")
    Rel(operator, gateway, "Наблюдает метрики, логи и состояния задач")
```

Ключевой смысл контекста: `invest-pay` и `user-expertise` не делят общую схему БД. Они интегрируются только через API шлюза. Все прямые вызовы внешнего сервиса должны быть удалены или запрещены сетевой политикой.

## Уровень 2. Представление контейнеров

```mermaid
C4Container
    title Шлюз внешнего сервиса - представление контейнеров

    System(invest_pay, "invest-pay", "Сервис-клиент")
    System(user_expertise, "user-expertise", "Сервис-клиент")
    System_Ext(external_service, "Внешний сервис", "Максимум 5 одновременных вызовов")

    System_Boundary(gateway_boundary, "external-service-gateway") {
        Container(api_app, "Приложение шлюза", "Spring Boot", "REST API, управление слотами, диспетчеризация очереди, доставка обратных вызовов")
        ContainerDb(pg, "PostgreSQL шлюза", "PostgreSQL", "Слоты, async-очередь, ожидающие sync-запросы, доставка обратных вызовов, аудит")
    }

    Rel(invest_pay, api_app, "POST /v1/external/sync, POST /v1/external/async, GET async-результата", "HTTP/JSON")
    Rel(user_expertise, api_app, "POST /v1/external/sync, POST /v1/external/async, GET async-результата", "HTTP/JSON")
    Rel(api_app, invest_pay, "POST обратного вызова с result/error", "HTTP")
    Rel(api_app, user_expertise, "POST обратного вызова с result/error", "HTTP")
    Rel(api_app, pg, "Занять слоты, забрать задачи, сохранить результаты, отслеживать обратные вызовы", "JDBC")
    Rel(api_app, external_service, "Вызовы внешнего сервиса", "HTTP")
```

`Приложение шлюза` может быть запущено в нескольких инстансах. Глобальный лимит обеспечивается не локальным пулом потоков, а общей PostgreSQL-схемой шлюза.

Если два датацентра/плеча не имеют общего координатора, глобальный лимит `5` невозможен без отдельного соглашения о квотах, например `3 + 2`.

## Уровень 3. Представление компонентов шлюза

```mermaid
C4Component
    title Шлюз внешнего сервиса - представление компонентов

    ContainerDb(pg, "PostgreSQL шлюза", "PostgreSQL", "Координация и хранение состояния")
    System_Ext(external_service, "Внешний сервис", "Максимум 5 одновременных вызовов")
    System(client_service, "Сервис-клиент", "invest-pay / user-expertise")

    Container_Boundary(app, "Приложение шлюза") {
        Component(sync_api, "Sync API", "Spring MVC", "Принимает sync-вызовы и ждет слот")
        Component(async_api, "Async API", "Spring MVC", "Принимает async-задачи и дает резервное чтение результата")
        Component(slot_manager, "Управление слотами", "Сервис", "Ведет глобальные lease-слоты и скользящий sync-резерв")
        Component(queue_repo, "Репозиторий очереди", "JDBC", "Хранит async-задачи и забирает работу через SKIP LOCKED")
        Component(dispatcher, "Async-диспетчер", "Плановый обработчик + слушатель NOTIFY", "Стартует async-работу только когда это разрешает политика приоритета")
        Component(upstream_client, "Клиент внешнего сервиса", "HTTP-клиент", "Вызывает внешний сервис с таймаутом, повторами и автоматическим выключателем")
        Component(callback_delivery, "Доставка обратных вызовов", "Обработчик", "Отправляет обратные вызовы с async-результатом и повторяет доставку")
        Component(reaper, "Восстановитель", "Плановый обработчик", "Восстанавливает устаревшие lease-записи, зависшие задачи и зависшие обратные вызовы")
        Component(metrics, "Наблюдаемость", "Micrometer + структурированные логи", "Экспортирует метрики и логи для эксплуатации")
    }

    Rel(client_service, sync_api, "Sync-запрос")
    Rel(client_service, async_api, "Постановка async / резервное чтение результата")
    Rel(sync_api, slot_manager, "Занять слот SYNC")
    Rel(async_api, queue_repo, "Создать задачу")
    Rel(dispatcher, queue_repo, "Забрать следующую задачу")
    Rel(dispatcher, slot_manager, "Занять слот ASYNC по динамическому резерву")
    Rel(slot_manager, pg, "Чтение/обновление ext_slots и ext_sync_waiters")
    Rel(queue_repo, pg, "Чтение/обновление ext_request_queue")
    Rel(dispatcher, upstream_client, "Выполнить async-вызов внешнего сервиса")
    Rel(sync_api, upstream_client, "Выполнить sync-вызов внешнего сервиса")
    Rel(upstream_client, external_service, "HTTP")
    Rel(callback_delivery, client_service, "Обратный вызов с result Map<String,String>")
    Rel(callback_delivery, pg, "Чтение/обновление ext_callback_delivery")
    Rel(reaper, pg, "Восстановить устаревшие записи")
    Rel(metrics, pg, "Читать эксплуатационные показатели")
```

Главный инвариант управления слотами:

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

## Динамическое представление. Sync-запрос

```mermaid
sequenceDiagram
    participant Client as invest-pay / user-expertise
    participant API as Sync API шлюза
    participant Slots as Управление слотами
    participant DB as PostgreSQL
    participant Upstream as Внешний сервис

    Client->>API: POST /v1/external/sync
    API->>DB: зарегистрировать sync waiter
    API->>Slots: занять sync-слот, максимум 5
    Slots->>DB: арендовать свободный слот с lease_id
    DB-->>Slots: слот занят
    Slots-->>API: lease слота
    API->>DB: удалить sync waiter
    API->>Upstream: HTTP-вызов
    Upstream-->>API: ответ
    API->>Slots: освободить слот по slot_id + lease_id
    Slots->>DB: очистить lease
    API-->>Client: 200 result
```

Если слот не получен до `syncWaitTimeout`, шлюз удаляет sync waiter и возвращает `429`. Код `503` используется для недоступности шлюза или координатора лимитов, а не как обычный ответ на исчерпание sync SLA.

## Динамическое представление. Async-запрос с обратным вызовом

```mermaid
sequenceDiagram
    participant Client as user-expertise
    participant API as Async API шлюза
    participant Dispatcher as Async-диспетчер
    participant Slots as Управление слотами
    participant DB as PostgreSQL
    participant Upstream as Внешний сервис
    participant Callback as Доставка обратных вызовов

    Client->>API: POST /v1/external/async deliveryMode=CALLBACK
    API->>DB: создать задачу PENDING
    API->>DB: NOTIFY external_gateway_queue
    API-->>Client: 202 taskId

    Dispatcher->>DB: забрать следующую задачу PENDING
    Dispatcher->>Slots: занять async-слот по скользящему резерву
    Slots->>DB: проверить syncBusy, asyncBusy, sync waiters
    DB-->>Slots: async lease разрешен
    Slots-->>Dispatcher: lease слота

    Dispatcher->>Upstream: HTTP-вызов
    Upstream-->>Dispatcher: ответ
    Dispatcher->>DB: отметить задачу DONE и атомарно создать доставку PENDING
    Dispatcher->>Slots: освободить слот

    Callback->>DB: забрать доставку обратного вызова
    Callback->>Client: POST /internal/external-gateway/callbacks
    Client-->>Callback: 200 OK
    Callback->>DB: отметить доставку DELIVERED
```

Перевод async-задачи в финальный статус и создание записи доставки обратного вызова должны быть атомарными: одна транзакция в PostgreSQL или транзакционный outbox. Иначе рестарт шлюза между этими действиями может оставить финальную задачу без доставки обратного вызова.

Если обратный вызов не доставлен, `Доставка обратных вызовов` переводит доставку в повтор с задержкой. Результат задачи остается доступен через резервный API шлюза.

## Динамическое представление. Резервное чтение async-результата

```mermaid
sequenceDiagram
    participant Client as user-expertise
    participant API as Async API шлюза
    participant DB as PostgreSQL

    Client->>API: GET /v1/external/async/{taskId}
    API->>DB: выбрать задачу по taskId и clientService из аутентифицированной идентичности
    DB-->>API: status, result, callbackDeliveryStatus
    API-->>Client: AsyncTask
```

Резервное чтение не требует общей БД между сервисами. `user-expertise` обращается к шлюзу по API, а шлюз читает собственную схему.

## Заметки по развертыванию

```mermaid
C4Deployment
    title Шлюз внешнего сервиса - представление развертывания

    Deployment_Node(dc, "Кластер / два плеча", "Docker / Kubernetes-подобная среда") {
        Deployment_Node(app_nodes, "Среда выполнения шлюза", "2+ инстанса") {
            Container(api_1, "Инстанс шлюза #1", "Spring Boot")
            Container(api_2, "Инстанс шлюза #2", "Spring Boot")
        }
        Deployment_Node(db_node, "База данных", "PostgreSQL") {
            ContainerDb(pg, "PostgreSQL шлюза", "PostgreSQL")
        }
    }

    System_Ext(external_service, "Внешний сервис", "Максимум 5 одновременных вызовов")
    System(client_services, "Сервисы-клиенты", "invest-pay, user-expertise")

    Rel(client_services, api_1, "Запросы")
    Rel(client_services, api_2, "Запросы")
    Rel(api_1, pg, "JDBC")
    Rel(api_2, pg, "JDBC")
    Rel(api_1, external_service, "HTTP")
    Rel(api_2, external_service, "HTTP")
```

Все инстансы шлюза должны использовать один логический координатор слотов. Если PostgreSQL раздельный по плечам, лимит `5` превращается в сумму локальных лимитов и перестает быть глобальным.
