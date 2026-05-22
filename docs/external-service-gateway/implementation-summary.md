# Итог реализации external-service-gateway

Документ фиксирует текущее состояние Java-реализации gateway, способы запуска и проверки, а также оставшиеся ограничения.

## Что сделано

Реализация выполнена по шагам, с проверкой тестами после каждого среза.

1. Слой слотов и лимитов:
   - добавлены `SlotRepository`, `SlotManager`, `SlotLease`, `SlotKind`;
   - memory-репозиторий эмулирует PostgreSQL lease-семантику;
   - release и heartbeat работают только по паре `slotId + leaseId`;
   - реализован sync reserve для async: `asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)`;
   - async не стартует при наличии живых sync waiters.

2. Sync API:
   - реализован `POST /v1/external/sync`;
   - запрос проходит через `SlotManager`;
   - при нехватке слота возвращается `429 NO_SLOT_AVAILABLE`;
   - upstream пока симулирован и возвращает стабильный `Map<String, String>`.

3. Async API:
   - реализованы:
     - `POST /v1/external/async`;
     - `GET /v1/external/async/{taskId}`;
     - `GET /v1/external/async/by-external-id/{externalId}`;
     - `DELETE /v1/external/async/{taskId}`;
     - `POST /v1/external/async/{taskId}/retry`;
   - memory-очередь поддерживает idempotency по `clientService + externalId`;
   - повтор с тем же payload возвращает существующую задачу;
   - повтор с другим payload, priority или deliveryMode возвращает `409 IDEMPOTENCY_CONFLICT`;
   - `deliveryMode=POLLING` выставляет `callbackDeliveryStatus=NOT_REQUIRED`.

4. Async dispatcher:
   - выбирает задачи по `priorityWeight DESC, availableAt ASC, taskId ASC`;
   - переводит `PENDING -> IN_PROGRESS -> DONE`;
   - вызывает simulated upstream вне lock/репозиторной секции;
   - использует async slot через `SlotManager`;
   - при ошибках переводит задачу в retry/backoff или `DEAD`;
   - scheduler выключен по умолчанию.

5. Callback delivery:
   - callback создается только для `deliveryMode=CALLBACK`;
   - callback URL берется из allow-list `external-gateway.clients.<clientService>.callback-url`;
   - произвольный `callbackUrl` из payload не используется;
   - ошибка доставки callback не меняет итоговый статус async-задачи;
   - delivery поддерживает `PENDING`, `DELIVERING`, `DELIVERED`, `RETRY`, `DEAD`;
   - scheduler выключен по умолчанию.

6. PostgreSQL-ready слой:
   - добавлен Liquibase changelog `src/main/resources/db/changelog/external-gateway/db.changelog-master.yaml`;
   - описаны таблицы `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`;
   - добавлены JDBC-репозитории для slots, async queue и callback delivery;
   - PostgreSQL mode включается только через `external-gateway.repository.type=postgres`;
   - memory mode остается дефолтом и не требует PostgreSQL.

## Что можно проверять сейчас

Можно проверять без установленного PostgreSQL:

- старт Spring Boot приложения в memory mode;
- smoke endpoint `/`;
- sync endpoint `/v1/external/sync`;
- async submit/get/cancel/retry API;
- idempotency async постановки;
- лимит слотов через тесты;
- async dispatcher и callback delivery через unit/integration tests;
- отсутствие `DataSource` и `SpringLiquibase` в memory mode;
- наличие Liquibase changelog.

Полный набор автотестов на текущий момент:

```powershell
mvn test
```

Ожидаемый результат:

```text
BUILD SUCCESS
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
```

## Как запустить локально

Запуск из корня репозитория:

```powershell
mvn spring-boot:run
```

По умолчанию приложение стартует в memory mode:

```properties
external-gateway.repository.type=memory
```

PostgreSQL при таком запуске не нужен.

Smoke-проверка:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/"
```

Ожидаемый ответ:

```text
TestQwenCli is running
```

## Как проверить sync API

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

## Как проверить async API

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

## Как проверить dispatcher вручную

По умолчанию async dispatcher выключен:

```properties
external-gateway.async.dispatcher-enabled=false
```

Для ручной проверки обработки async-задач можно запустить приложение с включенным dispatcher:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--external-gateway.async.dispatcher-enabled=true"
```

После этого новые async-задачи будут фоново переводиться из `PENDING` в `DONE` через simulated upstream.

Callback delivery scheduler по умолчанию выключен:

```properties
external-gateway.callback.delivery-enabled=false
```

Это безопасно для локального запуска: callback delivery будет создана, но приложение не будет пытаться отправлять HTTP callback в `invest-pay` или `user-expertise`.

Если нужно проверить HTTP callback вручную, поднимите локальный mock endpoint и переопределите callback URL, например:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--external-gateway.async.dispatcher-enabled=true --external-gateway.callback.delivery-enabled=true --external-gateway.clients.invest-pay.callback-url=http://localhost:9090/internal/external-gateway/callbacks"
```

## Как включить PostgreSQL mode позже

PostgreSQL mode не проверялся на живой БД в этой среде, потому что PostgreSQL локально не установлен. Код и миграции подготовлены для подключения.

Минимальные настройки:

```properties
external-gateway.repository.type=postgres
external-gateway.postgres.jdbc-url=jdbc:postgresql://localhost:5432/external_gateway
external-gateway.postgres.username=external_gateway
external-gateway.postgres.password=change-me
external-gateway.postgres.schema=external_gateway
external-gateway.postgres.liquibase-enabled=true
```

Условия:

- база данных должна существовать;
- пользователь должен иметь права на создание schema, таблиц, индексов и constraints;
- если `external-gateway.postgres.liquibase-enabled=false`, changelog должен быть применен заранее.

## Что не доделано

Оставшиеся рабочие пункты:

1. Реальный upstream HTTP client:
   - сейчас используется simulated client;
   - не реализованы connect/read timeout, circuit breaker и retry policy для настоящего внешнего сервиса.

2. Service-to-service security:
   - `clientService` пока берется из request/header;
   - реальные mTLS/JWT/service identity и сверка caller identity не внедрены.

3. Полная idempotency sync:
   - `Idempotency-Key` принимается sync endpoint'ом, но строгая логика хранения и сравнения sync-запросов не реализована.

4. Реальная проверка PostgreSQL mode:
   - JDBC-репозитории и Liquibase changelog добавлены;
   - интеграционный прогон на живом PostgreSQL или Testcontainers не выполнялся.

5. Observability:
   - нет полного набора Micrometer metrics, dashboards и alerts;
   - логи есть, но structured logging не доведен до production-формата.

6. Recovery jobs:
   - lease reaper и retry-механика есть на уровне сервисов/репозиториев;
   - отдельные production-grade scheduled recovery jobs для зависших `IN_PROGRESS` задач и callback deliveries еще не оформлены полностью.

7. Deployment:
   - нет Dockerfile/helm/k8s manifests;
   - нет проверенного rollback-плана и нагрузочных сценариев против кластера.

## Быстрая команда проверки перед передачей

```powershell
mvn test
```

Если тесты зеленые, memory-mode поведение и текущие API-контракты находятся в рабочем состоянии.
