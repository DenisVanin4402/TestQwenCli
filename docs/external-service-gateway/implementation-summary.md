# Итог реализации external-service-gateway

Документ фиксирует текущее состояние Java-реализации шлюза, способы запуска и проверки, а также оставшиеся ограничения.

## Что сделано

Реализация выполнена по шагам, с проверкой тестами после каждого среза.

1. Слой слотов и лимитов:
   - добавлены `SlotRepository`, `SlotManager`, `SlotLease`, `SlotKind`;
   - репозиторий в памяти эмулирует PostgreSQL-семантику lease;
   - освобождение и продление аренды работают только по паре `slotId + leaseId`;
   - реализован sync-резерв для async: `asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)`;
   - async не стартует при наличии живых ожидающих sync-запросов.

2. Синхронный API:
   - реализован `POST /v1/external/sync`;
   - запрос проходит через `SlotManager`;
   - при нехватке слота возвращается `429 NO_SLOT_AVAILABLE`;
   - внешний сервис пока симулирован и возвращает стабильный `Map<String, String>`.

3. Асинхронный API:
   - реализованы:
     - `POST /v1/external/async`;
     - `GET /v1/external/async/{taskId}`;
     - `GET /v1/external/async/by-external-id/{externalId}`;
     - `DELETE /v1/external/async/{taskId}`;
     - `POST /v1/external/async/{taskId}/retry`;
   - очередь в памяти поддерживает идемпотентность по `clientService + externalId`;
   - повтор с той же полезной нагрузкой возвращает существующую задачу;
   - повтор с другой полезной нагрузкой, priority или deliveryMode возвращает `409 IDEMPOTENCY_CONFLICT`;
   - `deliveryMode=POLLING` выставляет `callbackDeliveryStatus=NOT_REQUIRED`.

4. Асинхронный диспетчер:
   - выбирает задачи по `priorityWeight DESC, availableAt ASC, taskId ASC`;
   - переводит `PENDING -> IN_PROGRESS -> DONE`;
   - вызывает симулированный внешний сервис вне блокировки/репозиторной секции;
   - использует async slot через `SlotManager`;
   - при ошибках переводит задачу в повтор с задержкой или `DEAD`;
   - планировщик выключен по умолчанию.

5. Доставка обратных вызовов:
   - доставка создается только для `deliveryMode=CALLBACK`;
   - URL обратного вызова берется из списка разрешений `external-gateway.clients.<clientService>.callback-url`;
   - произвольный `callbackUrl` из полезной нагрузки не используется;
   - ошибка доставки обратного вызова не меняет итоговый статус async-задачи;
   - доставка поддерживает `PENDING`, `DELIVERING`, `DELIVERED`, `RETRY`, `DEAD`;
   - планировщик выключен по умолчанию.

6. Слой, готовый к PostgreSQL:
   - добавлен файл изменений Liquibase `src/main/resources/db/changelog/external-gateway/db.changelog-master.yaml`;
   - описаны таблицы `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`;
   - добавлены JDBC-репозитории для слотов, асинхронной очереди и доставки обратных вызовов;
   - режим PostgreSQL включается только через `external-gateway.repository.type=postgres`;
   - режим памяти остается значением по умолчанию и не требует PostgreSQL.

## Что можно проверять сейчас

Можно проверять без установленного PostgreSQL:

- старт приложения Spring Boot в режиме памяти;
- дымовая проверка эндпоинта `/`;
- синхронный эндпоинт `/v1/external/sync`;
- асинхронный API постановки, чтения, отмены и повтора;
- идемпотентность async-постановки;
- лимит слотов через тесты;
- асинхронный диспетчер и доставка обратных вызовов через модульные/интеграционные тесты;
- отсутствие `DataSource` и `SpringLiquibase` в режиме памяти;
- наличие файла изменений Liquibase.

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

По умолчанию приложение стартует в режиме памяти:

```properties
external-gateway.repository.type=memory
```

