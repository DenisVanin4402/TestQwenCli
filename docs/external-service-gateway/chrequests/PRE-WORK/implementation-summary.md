# Итог реализации external-service-gateway

Документ фиксирует текущее состояние Java-реализации gateway и команды проверки. Описание сфокусировано на PostgreSQL-варианте, который является целевой историей для кластерного лимита и устойчивой async-очереди.

## Что сделано

1. Слой слотов и лимитов:
   - добавлены `SlotRepository`, `SlotManager`, `SlotLease`, `SlotKind`;
   - PostgreSQL-репозиторий управляет lease-слотами в `ext_slots`;
   - release и heartbeat работают только по паре `slotId + leaseId`;
   - реализован sync reserve для async: `asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)`;
   - async не стартует при наличии живых sync waiters.

2. Sync API:
   - реализован `POST /v1/external/sync`;
   - запрос получает `SYNC` слот через `SlotManager`;
   - при нехватке слота возвращается `429 NO_SLOT_AVAILABLE` и `Retry-After: 1`;
   - слот освобождается в `finally` после успешного или ошибочного upstream-вызова;
   - `Idempotency-Key` принимается и передается upstream adapter'у, но не хранится gateway'ем.

3. Async API:
   - реализованы `POST`, `GET by taskId`, `GET by externalId`, `DELETE cancel`, `POST retry`;
   - идемпотентность submit реализована по `clientService + externalId`;
   - повтор с тем же payload возвращает существующую задачу;
   - повтор с другим payload, priority или deliveryMode возвращает `409 IDEMPOTENCY_CONFLICT`;
   - `deliveryMode=POLLING` выставляет `callbackDeliveryStatus=NOT_REQUIRED`.

4. Async dispatcher:
   - выбирает задачи по `priorityWeight DESC, availableAt ASC, taskId ASC`;
   - в PostgreSQL держит row-lock задачи в транзакции на время upstream-вызова;
   - переводит `PENDING -> IN_PROGRESS -> DONE`, при этом `IN_PROGRESS` в PostgreSQL не коммитится отдельно;
   - использует async slot через `SlotManager`; PostgreSQL lease слота коммитится отдельной короткой транзакцией, чтобы дашборд видел занятый слот;
   - при runtime-ошибках возвращает задачу в retry/backoff или переводит в `DEAD`;
   - scheduler включается свойством `external-gateway.async.dispatcher-enabled=true`.

5. Callback delivery:
   - callback создается только для `deliveryMode=CALLBACK`;
   - callback URL берется из allow-list `external-gateway.clients.<clientService>.callback-url`;
   - произвольный `callbackUrl` из payload игнорируется;
   - ошибка доставки callback не меняет итоговый статус async-задачи;
   - delivery поддерживает `PENDING`, `DELIVERING`, `DELIVERED`, `RETRY`, `DEAD`;
   - scheduler включается свойством `external-gateway.callback.delivery-enabled=true`.

6. PostgreSQL-ready слой:
   - добавлен Liquibase changelog `src/main/resources/db/changelog/external-gateway/db.changelog-master.yaml`;
   - созданы таблицы `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`;
   - добавлены JDBC-репозитории для slots, async queue и callback delivery;
   - PostgreSQL mode включается через `external-gateway.repository.type=postgres`;
   - для sync waiters добавлен режим `external-gateway.slots.sync-acquire-wait-mode=listen_notify`, который использует канал `external_gateway_slot_released` и fallback polling.

## Как Запустить С PostgreSQL

Минимальные настройки:

```properties
external-gateway.repository.type=postgres
external-gateway.postgres.jdbc-url=jdbc:postgresql://localhost:5432/external_gateway
external-gateway.postgres.username=external_gateway
external-gateway.postgres.password=change-me
external-gateway.postgres.schema=external_gateway
external-gateway.postgres.liquibase-enabled=true
external-gateway.slots.sync-acquire-wait-mode=listen_notify
external-gateway.async.dispatcher-enabled=true
external-gateway.callback.delivery-enabled=true
```

Запуск из корня репозитория:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--external-gateway.repository.type=postgres --external-gateway.postgres.jdbc-url=jdbc:postgresql://localhost:5432/external_gateway --external-gateway.postgres.username=external_gateway --external-gateway.postgres.password=change-me --external-gateway.slots.sync-acquire-wait-mode=listen_notify --external-gateway.async.dispatcher-enabled=true --external-gateway.callback.delivery-enabled=true"
```

Условия:

- база данных должна существовать;
- пользователь должен иметь права на создание schema, таблиц, индексов и constraints;
- если `external-gateway.postgres.liquibase-enabled=false`, changelog должен быть применен заранее.

Smoke-проверка:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/"
```

