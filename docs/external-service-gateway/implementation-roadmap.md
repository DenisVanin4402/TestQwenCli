# Implementation Roadmap

Документ хранит статус реализации `external-service-gateway` относительно целевой PostgreSQL-архитектуры. Статусы:

```text
done    - реализовано в текущем коде
partial - реализована базовая часть, production-доработки остаются
todo    - еще не реализовано
```

## Шаг 0. Контракты API

Статус: `partial`.

Сделано:

- описаны `../openapi/external-gateway-sync.yaml`, `../openapi/external-gateway-async.yaml` и `../openapi/external-gateway-callback.yaml`;
- определены client service names для примеров: `invest-pay`, `user-expertise`;
- async submit использует `clientService + externalId` как идемпотентный ключ;
- callback endpoint вынесен в отдельный контракт.

Осталось:

- закрепить production security contract: mTLS/JWT/service identity;
- заменить временный `X-Client-Service` в read/cancel/retry на caller identity;
- описать подпись callback, если она будет нужна.

## Шаг 1. Spring Boot Сервис

Статус: `done`.

Сделано:

- приложение создано на Spring Boot 3.4.3 и Java 21;
- есть smoke endpoint `/`, который возвращает `TestQwenCli is running`;
- свойства gateway вынесены в `application.properties`;
- включены scheduled-компоненты для async и callback dispatcher через feature flags.

Осталось:

- добавить полноценные actuator health/readiness endpoints, если они потребуются для deployment.

## Шаг 2. PostgreSQL-Схема Gateway

Статус: `done`.

Сделано:

- добавлен Liquibase changelog `db/changelog/external-gateway/db.changelog-master.yaml`;
- создается схема `${externalGatewaySchema}`;
- создаются таблицы `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`;
- `ext_slots` заполняется 5 физическими slot rows;
- добавлены constraints на slot kind, async priority/status/delivery mode, callback status;
- добавлены индексы для claim async tasks, lookup by external id, claim callback delivery и cleanup sync waiters.

Осталось:

- при необходимости добавить отдельную audit-таблицу, которой сейчас нет в реализации.

## Шаг 3. Slot Manager

Статус: `done`.

Сделано:

- sync acquire использует до 5 слотов;
- async acquire соблюдает dynamic sync reserve;
- async не стартует при наличии живых sync waiters;
- release и heartbeat проверяют `slot_id + lease_id`;
- `reapExpiredLeases()` освобождает истекшие lease и публикует slot-release notification;
- PostgreSQL `LISTEN/NOTIFY` доступен в режиме `listen_notify` с polling fallback.

Осталось:

- оформить production scheduler для регулярного вызова lease cleanup, если это требуется операционно.

## Шаг 4. Sync API

Статус: `partial`.

Сделано:

- реализован `POST /v1/external/sync`;
- запрос ждет слот до `external-gateway.sync.wait-timeout-ms`;
- при timeout возвращается `429 NO_SLOT_AVAILABLE`;
- upstream-вызов выполняется вне DB-транзакции;
- слот освобождается в `finally`;
- validation errors возвращаются как структурированный `ErrorResponse`.

Осталось:

- заменить simulated upstream adapter на реальный HTTP client;
- добавить production mapping upstream timeout/status codes;
- внедрить строгую sync idempotency, если upstream операция меняет состояние.

## Шаг 5. Async API

Статус: `done`.

Сделано:

- реализованы:
  - `POST /v1/external/async`;
  - `GET /v1/external/async/{taskId}`;
  - `GET /v1/external/async/by-external-id/{externalId}`;
  - `DELETE /v1/external/async/{taskId}`;
  - `POST /v1/external/async/{taskId}/retry`.
- поддержаны `deliveryMode=CALLBACK | POLLING`;
- повтор submit с тем же `clientService + externalId` и тем же payload возвращает существующую задачу;
- конфликт идемпотентности возвращает `409 IDEMPOTENCY_CONFLICT`;
- result хранится как `Map<String, String>`;
- финальная ошибка хранится в структурированном поле `error`.

