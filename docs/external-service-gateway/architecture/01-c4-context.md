# C4 Level 1. System Context

`external-service-gateway` является внутренним фасадом к внешнему сервису с жестким лимитом `5 concurrent calls`. Он отделяет доменные сервисы от политики лимитирования, очередей, retry, callback-доставки и деталей интеграции с upstream.

## Диаграмма контекста

```mermaid
C4Context
    title external-service-gateway - System Context

    Person(ops, "Support / Operations", "Следит за состоянием gateway, очередей, callback-доставки и инцидентами")

    System(investPay, "invest-pay", "Сервис-клиент. Отправляет sync и async запросы")
    System(userExpertise, "user-expertise", "Сервис-клиент. Отправляет sync и async запросы")
    System(otherClients, "Будущие внутренние сервисы", "Новые потребители gateway API")

    System(gateway, "external-service-gateway", "Внутренний Spring Boot gateway для sync/async вызовов внешнего сервиса")
    System_Ext(externalService, "External Service", "Внешний сервис с лимитом 5 одновременных вызовов")
    System_Ext(observability, "Observability Platform", "Логи, метрики, алерты и трассировка в production target")

    Rel(investPay, gateway, "Sync/async запросы, polling, cancel, retry", "HTTP/JSON")
    Rel(userExpertise, gateway, "Sync/async запросы, polling, cancel, retry", "HTTP/JSON")
    Rel(otherClients, gateway, "Будущие интеграции через стабильный HTTP contract", "HTTP/JSON")

    Rel(gateway, investPay, "Async callback с финальным результатом", "HTTP/JSON")
    Rel(gateway, userExpertise, "Async callback с финальным результатом", "HTTP/JSON")
    Rel(gateway, externalService, "Лимитированные upstream-вызовы", "HTTP")
    Rel(ops, gateway, "Диагностика через dashboard, логи и health-снимки", "Browser/HTTP")
    Rel(gateway, observability, "Технические события, метрики, алерты", "Logs/Metrics")
```

## Участники

| Участник | Роль | Контракт |
| --- | --- | --- |
| `invest-pay` | Сервис-клиент | Вызывает sync/async API, принимает callback для своих задач. |
| `user-expertise` | Сервис-клиент | Вызывает sync/async API, принимает callback для своих задач. |
| `external-service-gateway` | Владелец интеграционной политики | Применяет лимит слотов, хранит async state, выполняет retry и callback. |
| `External Service` | Внешняя зависимость | Принимает не более 5 параллельных вызовов. |
| `Support / Operations` | Эксплуатация | Смотрит dashboard, очереди, ошибки и backlog. |
| `Observability Platform` | Production target | Получает метрики, логи и алерты. В текущем коде полноценный набор еще не внедрен. |

## Доверенные границы

```mermaid
flowchart LR
    subgraph ClientZone["Доверенная зона сервисов-клиентов"]
        investPay["invest-pay"]
        userExpertise["user-expertise"]
    end

    subgraph GatewayZone["Зона владения external-service-gateway"]
        api["Gateway HTTP API"]
        workers["Dispatchers"]
        db[("Gateway PostgreSQL")]
        dashboard["Dashboard"]
    end

    subgraph ExternalZone["Внешняя интеграционная зона"]
        upstream["External Service"]
    end

    investPay -->|HTTP JSON| api
    userExpertise -->|HTTP JSON| api
    workers -->|callback HTTP| investPay
    workers -->|callback HTTP| userExpertise
    api --> db
    workers --> db
    api -->|sync upstream| upstream
    workers -->|async upstream| upstream
```

В текущей реализации `clientService` передается в теле submit/sync запроса, а fallback-операции используют необязательный `X-Client-Service`. В production target эта модель должна быть заменена на service identity из mTLS, JWT или service mesh с проверкой соответствия caller identity и `clientService`.

## Ответственность gateway

- Нормализовать входной REST/OpenAPI contract для всех клиентов.
- Сохранять единую политику лимитирования внешнего сервиса.
- Давать sync-клиентам приоритет над стартом новых async-задач.
- Хранить async state и результат до получения клиентом через callback или polling.
- Доставлять callback с retry/backoff и фиксировать статус доставки отдельно от статуса upstream-задачи.
- Давать support-команде диагностический dashboard и health-снимки.

## Не ответственность gateway

- Gateway не владеет бизнес-решениями, которые принимает внешний сервис.
- Gateway не должен становиться общей БД между сервисами-клиентами.
- Gateway не должен принимать произвольный `callbackUrl` из payload, чтобы не открывать SSRF-вектор.
- Gateway не должен компенсировать отсутствие идемпотентности на стороне callback endpoint. Клиентский callback endpoint обязан быть идемпотентным.

## Критические инварианты

```text
totalSlots = 5
targetFreeSyncSlots = 1
asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)

async start is allowed only when:
  liveSyncWaiters == 0
  asyncBusy < asyncAllowed
```

Эти правила означают, что уже начатый async-вызов не прерывается, но новые async-вызовы не стартуют, если sync-нагрузка уже ждет слот или необходимо сохранить резерв под sync.
