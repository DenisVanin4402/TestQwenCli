# CR002-T001: инвентаризация фактического HTTP API

## Источники истины

| Контракт | Источник истины | YAML для синхронизации |
| --- | --- | --- |
| Sync gateway API | `ExternalSyncController`, `ExternalSyncRequest`, `ExternalSyncResponse`, `ExternalGatewayExceptionHandler` | `docs/external-service-gateway/openapi/external-gateway-sync.yaml` |
| Async gateway API | `ExternalAsyncController`, `ExternalAsyncRequest`, `AsyncSubmitResponse`, `AsyncTask`, `TaskError`, enum-классы async-моделей, `ExternalGatewayExceptionHandler` | `docs/external-service-gateway/openapi/external-gateway-async.yaml` |
| Callback API сервиса-клиента | `CallbackPayload`, `TaskError`, `HttpCallbackClient`, `HttpCallbackClientTest` | `docs/external-service-gateway/openapi/external-gateway-callback.yaml` |
| Dashboard API | `DashboardController` из `dashboard-backend` | Не входит в `external-gateway-*.yaml` в рамках CR002 |
| Root endpoint | `HomeController` | Не входит в `external-gateway-*.yaml` в рамках CR002 |

Контроллеры и DTO приложения важнее YAML. Если ниже зафиксировано расхождение или спорное место, исправление выполняется в следующих этапах CR002, а не в T001.

## Gateway endpoints

| Endpoint | Метод | OperationId | Контроллер | Заголовки | Успешный ответ | Ошибки по явным annotations/handler |
| --- | --- | --- | --- | --- | --- | --- |
| `/v1/external/sync` | `POST` | `callExternalSync` | `ExternalSyncController.sync` | `X-Request-Id` optional, `Idempotency-Key` optional | `200`, `ExternalSyncResponse` | `400`, `429`, `503`, `504`, `ErrorResponse` |
| `/v1/external/async` | `POST` | `submitExternalAsync` | `ExternalAsyncController.submit` | `X-Request-Id` optional | `202`, `AsyncSubmitResponse` | `400`, `409`, `ErrorResponse` |
| `/v1/external/async/{taskId}` | `GET` | `getExternalAsyncTask` | `ExternalAsyncController.getByTaskId` | `X-Request-Id` optional, `X-Client-Service` optional | `200`, `AsyncTask` | `404`, `ErrorResponse` |
| `/v1/external/async/{taskId}` | `DELETE` | `cancelExternalAsyncTask` | `ExternalAsyncController.cancel` | `X-Request-Id` optional, `X-Client-Service` optional | `200`, `AsyncTask` | `404`, `409`, `ErrorResponse` |
| `/v1/external/async/by-external-id/{externalId}` | `GET` | `getExternalAsyncTaskByExternalId` | `ExternalAsyncController.getByExternalId` | `X-Request-Id` optional, `X-Client-Service` optional | `200`, `AsyncTask` | `404`, `ErrorResponse` |
| `/v1/external/async/{taskId}/retry` | `POST` | `retryExternalAsyncTask` | `ExternalAsyncController.retry` | `X-Request-Id` optional, `X-Client-Service` optional | `202`, `AsyncTask` | `404`, `409`, `ErrorResponse` |

`X-Client-Service` является временным scope доступа для read/cancel/retry до внедрения service-to-service identity. Если заголовок не передан, текущая реализация не ограничивает lookup сервисом-клиентом.

## Callback endpoint сервиса-клиента

| Endpoint | Метод | OperationId в YAML | Источник фактического вызова | Заголовки | Тело |
| --- | --- | --- | --- | --- | --- |
| `/internal/external-gateway/callbacks` | `POST` | `receiveExternalGatewayCallback` | `HttpCallbackClient.send` | `X-Callback-Attempt` всегда передается; `X-Request-Id` передается только если непустой | `CallbackPayload` |