PostgreSQL при таком запуске не нужен.

Дымовая проверка:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/"
```

Ожидаемый ответ:

```text
TestQwenCli is running
```

## Как проверить синхронный API

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

Ожидаемо вернется `status = SUCCEEDED`, `upstreamStatus = 200` и результат вида:

```json
{
  "decision": "APPROVED",
  "score": "82",
  "reasonCode": "OK"
}
```

## Как проверить асинхронный API

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

Повторный `POST` с тем же `clientService + externalId` и той же полезной нагрузкой должен вернуть тот же `taskId` и `alreadyExisted = true`.

Отменить задачу в статусе `PENDING`:

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:8080/v1/external/async/$($created.taskId)" `
  -Headers @{ "X-Client-Service" = "invest-pay" }
```

## Как проверить диспетчер вручную

По умолчанию асинхронный диспетчер выключен:

```properties
external-gateway.async.dispatcher-enabled=false
```

Для ручной проверки обработки async-задач можно запустить приложение с включенным диспетчером:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--external-gateway.async.dispatcher-enabled=true"
```

После этого новые async-задачи будут фоново переводиться из `PENDING` в `DONE` через симулированный внешний сервис.

Планировщик доставки обратных вызовов по умолчанию выключен:

```properties
external-gateway.callback.delivery-enabled=false
```

Это безопасно для локального запуска: доставка обратного вызова будет создана, но приложение не будет пытаться отправлять HTTP-обратный вызов в `invest-pay` или `user-expertise`.

Если нужно проверить HTTP-обратный вызов вручную, поднимите локальный имитационный эндпоинт и переопределите URL обратного вызова, например:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--external-gateway.async.dispatcher-enabled=true --external-gateway.callback.delivery-enabled=true --external-gateway.clients.invest-pay.callback-url=http://localhost:9090/internal/external-gateway/callbacks"
```

## Как включить режим PostgreSQL позже

Режим PostgreSQL не проверялся на живой БД в этой среде, потому что PostgreSQL локально не установлен. Код и миграции подготовлены для подключения.

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
- пользователь должен иметь права на создание схемы, таблиц, индексов и ограничений;
- если `external-gateway.postgres.liquibase-enabled=false`, файл изменений должен быть применен заранее.

## Что не доделано

Оставшиеся рабочие пункты:

1. Реальный HTTP-клиент внешнего сервиса:
   - сейчас используется симулированный клиент;
   - не реализованы таймауты соединения/чтения, автоматический выключатель и политика повторов для настоящего внешнего сервиса.

2. Межсервисная безопасность:
   - `clientService` пока берется из запроса/заголовка;
   - реальные mTLS/JWT, сервисная идентичность и сверка идентичности вызывающего сервиса не внедрены.

3. Полная идемпотентность sync:
   - `Idempotency-Key` принимается sync-эндпоинтом, но строгая логика хранения и сравнения sync-запросов не реализована.

4. Реальная проверка режима PostgreSQL:
   - JDBC-репозитории и файл изменений Liquibase добавлены;
   - интеграционный прогон на живом PostgreSQL или Testcontainers не выполнялся.

5. Наблюдаемость:
   - нет полного набора метрик Micrometer, панелей и оповещений;
   - логи есть, но структурированное логирование не доведено до промышленного формата.

6. Задачи восстановления:
   - восстановитель lease-записей и механика повторов есть на уровне сервисов/репозиториев;
   - отдельные плановые задачи восстановления промышленного уровня для зависших задач `IN_PROGRESS` и доставок обратных вызовов еще не оформлены полностью.

7. Развертывание:
   - нет Dockerfile, Helm-чарта и манифестов k8s;
   - нет проверенного плана отката и нагрузочных сценариев против кластера.

## Быстрая команда проверки перед передачей

```powershell
mvn test
```

Если тесты зеленые, поведение в режиме памяти и текущие API-контракты находятся в рабочем состоянии.