Осталось:

- заменить временный `X-Client-Service` на реальную caller identity.

## Шаг 5.1. Callback Delivery

Статус: `partial`.

Сделано:

- callback URL выбирается по `clientService` из allow-list конфигурации;
- произвольный `callbackUrl` из payload не используется;
- после финального статуса async-задачи создается callback delivery;
- callback payload содержит `eventId`, `taskId`, `externalId`, `clientService`, `status`, `result`, `error`, `finishedAt`;
- отправляются `X-Callback-Attempt` и `X-Request-Id`;
- доставка имеет retry/backoff и финальный статус `DEAD`;
- ошибка callback-доставки не меняет результат upstream-задачи.

Осталось:

- внедрить подпись callback, если она потребуется;
- добавить production timeout/retry policy для HTTP callback client.

## Шаг 6. Async Dispatcher

Статус: `done`.

Сделано:

- dispatcher выбирает задачи по `priority_weight DESC, available_at ASC, id ASC`;
- claim выполняется через `FOR UPDATE SKIP LOCKED`;
- dispatcher получает async slot по dynamic sync reserve;
- если слот недоступен, задача возвращается в `PENDING`;
- successful upstream переводит задачу в `DONE`;
- runtime upstream failure переводит задачу в retry/backoff или `DEAD`;
- scheduler управляется свойством `external-gateway.async.dispatcher-enabled`.

Осталось:

- после реального upstream client уточнить retryable/non-retryable классификацию ошибок.

## Шаг 7. Upstream Client

Статус: `partial`.

Сделано:

- есть интерфейс `ExternalUpstreamClient`;
- текущий adapter возвращает стабильный `Map<String, String>` и поддерживает искусственную задержку;
- async dispatcher корректно обрабатывает runtime-ошибки adapter'а.

Осталось:

- реализовать настоящий HTTP client;
- настроить connect/read timeout;
- описать mapping upstream status codes;
- добавить circuit breaker и retry policy, если они нужны.

## Шаг 8. Recovery

Статус: `partial`.

Сделано:

- lease cleanup реализован методом `SlotManager.reapExpiredLeases()`;
- retry/backoff async-задач реализован в repository/dispatcher;
- callback retry/backoff реализован в callback repository/dispatcher;
- ручной retry поддержан для `FAILED`/`DEAD` задач с `retryable=true`.

Осталось:

- добавить scheduler для expired leases;
- добавить recovery для зависших `IN_PROGRESS` задач;
- добавить recovery для зависших `DELIVERING` callback deliveries.

## Шаг 9. Observability

Статус: `partial`.

Сделано:

- есть прикладные логи по sync, async и callback flow;
- error responses содержат `code`, `message`, `retryable`, `requestId`, `details`.

Осталось:

- добавить Micrometer metrics;
- добавить dashboards и alerts;
- стандартизировать structured logging.

## Шаг 10. Интеграционные И Нагрузочные Тесты

Статус: `partial`.

Сделано:

- есть тесты sync API, async API, Slot Manager, dispatcher, callback delivery и условной PostgreSQL-конфигурации;
- проверяется наличие Liquibase changelog.

Осталось:

- выполнить PostgreSQL integration test на живой БД или Testcontainers;
- проверить кластерную конкуренцию нескольких gateway-инстансов;
- проверить потерю `NOTIFY` и fallback polling на настоящем PostgreSQL;
- провести нагрузочные сценарии с sync и async backlog.

## Шаг 11. Развертывание

Статус: `todo`.

Осталось:

- подготовить Dockerfile/helm/k8s manifests;
- описать миграционный и rollback-план;
- настроить секреты PostgreSQL через внешнюю конфигурацию;
- включить service-to-service authentication;
- зафиксировать operational runbook для очереди, callback retries и dead tasks.