Callback endpoint не реализован gateway-контроллером. Это контракт, который реализуют сервисы-клиенты, а gateway вызывает через `HttpCallbackClient`. Для gateway успешным считается любой `2xx` HTTP-ответ клиента; `4xx/5xx` и сетевые ошибки являются входом для retry/dead логики доставки.

## Endpoints вне CR002 YAML

| Endpoint | Метод | Контроллер | Решение для CR002 |
| --- | --- | --- | --- |
| `/` | `GET` | `HomeController.home` | Не включать в `external-gateway-*.yaml`; это health/smoke endpoint приложения, не gateway contract. |
| `/dashboard` | `GET` | `DashboardController.dashboardPage` | Не включать в `external-gateway-*.yaml`; относится к dashboard UI. |
| `/dashboard/api/snapshot` | `GET` | `DashboardController.snapshot` | Не включать в `external-gateway-*.yaml`; dashboard API требует отдельного контракта при необходимости. |
| `/dashboard/api/health` | `GET` | `DashboardController.health` | Не включать в `external-gateway-*.yaml`. |
| `/dashboard/api/load/profile` | `GET`, `PUT` | `DashboardController.profile`, `DashboardController.updateProfile` | Не включать в `external-gateway-*.yaml`. |
| `/dashboard/api/load/start` | `POST` | `DashboardController.startLoad` | Не включать в `external-gateway-*.yaml`. |
| `/dashboard/api/load/stop` | `POST` | `DashboardController.stopLoad` | Не включать в `external-gateway-*.yaml`. |
| `/dashboard/api/load/reset` | `POST` | `DashboardController.resetMetrics` | Не включать в `external-gateway-*.yaml`. |
| `/dashboard/api/upstream-simulation` | `GET`, `PUT` | `DashboardController.simulationSettings`, `DashboardController.updateSimulationSettings` | Не включать в `external-gateway-*.yaml`. |

Текущий `ExternalGatewayOpenApiContractTest` проверяет наличие dashboard paths в generated `/v3/api-docs`, но это не означает включение dashboard API в external-service-gateway YAML. Для CR002 dashboard остается отдельной пользовательской/диагностической поверхностью.

## Error contract

`ExternalGatewayExceptionHandler` задает стабильный error body для gateway package:

| Источник ошибки | HTTP status | `code` | `retryable` | Особенности |
| --- | --- | --- | --- | --- |
| `MethodArgumentNotValidException` | `400` | `VALIDATION_ERROR` | `false` | `requestId` берется из `X-Request-Id`, `details.fields` содержит ошибки полей. |
| `HttpMessageNotReadableException` | `400` | `INVALID_REQUEST` | `false` | `details.reason = "Тело запроса не соответствует контракту"`. |
| `NoSlotAvailableException` | `429` | `NO_SLOT_AVAILABLE` | `true` | Добавляет header `Retry-After: 1`, `details.syncWaitTimeoutMs`. |
| `UpstreamTimeoutException` | `504` | `UPSTREAM_TIMEOUT` | `true` | `details.syncTimeoutMs`, `details.plannedDelayMs`. |
| `UpstreamInterruptedException` | `503` | `UPSTREAM_INTERRUPTED` | `true` | `details` пустой. |
| `SimulatedUpstreamFailureException` | `503` | `UPSTREAM_SIMULATED_FAILURE` | `true` | `details` пустой. |
| `AsyncIdempotencyConflictException` | `409` | `IDEMPOTENCY_CONFLICT` | `false` | `details.existingTaskId`, `details.conflictingFields`. |
| `AsyncTaskNotFoundException` по `taskId` | `404` | `TASK_NOT_FOUND` | `false` | `details.taskId`. |
| `AsyncTaskNotFoundException` по `externalId` | `404` | `TASK_NOT_FOUND` | `false` | `details.externalId`, опционально `details.clientService`. |
| `AsyncTaskStateConflictException` | `409` | `TASK_STATE_CONFLICT` | `false` | `details.taskId`, `details.currentStatus`. |

