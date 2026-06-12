# Production Readiness

Документ фиксирует, что уже реализовано в проекте и что обязательно нужно закрыть перед enterprise production rollout.

## Текущее состояние

| Область | Состояние | Комментарий |
| --- | --- | --- |
| REST/OpenAPI sync API | Реализовано | `POST /v1/external/sync`, error contract, `Retry-After` для 429. |
| REST/OpenAPI async API | Реализовано | Submit, polling, lookup by externalId, cancel, manual retry. |
| Async idempotency | Реализовано | Ключ `clientService + externalId`, конфликт по payload/priority/deliveryMode. |
| Global slot coordination | Реализовано для PostgreSQL mode | Lease-слоты `ext_slots`, sync reserve, live waiters gate. |
| LISTEN/NOTIFY wait mode | Реализовано | Есть fallback на polling interval. |
| Durable async queue | Реализовано | `ext_request_queue`. |
| Callback delivery queue | Реализовано | `ext_callback_delivery`, retry/dead/recovery. |
| Dashboard | Реализовано | Embedded operational UI/API для локальной нагрузки и diagnostics. |
| Реальный upstream HTTP client | Не реализовано | Сейчас используется simulated adapter. |
| Service-to-service identity | Не реализовано | `clientService` берется из payload или временного header. |
| Full sync idempotency | Не реализовано | `Idempotency-Key` не хранит result в gateway. |
| Production observability | Частично | Есть logs и dashboard health, но нет полного Micrometer/alerts набора. |
| Deployment manifests | Не реализовано | Нет Dockerfile/Helm/Kubernetes manifests. |
| Retention policy | Не реализовано | Таблицы queue/callback будут расти без cleanup. |

## Обязательные production gates

### P0. Корректность лимита

- Все production replicas используют `external-gateway.repository.type=postgres`.
- Все replicas указывают на одну логическую PostgreSQL schema.
- `ext_slots` содержит ровно согласованное количество активных слотов.
- Нагрузочный тест подтверждает, что сумма `SYNC + ASYNC` busy slots никогда не превышает 5.
- Reaper освобождает истекшие lease, но не ломает активные корректные lease.

### P0. Service identity

- Входящий caller identity извлекается из mTLS/JWT/service mesh.
- `clientService` в payload сверяется с caller identity.
- `X-Client-Service` удален из публичной модели доступа или используется только как backward-compatible диагностический header под строгой авторизацией.
- Callback URL выбирается из allow-list по verified identity.

### P0. Реальный upstream HTTP client

- Настроены connect timeout и read timeout.
- Ошибки классифицируются на retryable и non-retryable.
- Есть circuit breaker или bulkhead, чтобы slow upstream не съедал все servlet/worker threads.
- `Idempotency-Key` или эквивалентная корреляция передается upstream для безопасных повторов.
- Логируются upstream status, latency и trace id.

### P0. Observability и alerting

- Включены метрики из [08-deployment-operations.md](08-deployment-operations.md).
- Настроены алерты на backlog, dead callback, no slot spike, upstream timeout, DB errors.
- Все технические ошибки имеют stable code.
- Логи коррелируются по `X-Request-Id`, `externalId`, `clientService`, `taskId`, `deliveryId`.

### P1. Retention и support operations

- Определены сроки хранения финальных задач и callback deliveries.
- Есть job очистки старых `DONE`, `DELIVERED`, `CANCELLED`.
- `DEAD` и `FAILED` хранятся дольше или архивируются.
- Есть runbook ручного расследования callback `DEAD`.
- Есть безопасная manual redelivery callback или документированный workaround через polling.

### P1. Deployment и rollback

- Есть повторяемый artifact build.
- Есть инфраструктурные manifests.
- Readiness не пропускает traffic при недоступной БД или неготовых migrations.
- Rollback не откатывает БД в несовместимое состояние.
- Canary rollout проверяет sync, async polling и callback path.

## Риски и решения

| Риск | Последствие | Решение |
| --- | --- | --- |
| Несколько независимых PostgreSQL coordinators | Превышение внешнего лимита 5. | Один логический координатор, проверка в deployment policy. |
| Client timeout на sync после старта upstream | Клиент может повторить операцию, upstream получит дубль. | Внедрить sync idempotency storage или рекомендовать async для критичных операций. |
| Callback endpoint неидемпотентен | Duplicate callback создает дубль бизнес-эффекта. | Обязать clients делать idempotent callback processing. |
| Dashboard доступен широко | Нагрузкой можно повлиять на gateway и upstream. | Закрыть dashboard auth/network policy. |
| Нет retention | Рост таблиц, деградация индексов, сложность backup. | Cleanup policy и partitioning при необходимости. |
| Simulated upstream остается в production | Нет реальной сетевой устойчивости и ошибок протокола. | Заменить на HTTP adapter до rollout. |

## Recommended SLO

Предлагаемый стартовый набор SLO требует подтверждения владельцами клиентов:

- Sync availability: 99.9% для gateway API без учета отказа внешнего сервиса.
- Sync latency: p95 меньше согласованного `external-gateway.sync.wait-timeout-ms` при нормальной upstream latency.
- Async submit latency: p95 меньше 200 ms без учета БД деградации.
- Async completion lag: p95 в пределах бизнес-SLA при доступном upstream.
- Callback delivery lag: p95 в пределах бизнес-SLA при доступном callback endpoint.
- Callback eventual delivery: 99.9% доставок до `DELIVERED` без ручного вмешательства при исправном endpoint клиента.

## Проверочный набор перед релизом

Минимальная проверка:

```powershell
mvn test
mvn verify -Pintegration-tests
```

Production-like проверка:

- запустить 2+ gateway instances в `postgres` mode;
- подать смешанную sync/async нагрузку;
- подтвердить, что busy slots не превышают 5;
- подтвердить, что sync waiters блокируют старт новых async-задач;
- подтвердить retry/backoff async при transient upstream errors;
- подтвердить callback retry и recovery `DELIVERING`;
- подтвердить, что сервисы-клиенты получают duplicate callback безопасно;
- проверить rollback приложения без отката данных.

## Решение о готовности

Система архитектурно готова как v1 PostgreSQL-based gateway pattern, но текущая реализация не должна считаться enterprise production-ready до закрытия P0 gates: service identity, real upstream HTTP client, production observability, deployment manifests и подтвержденный cluster load test.
