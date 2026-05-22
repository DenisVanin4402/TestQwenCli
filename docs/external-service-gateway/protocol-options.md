# REST/OpenAPI Protocol

## Контекст

`external-service-gateway` обслуживает внутренние сервисы `invest-pay`, `user-expertise` и будущих клиентов. У внешнего сервиса есть ограничение `5 concurrent calls`, поэтому sync и async вызовы проходят через общий PostgreSQL-координатор слотов. Persistent queue используется только для async-задач; sync-запросы ждут слот через короткоживущие записи `ext_sync_waiters`.

Документация v1 фиксирует один интеграционный вариант:

```text
REST/OpenAPI для sync и async submit/result/cancel/retry
HTTP callback для доставки async-результата
GET result как fallback
PostgreSQL как координатор слотов, очереди и callback-доставки
```

Контракты описаны в OpenAPI:

- `../openapi/external-gateway-sync.yaml`
- `../openapi/external-gateway-async.yaml`
- `../openapi/external-gateway-callback.yaml`

## Sync API

```http
POST /v1/external/sync
```

Gateway ждет `SYNC` слот, выполняет upstream-вызов и возвращает ответ в рамках исходного HTTP-запроса.

В текущей реализации `clientService` берется из тела запроса. Service-to-service identity и сверка caller identity пока не внедрены. `Idempotency-Key` принимается заголовком и передается upstream adapter'у, но gateway не хранит sync-результат по этому ключу.

Если слот не получен до истечения `external-gateway.sync.wait-timeout-ms`, gateway возвращает `429 NO_SLOT_AVAILABLE` и заголовок `Retry-After: 1`.

Пример запроса:

```json
{
  "externalId": "4c48a4dc-3226-4e63-8597-4ee793fc3c3c",
  "clientService": "invest-pay",
  "payload": {
    "operation": "calculate",
    "amount": 1000.50,
    "currency": "RUB"
  }
}
```

Пример успешного ответа:

```json
{
  "externalId": "4c48a4dc-3226-4e63-8597-4ee793fc3c3c",
  "status": "SUCCEEDED",
  "result": {
    "decision": "APPROVED",
    "score": "82",
    "reasonCode": "OK"
  },
  "upstreamStatus": 200,
  "durationMs": 430,
  "upstreamTraceId": null
}
```

`result` в успешном sync-ответе нормализуется в `Map<String, String>`.

## Async Submit API

```http
POST /v1/external/async
```

В текущей реализации `clientService` берется из тела запроса. Значение участвует в уникальном ключе `clientService + externalId` и используется для выбора callback URL из allow-list.

Async идемпотентность реализована не через заголовок `Idempotency-Key`, а через пару:

```text
clientService + externalId
```

Повторный submit с тем же payload, priority и deliveryMode возвращает существующий `taskId` и `alreadyExisted=true`. Повтор с отличающимися полями возвращает `409 IDEMPOTENCY_CONFLICT`.

Пример запроса:

```json
{
  "externalId": "4c48a4dc-3226-4e63-8597-4ee793fc3c3c",
  "clientService": "user-expertise",
  "priority": "LOW",
  "deliveryMode": "CALLBACK",
  "payload": {
    "operation": "enrich-profile",
    "userId": 100500
  }
}
```

Gateway возвращает:

```json
{
  "taskId": 12345,
  "externalId": "4c48a4dc-3226-4e63-8597-4ee793fc3c3c",
  "status": "PENDING",
  "deliveryMode": "CALLBACK",
  "statusUrl": "/v1/external/async/12345",
  "alreadyExisted": false
}
```

Приоритеты в публичном API задаются строками:

```text
HIGH -> priority_weight = 100
LOW  -> priority_weight = 10
```

Внутренняя очередь сортируется по `priority_weight DESC, available_at ASC, id ASC`.

## Async Fallback API

```http
GET /v1/external/async/{taskId}
GET /v1/external/async/by-external-id/{externalId}
DELETE /v1/external/async/{taskId}
POST /v1/external/async/{taskId}/retry
```

Для этих операций текущая реализация использует необязательный заголовок:

```http
X-Client-Service: invest-pay
```

Если заголовок передан, gateway ограничивает lookup задачей этого сервиса-клиента. Если заголовок не передан, lookup не ограничивается `clientService`. Это временное dev-ограничение до внедрения service-to-service identity.

Fallback идет через HTTP API gateway, а не через общую схему БД. GET возвращает тот же `result` и структурированный `error`, который используется в callback payload.

Если при постановке задачи выбран `deliveryMode=POLLING`, gateway не создает callback-доставку и не требует callback endpoint. Результат читается только через GET, а `callbackDeliveryStatus` равен `NOT_REQUIRED`. Значение по умолчанию для `deliveryMode` - `CALLBACK`.

## Async Callback

Gateway вызывает endpoint сервиса-клиента:

```http
POST /internal/external-gateway/callbacks
```

Текущая реализация отправляет:

- `X-Callback-Attempt` - номер попытки доставки, начиная с `1`;
- `X-Request-Id` - correlation id доставки, равный `eventId`.

`X-Gateway-Signature` пока не реализован и не отправляется.

Пример успешного callback:

```json
{
  "eventId": "9eab8bb2-b8e4-4c6e-a1d9-e0d4b7b0d77a",
  "taskId": 12345,
  "externalId": "4c48a4dc-3226-4e63-8597-4ee793fc3c3c",
  "clientService": "user-expertise",
  "status": "DONE",
  "result": {
    "decision": "APPROVED",
    "score": "82",
    "reasonCode": "OK"
  },
  "error": null,
  "finishedAt": "2026-05-21T20:30:00Z"
}
```

Пример callback после исчерпания retry upstream:

```json
{
  "eventId": "33e72e4a-48f2-4140-9f22-9c7339b789ce",
  "taskId": 12346,
  "externalId": "1aaac57e-7922-4f68-bc01-d0b795e0e661",
  "clientService": "user-expertise",
  "status": "DEAD",
  "result": null,
  "error": {
    "code": "UPSTREAM_TRANSIENT_FAILURE",
    "message": "External service timeout",
    "retryable": true
  },
  "finishedAt": "2026-05-21T20:31:00Z"
}
```

Для `DONE` поле `result` содержит `Map<String, String>`, а `error` равно `null`. Для `FAILED`, `DEAD` и `CANCELLED` поле `result` равно `null`, а причина передается в структурированном поле `error`.

Callback endpoint должен быть идемпотентным. Повторная доставка того же `taskId/status` должна возвращать `200` или `204` и не создавать дублей в сервисе-клиенте.

## Статусы Async-Задач

```text
PENDING     - задача ожидает доступного слота или backoff
IN_PROGRESS - upstream-вызов выполняется
DONE        - upstream-вызов успешно завершен, result заполнен
FAILED      - финальная неретраибельная ошибка
DEAD        - retry upstream исчерпаны
CANCELLED   - задача отменена до старта upstream-вызова
```

Callback отправляется только после финального статуса `DONE`, `FAILED`, `DEAD` или `CANCELLED`, если для задачи выбран `deliveryMode=CALLBACK`.

## Итоговое Решение

Для v1 gateway использует REST/OpenAPI, HTTP callback и PostgreSQL как общий координатор. Эта модель покрывает текущий контракт, сохраняет диагностику через HTTP tooling и не требует дополнительной брокерной инфраструктуры.