Схема `ErrorResponse`: `code`, `message`, `retryable`, `requestId`, `details`. Обязательные поля по фактической record-модели не заданы через validation annotations, но handler всегда передает `code`, `message`, `retryable`; `requestId` и `details` могут быть `null`.

## Sync DTO

### `ExternalSyncRequest`

| Поле | Тип | Обязательность и ограничения | Примечание |
| --- | --- | --- | --- |
| `externalId` | `UUID` | `@NotNull` | Внешний id операции клиента. |
| `clientService` | `String` | `@NotBlank`, `@Size(min=2, max=80)` | Текущая реализация берет значение из тела запроса. |
| `payload` | `Map<String, Object>` | `@NotNull` | Копируется в unmodifiable map, произвольная JSON-структура объекта. |

### `ExternalSyncResponse`

| Поле | Тип | Фактическое ограничение | Примечание |
| --- | --- | --- | --- |
| `externalId` | `UUID` | Может быть `null` на уровне record, но сервисный flow передает id исходного запроса. | |
| `status` | `ExternalSyncStatus` | enum: `SUCCEEDED` | |
| `result` | `Map<String, String>` | Не может быть `null`, копируется через `Map.copyOf`. | |
| `upstreamStatus` | `int` | Java `int`; бизнес-смысл HTTP/upstream status. | |
| `durationMs` | `long` | Конструктор запрещает значение меньше `0`. | |
| `upstreamTraceId` | `String` | Может быть `null`. | |

## Async DTO

### `ExternalAsyncRequest`

| Поле | Тип | Обязательность и ограничения | Примечание |
| --- | --- | --- | --- |
| `externalId` | `UUID` | `@NotNull` | Участвует в idempotency вместе с `clientService`. |
| `clientService` | `String` | `@NotBlank`, `@Size(min=2, max=80)` | Текущая реализация берет значение из тела запроса. |
| `priority` | `AsyncPriority` | `@NotNull` | enum: `HIGH`, `LOW`. |
| `deliveryMode` | `AsyncDeliveryMode` | Может отсутствовать; constructor default `CALLBACK`; `SYNC` запрещен для внешнего async API. | Публично допустимы `CALLBACK`, `POLLING`; `SYNC` внутренний. |
| `payload` | `Map<String, Object>` | `@NotNull` | Копируется через `AsyncPayloads.copyMap`. |

### `AsyncSubmitResponse`

| Поле | Тип | Фактическое ограничение |
| --- | --- | --- |
| `taskId` | `long` | Конструктор запрещает значение меньше `1`. |
| `externalId` | `UUID` | `Objects.requireNonNull`. |
| `status` | `AsyncTaskStatus` | `Objects.requireNonNull`; после submit обычно `PENDING`. |
| `deliveryMode` | `AsyncDeliveryMode` | `Objects.requireNonNull`. |
| `statusUrl` | `String` | `Objects.requireNonNull`, не blank; формат `/v1/external/async/{taskId}`. |
| `alreadyExisted` | `boolean` | `true` для idempotent replay. |

### `AsyncTask`

| Поле | Тип | Фактическое ограничение |
| --- | --- | --- |
| `taskId` | `long` | Конструктор запрещает значение меньше `1`. |
| `externalId` | `UUID` | `Objects.requireNonNull`. |
| `clientService` | `String` | `Objects.requireNonNull`, не blank. |
| `priority` | `AsyncPriority` | `Objects.requireNonNull`. |
| `deliveryMode` | `AsyncDeliveryMode` | `Objects.requireNonNull`. |
| `status` | `AsyncTaskStatus` | `Objects.requireNonNull`. |
| `callbackDeliveryStatus` | `CallbackDeliveryStatus` | `Objects.requireNonNull`. |
| `result` | `Map<String, Object>` | Может быть `null`; при наличии копируется. |
| `error` | `TaskError` | Может быть `null`. |
| `attempts` | `int` | Не меньше `0`. |
| `maxAttempts` | `int` | Не меньше `1`. |
| `createdAt` | `Instant` | `Objects.requireNonNull`. |
| `availableAt` | `Instant` | Может быть `null` на уровне record. |
| `startedAt` | `Instant` | Может быть `null`. |
| `finishedAt` | `Instant` | Может быть `null`. |
| `lastError` | `String` | Может быть `null`. |
| `retryable` | `boolean` | Публичный признак возможности manual retry. |

