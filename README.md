# TestQwenCli

Spring Boot 3.4.3 / Java 21 проект для проверки `external-service-gateway`: синхронного API, async-очереди, callback-доставки и тестового dashboard для управляемой нагрузки.

## Структура проекта

- `test-qwen-cli-app` - основное Spring Boot приложение gateway.
- `dashboard-backend` - REST API dashboard, генератор нагрузки и сбор диагностических снимков.
- `dashboard-ui` - статическая SPA-страница dashboard.
- `docs/external-service-gateway` - архитектурная документация, план и runbook gateway.
- `docs/openapi` - OpenAPI-контракты sync, async и callback API.

`dashboard-backend` и `dashboard-ui` подключаются к `test-qwen-cli-app` как Maven-зависимости. Отдельный UI-сервер или отдельный backend-процесс для dashboard запускать не нужно: все доступно в одном Spring Boot приложении на `localhost:8080`.

## Требования

- Java 21.
- Maven.
- PostgreSQL нужен только для запуска в `postgres`-режиме. По умолчанию приложение стартует в `memory`-режиме без базы данных.

## Проверка проекта

Из корня репозитория:

```powershell
mvn test
```

Docker-зависимые integration-тесты запускаются отдельно через Maven Failsafe:

```powershell
mvn verify -Pintegration-tests
```

Эта команда запускает классы `*IT` и требует доступный Docker. Обычный `mvn test` контейнеры не поднимает.

Сборка jar:

```powershell
mvn package
```

## Быстрый запуск без PostgreSQL

Запуск основного приложения вместе с dashboard:

```powershell
mvn -pl test-qwen-cli-app -am spring-boot:run
```

После старта проверьте smoke endpoint:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/"
```

Ожидаемый ответ:

```text
TestQwenCli is running
```

## Запуск с PostgreSQL

Локальный профиль `postgres` лежит в `test-qwen-cli-app/src/main/resources/application-postgres.properties` и ожидает базу:

```properties
external-gateway.postgres.jdbc-url=jdbc:postgresql://localhost:5432/external_gateway
external-gateway.postgres.username=external_gateway
external-gateway.postgres.password=external_gateway
external-gateway.postgres.schema=external_gateway
```

Запуск:

```powershell
mvn -pl test-qwen-cli-app -am spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

Пользователь БД должен иметь права на создание schema, таблиц, индексов и constraints. При `external-gateway.postgres.liquibase-enabled=true` Liquibase применит changelog при старте приложения.

## Dashboard UI

Откройте dashboard в браузере:

```text
http://localhost:8080/dashboard
```

Этот адрес редиректит на:

```text
http://localhost:8080/dashboard/index.html
```

Dashboard позволяет:

- запускать и останавливать тестовую нагрузку;
- менять профиль нагрузки;
- менять параметры симуляции upstream и callback;
- смотреть realtime-снимок нагрузки, слотов, очередей и callback-доставки.

Основные dashboard API:

```http
GET  /dashboard/api/snapshot
GET  /dashboard/api/health
GET  /dashboard/api/load/profile
PUT  /dashboard/api/load/profile
POST /dashboard/api/load/start
POST /dashboard/api/load/stop
POST /dashboard/api/load/reset
GET  /dashboard/api/upstream-simulation
PUT  /dashboard/api/upstream-simulation
```

Пример запуска нагрузки через API:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/dashboard/api/load/start"
```

Остановка нагрузки:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/dashboard/api/load/stop"
```

Получение текущего снимка:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/dashboard/api/snapshot"
```

Обновление профиля нагрузки:

```powershell
$profile = @{
  syncRps = 10
  asyncRps = 20
  highPriorityPercent = 40
  timeoutMs = 1500
  dispatchBatchSize = 30
  clientServices = @("invest-pay", "user-expertise")
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Put `
  -Uri "http://localhost:8080/dashboard/api/load/profile" `
  -ContentType "application/json" `
  -Body $profile
```

Обновление симуляции upstream:

```powershell
$settings = @{
  latencyMs = 50
  jitterMs = 20
  errorRatePercent = 5
  responseSizeKb = 8
  callbackLatencyMs = 20
  callbackErrorRatePercent = 0
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Put `
  -Uri "http://localhost:8080/dashboard/api/upstream-simulation" `
  -ContentType "application/json" `
  -Body $settings
```

## Gateway API

### Sync API

Endpoint:

```http
POST /v1/external/sync
```

Пример:

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

### Async API

Создание задачи:

```http
POST /v1/external/async
```

Пример:

```powershell
$body = @{
  externalId = "1cebc6e0-41f4-47cb-88f1-a915f6dc7801"
  clientService = "invest-pay"
  priority = "HIGH"
  deliveryMode = "POLLING"
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

Получение задачи по `taskId`:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/v1/external/async/$($created.taskId)" `
  -Headers @{ "X-Client-Service" = "invest-pay" }
```

Получение задачи по `externalId`:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/v1/external/async/by-external-id/1cebc6e0-41f4-47cb-88f1-a915f6dc7801" `
  -Headers @{ "X-Client-Service" = "invest-pay" }
```

Отмена pending-задачи:

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:8080/v1/external/async/$($created.taskId)" `
  -Headers @{ "X-Client-Service" = "invest-pay" }
```

Ручной retry для retryable-задачи:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/v1/external/async/$($created.taskId)/retry" `
  -Headers @{ "X-Client-Service" = "invest-pay" }
```

## OpenAPI и Swagger UI

После запуска приложения доступны:

```text
http://localhost:8080/v3/api-docs
http://localhost:8080/swagger-ui.html
```

Исходные OpenAPI-файлы находятся в `docs/openapi`.
