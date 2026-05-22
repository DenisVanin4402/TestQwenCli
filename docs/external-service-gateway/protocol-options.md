# Протокол REST/OpenAPI

## Контекст

`external-service-gateway` обслуживает внутренние сервисы `invest-pay`, `user-expertise` и будущих клиентов. У внешнего сервиса есть ограничение `5` одновременных вызовов, поэтому все sync- и async-вызовы проходят через общее управление слотами. Постоянная очередь используется только для асинхронных задач; sync-запросы ждут слот через короткоживущие `sync waiters`.

Документация v1 фиксирует один интеграционный вариант:

```text
REST/OpenAPI для постановки sync/async-запросов и чтения результата
HTTP-обратный вызов для доставки async-результата
GET result как резервное чтение
```

Контракты описаны в OpenAPI:

- `../openapi/external-gateway-sync.yaml`
- `../openapi/external-gateway-async.yaml`
- `../openapi/external-gateway-callback.yaml`

## Синхронный API

```http
POST /v1/external/sync
```

Шлюз ждет слот, выполняет HTTP-вызов внешнего сервиса и возвращает ответ в рамках исходного HTTP-запроса.

Для `POST /v1/external/sync` шлюз проверяет, что `clientService` в теле запроса совпадает с межсервисной идентичностью вызывающего сервиса. Несовпадение считается ошибкой авторизации или валидации.

Если слот не получен до истечения `syncWaitTimeout`, шлюз возвращает `429 Too Many Requests`. `503 Service Unavailable` используется для недоступности шлюза или координатора лимитов, а не как обычный ответ на исчерпание sync SLA.

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

## API постановки async-задачи

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

Шлюз возвращает:

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

Для `POST /v1/external/async` шлюз проверяет, что `clientService` в теле запроса совпадает с межсервисной идентичностью вызывающего сервиса. Это значение определяет область идемпотентности и выбор URL обратного вызова из списка разрешений, но не является самостоятельным источником доверия.

Приоритеты в публичном API задаются строками:

```text
HIGH -> priority_weight = 100
LOW  -> priority_weight = 10
```

Внутренняя очередь сортируется по `priority_weight DESC, available_at ASC, id ASC`.

## Обратный вызов async-задачи

Шлюз вызывает эндпоинт сервиса-клиента:

```http
POST /internal/external-gateway/callbacks
```

Обязательные заголовки:

- `X-Callback-Attempt` - номер попытки доставки, начиная с `1`.

Опциональные заголовки:

- `X-Request-Id` - идентификатор корреляции доставки;
- `X-Gateway-Signature` - подпись тела обратного вызова, если выбрана аутентификация на основе HMAC.

Пример успешного обратного вызова:

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

Пример обратного вызова после исчерпания повторов внешнего сервиса:

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
    "message": "Таймаут внешнего сервиса",
    "retryable": true
  },
  "finishedAt": "2026-05-21T20:31:00Z"
}
```

Для `DONE` поле `result` содержит `Map<String, String>`, а `error` равно `null`. Для `FAILED`, `DEAD` и `CANCELLED` поле `result` равно `null`, а причина передается в структурированном поле `error`. Поле `retryable` означает возможность ручного повтора; автоматические повторы внешнего вызова уже завершены до финального обратного вызова.

Эндпоинт обратного вызова должен быть идемпотентным. Повторная доставка того же `taskId` и статуса должна возвращать `200` или `204` и не создавать дублей в сервисе-клиенте.

## Резервное чтение async-результата

Если обратный вызов не доставлен или сервису-клиенту нужно перечитать результат:

```http
GET /v1/external/async/{taskId}
GET /v1/external/async/by-external-id/{externalId}
```

Для `GET`, `DELETE` и `POST /v1/external/async/{taskId}/retry` шлюз не принимает `clientService` из параметров запроса. Сервис-клиент определяется из межсервисной идентичности, а доступ к задаче ограничивается этим значением.

Резервное чтение идет через HTTP API шлюза, а не через общую схему БД. GET возвращает тот же структурированный `error`, который отправляется в обратном вызове; `lastError`, если он присутствует в реализации, остается только диагностической строкой.

Если при постановке задачи выбран `deliveryMode=POLLING`, шлюз не создает доставку обратного вызова и не требует эндпоинта обратного вызова. Результат читается только через GET, а `callbackDeliveryStatus` равен `NOT_REQUIRED`. Значение по умолчанию для `deliveryMode` в v1 - `CALLBACK`, поэтому сервисы без эндпоинта обратного вызова должны явно передавать `POLLING`.

## Статусы async-задач

```text
PENDING     - задача ожидает доступного слота или задержки перед повтором
IN_PROGRESS - выполняется вызов внешнего сервиса
DONE        - вызов внешнего сервиса успешно завершен, result заполнен
FAILED      - финальная неретраибельная ошибка внешнего сервиса или валидации
DEAD        - повторы вызова внешнего сервиса исчерпаны
CANCELLED   - задача отменена до старта вызова внешнего сервиса
```

Обратный вызов отправляется только после финального статуса `DONE`, `FAILED`, `DEAD` или `CANCELLED`, если для задачи выбран `deliveryMode=CALLBACK`.

## Итоговое решение

Для v1 шлюз использует REST/OpenAPI и HTTP-обратный вызов. Эта модель покрывает текущую нагрузку, поддерживает генерацию клиентов по OpenAPI, сохраняет простую диагностику через HTTP-инструменты и не требует дополнительной протокольной инфраструктуры.