Ожидаемый ответ:

```text
TestQwenCli is running
```

## Как Проверить Sync API

```powershell
$body = @{
  externalId = "4c48a4dc-3226-4e63-8597-4ee793fc3c3c"
  clientService = "invest-pay"
  payload = @{
    operation = "calculate"
    amount = 1000.50
    currency = "RUB"
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/v1/external/sync" `
  -ContentType "application/json" `
  -Headers @{ "X-Request-Id" = "manual-sync-1"; "Idempotency-Key" = "manual-key-1" } `
  -Body $body
```

Ожидаемо вернется `status = SUCCEEDED`, `upstreamStatus = 200` и result вида:

```json
{
  "decision": "APPROVED",
  "score": "82",
  "reasonCode": "OK"
}
```

## Как Проверить Async API

Создать задачу:

```powershell
$body = @{
  externalId = "1cebc6e0-41f4-47cb-88f1-a915f6dc7801"
  clientService = "invest-pay"
  priority = "HIGH"
  deliveryMode = "CALLBACK"
  payload = @{
    operation = "calculate"
    amount = 100
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/v1/external/async" `
  -ContentType "application/json" `
  -Headers @{ "X-Request-Id" = "manual-async-1" } `
  -Body $body

$created
```

Получить задачу по `taskId`:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/v1/external/async/$($created.taskId)" `
  -Headers @{ "X-Client-Service" = "invest-pay" }
```

Повторный `POST` с тем же `clientService + externalId` и тем же payload должен вернуть тот же `taskId` и `alreadyExisted = true`.

Отменить pending-задачу:

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:8080/v1/external/async/$($created.taskId)" `
  -Headers @{ "X-Client-Service" = "invest-pay" }
```

## Как Проверить Dispatchers

Async dispatcher:

```properties
external-gateway.async.dispatcher-enabled=true
```

Callback delivery dispatcher:

```properties
external-gateway.callback.delivery-enabled=true
```

Если нужно проверить HTTP callback вручную, поднимите локальный mock endpoint и переопределите callback URL:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--external-gateway.repository.type=postgres --external-gateway.postgres.jdbc-url=jdbc:postgresql://localhost:5432/external_gateway --external-gateway.postgres.username=external_gateway --external-gateway.postgres.password=change-me --external-gateway.async.dispatcher-enabled=true --external-gateway.callback.delivery-enabled=true --external-gateway.clients.invest-pay.callback-url=http://localhost:9090/internal/external-gateway/callbacks"
```

## Что Проверяется Автотестами

```powershell
mvn test
```

Тесты проверяют:

- sync happy path, validation error и `429 NO_SLOT_AVAILABLE`;
- slot lease/release/heartbeat semantics;
- async submit/get/cancel/retry и идемпотентность;
- async dispatcher happy path, отсутствие async-слота и transient upstream failure;
- callback delivery happy path, retry, `DEAD` и отсутствие allow-list URL;
- условное создание PostgreSQL infrastructure только для PostgreSQL mode;
- наличие Liquibase changelog.

## Что Не Доделано

1. Реальный upstream HTTP client:
   - сейчас используется simulated adapter;
   - connect/read timeout, circuit breaker и retry policy для настоящего внешнего сервиса не реализованы.

2. Service-to-service security:
   - `clientService` пока берется из request body или `X-Client-Service`;
   - mTLS/JWT/service identity и сверка caller identity не внедрены.

3. Полная idempotency sync:
   - `Idempotency-Key` принимается sync endpoint'ом, но строгая логика хранения и сравнения sync-запросов не реализована.

4. Реальная проверка PostgreSQL mode:
   - JDBC-репозитории и Liquibase changelog добавлены;
   - интеграционный прогон на живом PostgreSQL или Testcontainers нужно выполнить отдельно.

5. Observability:
   - полного набора Micrometer metrics, dashboards и alerts нет;
   - логи есть, но structured logging не доведен до production-формата.

6. Recovery:
   - lease cleanup доступен на уровне `SlotManager.reapExpiredLeases()`;
   - зависшие `IN_PROGRESS` async-задачи предотвращаются транзакционным row-lock claim в PostgreSQL;
   - если JVM падает после отдельного committed-захвата async-слота, слот освобождается lease cleanup по TTL;
   - recovery зависших `DELIVERING` callback deliveries выполняется на старте и scheduler-ом.

7. Deployment:
   - нет Dockerfile/helm/k8s manifests;
   - нет проверенного rollback-плана и нагрузочных сценариев против кластера.
