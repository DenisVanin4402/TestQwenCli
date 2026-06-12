# CR002-T003: план синхронизации sync YAML

## Цель этапа

Привести `docs/external-service-gateway/openapi/external-gateway-sync.yaml` к фактическому контракту `ExternalSyncController`, sync DTO и `ExternalGatewayExceptionHandler`.

## Выбранный подход

Этап выполняется как точечная синхронизация OpenAPI YAML без изменения production-кода, контроллеров, DTO, Maven-настроек и тестовой логики.

Источники истины:

- `ExternalSyncController` для пути, HTTP-метода, `operationId`, headers, request body и response status codes;
- `ExternalSyncRequest`, `ExternalSyncResponse`, `ExternalSyncStatus` для sync schemas;
- `ExternalGatewayExceptionHandler` и gateway exception classes для error response;
- `ExternalSyncControllerTest` и `ExternalGatewayOpenApiContractTest` как проверка стабильных частей контракта.

Если YAML окажется строже фактической Java-модели без явного validation/constructor ограничения, YAML нужно ослабить до фактического поведения. Если найдено спорное место, оно фиксируется в `execution-progress.md`, а не исправляется через изменение контроллера в T003.

## Затронутые файлы и границы компонентов

Планируемые изменения:

- `docs/external-service-gateway/openapi/external-gateway-sync.yaml`;
- `docs/external-service-gateway/chrequests/CR002/execution-progress.md`;
- `docs/external-service-gateway/chrequests/CR002/review_T003.md` после senior architect review.

Не планируются изменения:

- `ExternalSyncController` и sync DTO;
- async/callback YAML;
- Maven-сборка и generated sources;
- архитектурные документы.

Границы gateway, dashboard и callback-компонентов не меняются.

## Публичные контракты

Публичное HTTP-поведение не меняется. YAML должен описывать фактическое поведение:

- `POST /v1/external/sync`;
- `operationId=callExternalSync`;
- optional headers `X-Request-Id` и `Idempotency-Key`;
- response statuses `200`, `400`, `429`, `503`, `504`;
- schemas `ExternalSyncRequest`, `ExternalSyncResponse`, `ErrorResponse`.

Sync response остается успешным только со статусом `SUCCEEDED`. `Idempotency-Key` документируется как передаваемый в upstream adapter без хранения sync-результата по ключу.

## Data, State, Deployment, Operations

Этап не меняет data/state модель: sync trace, слоты, upstream simulation и error handling остаются прежними.

Deployment, operations и Maven lifecycle не меняются. Перенос YAML в resources и генерация кода выполняются позже в T006-T007.

## Тестовая стратегия

После YAML-правки выполнить точечный contract test:

```text
mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Если изменения затронут response/status/schema части, которые проверяются controller tests, дополнительно запустить `ExternalSyncControllerTest`.

## Риски

- Риск зафиксировать в YAML более строгие ограничения, чем есть в Java DTO и Spring MVC runtime.
- Риск случайно изменить async/callback контракт вне scope T003.
- Риск документировать error codes слишком узко, хотя `ErrorResponse.code` является строкой и часть ошибок относится к async этапам.

Снижение рисков: сверять YAML только с sync controller/DTO/handler и запускать быстрый OpenAPI contract test.

## Критерии отката

Откат этапа означает возврат изменений в `external-gateway-sync.yaml`, удаление `plan_T003.md`, `review_T003.md` и записей T003 из `execution-progress.md`. Production-код на этапе не меняется.