### `TaskError`

| Поле | Тип | Фактическое ограничение |
| --- | --- | --- |
| `code` | `String` | `Objects.requireNonNull`, не blank. |
| `message` | `String` | `Objects.requireNonNull`, не blank. |
| `retryable` | `boolean` | Без дополнительных ограничений. |

## Async enum-значения

| Enum | Значения | Публичность |
| --- | --- | --- |
| `AsyncPriority` | `HIGH`, `LOW` | Входящее async API. |
| `AsyncDeliveryMode` | `CALLBACK`, `POLLING`, `SYNC` | `CALLBACK` и `POLLING` публичны для async submit; `SYNC` используется только для внутренних sync trace. |
| `AsyncTaskStatus` | `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED`, `DEAD`, `CANCELLED` | Публичный статус async task. |
| `CallbackDeliveryStatus` | `NOT_REQUIRED`, `PENDING`, `DELIVERING`, `DELIVERED`, `RETRY`, `DEAD` | Публичное поле `AsyncTask.callbackDeliveryStatus`. |
| `ExternalSyncStatus` | `SUCCEEDED` | Публичный статус sync response. |

## Callback DTO

### `CallbackPayload`

| Поле | Тип | Фактическое ограничение |
| --- | --- | --- |
| `eventId` | `UUID` | `Objects.requireNonNull`; idempotency key доставки. |
| `taskId` | `long` | Больше `0`. |
| `externalId` | `UUID` | `Objects.requireNonNull`. |
| `clientService` | `String` | `Objects.requireNonNull`, не blank. |
| `status` | `AsyncTaskStatus` | `Objects.requireNonNull`; `fromTask` разрешает только `DONE`, `FAILED`, `DEAD`, `CANCELLED`. |
| `result` | `Map<String, String>` | Для `DONE` обязателен и копируется; для остальных финальных статусов должен быть `null`. |
| `error` | `TaskError` | Для `DONE` должен быть `null`; для остальных финальных статусов обязателен. |
| `finishedAt` | `Instant` | `Objects.requireNonNull`. |

`CallbackPayload.fromTask` преобразует `AsyncTask.result` из `Map<String, Object>` в `Map<String, String>` через `Objects.toString(value, null)`.

## Спорные места для следующих этапов

1. `ExternalGatewayOpenApiContractTest` сейчас ищет `docs/openapi`, тогда как актуальный каталог CR002: `docs/external-service-gateway/openapi`. Исправление относится к CR002-T002.
2. Generated `/v3/api-docs` включает dashboard paths, но external-service-gateway YAML в рамках CR002 не должен включать dashboard API. В T009 нужно сохранить это решение в contract checks без ложной обязанности документировать dashboard в `external-gateway-*.yaml`.
3. `AsyncDeliveryMode.SYNC` присутствует в Java enum, но внешний async request запрещает его в constructor. В YAML для async submit нужно документировать только публичные значения `CALLBACK` и `POLLING`, а внутренний `SYNC` не должен появиться как допустимый request value.
4. Ошибки конвертации path variables, например нечисловой `taskId` или не-UUID `externalId`, не покрыты отдельным handler в `ExternalGatewayExceptionHandler`. Следующие этапы не должны обещать для этих случаев стабильный `ErrorResponse`, если не будет отдельного изменения реализации.
5. Callback YAML описывает endpoint сервиса-клиента, а не gateway endpoint. Поэтому он сверяется с сериализацией `CallbackPayload` и HTTP-заголовками `HttpCallbackClient`, а не с generated `/v3/api-docs` приложения.
