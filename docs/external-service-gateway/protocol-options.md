# REST/OpenAPI Protocol

## Контекст

`external-service-gateway` обслуживает внутренние сервисы `invest-pay`, `user-expertise` и будущих клиентов. У внешнего сервиса есть ограничение `5 concurrent calls`, поэтому все sync и async вызовы проходят через общий Slot Manager. Persistent queue используется только для async-задач; sync-запросы ждут слот через короткоживущие `sync waiters`.

Документация v1 фиксирует один интеграционный вариант:

```text
REST/OpenAPI для sync и async submit/result
HTTP callback для доставки async-результата
GET result как fallback
```

Контракты описаны в OpenAPI:

- `../openapi/external-gateway-sync.yaml`
- `../openapi/external-gateway-async.yaml`
- `../openapi/external-gateway-callback.yaml`

## Sync API

```http
POST /v1/external/sync
```

Gateway ждет слот, выполняет upstream HTTP-вызов и возвращает ответ в рамках исходного HTTP-запроса.

Для `POST /v1/external/sync` gateway проверяет, что `clientService` в теле запроса совпадает с service-to-service identity вызывающего сервиса. Несовпадение считается ошибкой авторизации или валидации.

Если slot не получен до истечения `syncWaitTimeout`, gateway возвращает `429 Too Many Requests`. `503 Service Unavailable` используется для недоступности gateway или координатора лимитов, а не как обычный ответ на исчерпание sync SLA.

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
    "score": "82"
  },
  "upstreamStatus": 200,
  "durationMs": 430
}
```

`result` в успешном sync-ответе нормализуется в `Map<String, String>`.

## Async Submit API

```http
POST /v1/external/async
```

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

Для `POST /v1/external/async` gateway проверяет, что `clientService` в теле запроса совпадает с service-to-service identity вызывающего сервиса. Это значение определяет scope идемпотентности и выбор callback URL из allow-list, но не является самостоятельным источником доверия.

Приоритеты в публичном API задаются строками:

```text
HIGH -> priority_weight = 100
LOW  -> priority_weight = 10
```

Внутренняя очередь сортируется по `priority_weight DESC, available_at ASC, id ASC`.

## Async Callback

Gateway вызывает endpoint сервиса-клиента:

```http
POST /internal/external-gateway/callbacks
```

Обязательные заголовки:

- `X-Callback-Attempt` - номер попытки доставки, начиная с `1`.

Опциональные заголовки:

- `X-Request-Id` - correlation id доставки;
- `X-Gateway-Signature` - подпись тела callback, если выбран HMAC-based auth.

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
    "code": "UPSTREAM_TIMEOUT",
    "message": "External service timeout",
    "retryable": true
  },
  "finishedAt": "2026-05-21T20:31:00Z"
}
```

Для `DONE` поле `result` содержит `Map<String, String>`, а `error` равно `null`. Для `FAILED`, `DEAD` и `CANCELLED` поле `result` равно `null`, а причина передается в структурированном поле `error`. Поле `retryable` означает возможность ручного retry; автоматические retry upstream уже завершены до финального callback.

Callback endpoint должен быть идемпотентным. Повторная доставка того же `taskId` и статуса должна возвращать `200` или `204` и не создавать дублей в сервисе-клиенте.

## Async Fallback API

Если callback не доставлен или сервису-клиенту нужно перечитать результат:

```http
GET /v1/external/async/{taskId}
GET /v1/external/async/by-external-id/{externalId}
```

Для `GET`, `DELETE` и `POST /v1/external/async/{taskId}/retry` gateway не принимает `clientService` из параметров запроса. Сервис-клиент определяется из service-to-service identity, а доступ к задаче ограничивается этим значением.

Fallback идет через HTTP API gateway, а не через общую схему БД. GET возвращает тот же структурированный `error`, который отправляется в callback; `lastError`, если он присутствует в реализации, остается только диагностической строкой.

Если при постановке задачи выбран `deliveryMode=POLLING`, gateway не создает callback-доставку и не требует callback endpoint. Результат читается только через GET, а `callbackDeliveryStatus` равен `NOT_REQUIRED`. Значение по умолчанию для `deliveryMode` в v1 - `CALLBACK`, поэтому сервисы без callback endpoint должны явно передавать `POLLING`.

## Статусы Async-Задач

```text
PENDING     - задача ожидает доступного слота или backoff
IN_PROGRESS - upstream-вызов выполняется
DONE        - upstream-вызов успешно завершен, result заполнен
FAILED      - финальная неретраибельная ошибка upstream или валидации
DEAD        - retry upstream исчерпаны
CANCELLED   - задача отменена до старта upstream-вызова
```

Callback отправляется только после финального статуса `DONE`, `FAILED`, `DEAD` или `CANCELLED`, если для задачи выбран `deliveryMode=CALLBACK`.

## Итоговое Решение

Для v1 gateway использует REST/OpenAPI и HTTP callback. Эта модель покрывает текущую нагрузку, поддерживает генерацию клиентов по OpenAPI, сохраняет простую диагностику через HTTP tooling и не требует дополнительной протокольной инфраструктуры.
