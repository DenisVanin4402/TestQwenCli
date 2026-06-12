# CR002-T004: план синхронизации async YAML

## Цель этапа

Привести `docs/external-service-gateway/openapi/external-gateway-async.yaml` к фактическому контракту `ExternalAsyncController`, async DTO, enum-классов и `ExternalGatewayExceptionHandler`.

## Выбранный подход

Этап выполняется как точечная синхронизация OpenAPI YAML без изменения production-кода, контроллеров, DTO, Maven-настроек и общей тестовой логики.

Источники истины:

- `ExternalAsyncController` для путей, HTTP-методов, `operationId`, headers, path variables, request body и response status codes;
- `ExternalAsyncRequest`, `AsyncSubmitResponse`, `AsyncTask`, `TaskError`, `AsyncPayloads` и enum-классы async-моделей для schemas;
- `ExternalGatewayExceptionHandler` и gateway exception classes для error response;
- `ExternalGatewayOpenApiContractTest` как быстрая проверка стабильных частей YAML против generated `/v3/api-docs`.

Если YAML строже фактической Java-модели без validation annotation, constructor check или явно зафиксированного поведения, YAML ослабляется до фактического контракта. Если обнаружено поведение, которое Spring MVC сейчас не стабилизирует через handler, YAML не должен обещать стабильный `ErrorResponse` для такого случая.

## Затронутые файлы и границы компонентов

Планируемые изменения:

- `docs/external-service-gateway/openapi/external-gateway-async.yaml`;
- `docs/external-service-gateway/chrequests/CR002/execution-progress.md`;
- `docs/external-service-gateway/chrequests/CR002/review_T004.md` после senior architect review.

Не планируются изменения:

- `ExternalAsyncController`, async DTO и сервисы;
- sync/callback YAML;
- `pom.xml`, generated sources и перенос YAML в resources;
- архитектурные документы.

Границы gateway, dashboard и callback-компонентов не меняются.

## Публичные контракты

Публичное HTTP-поведение не меняется. YAML должен описывать фактическое поведение:

- `POST /v1/external/async`;
- `GET /v1/external/async/{taskId}`;
- `DELETE /v1/external/async/{taskId}`;
- `GET /v1/external/async/by-external-id/{externalId}`;
- `POST /v1/external/async/{taskId}/retry`;
- optional headers `X-Request-Id` и `X-Client-Service`;
- response statuses `200`, `202`, `400`, `404`, `409`;
- schemas `ExternalAsyncRequest`, `AsyncSubmitResponse`, `AsyncTask`, `TaskError`, `ErrorResponse`.

`X-Client-Service` остается временным optional scope доступа для read/cancel/retry до внедрения service-to-service identity. `AsyncDeliveryMode.SYNC` не является допустимым значением входного async request и не публикуется как значение внешних async response. Для response используется отдельная OpenAPI-схема со значениями `CALLBACK` и `POLLING`, а `SYNC` остается внутренней деталью Java-модели sync trace-строк.

## Data, State, Deployment, Operations

Этап не меняет data/state модель: таблицы очереди, idempotency, callback delivery state, dispatcher claim и retry/cancel flows остаются прежними.

Deployment, operations и Maven lifecycle не меняются. Перенос YAML в resources и генерация кода выполняются позже в T006-T007.

## Тестовая стратегия

После YAML-правки выполнить точечный contract test:

```text
mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Если изменение потребует уточнения response/status behavior, дополнительно запустить async controller tests.

## Риски

- Риск задокументировать `additionalProperties: false`, хотя Java records и Jackson не запрещают неизвестные JSON-поля без отдельной настройки.
- Риск сузить `payload` или `result` до `Map<String, String>`, хотя async модели используют `Map<String, Object>`.
- Риск случайно разрешить `SYNC` во входном async request, хотя constructor `ExternalAsyncRequest` его запрещает.
- Риск случайно опубликовать `SYNC` как значение внешних async response, хотя внешние async endpoints возвращают только async-задачи `CALLBACK`/`POLLING`.
- Риск обещать стабильный error body для ошибок конвертации path variables, которые не покрыты отдельным handler.

Снижение рисков: сверять YAML только с `ExternalAsyncController`, async DTO и уже зафиксированной инвентаризацией T001, а после правки запускать быстрый OpenAPI contract test.

## Критерии отката

Откат этапа означает возврат изменений в `external-gateway-async.yaml`, удаление `plan_T004.md`, `review_T004.md` и записей T004 из `execution-progress.md`. Production-код на этапе не меняется.
