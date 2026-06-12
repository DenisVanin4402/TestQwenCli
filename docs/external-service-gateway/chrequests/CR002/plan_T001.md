# CR002-T001: план инвентаризации фактического HTTP API

## Цель этапа

Зафиксировать фактический HTTP API приложения перед правкой OpenAPI YAML, чтобы следующие этапы CR002 синхронизировали документы с контроллерами и DTO, а не наоборот.

## Выбранный подход

Этап выполняется как документационная инвентаризация без изменения production-кода, тестов и OpenAPI YAML. Источниками истины считаются:

- `ExternalSyncController`, `ExternalAsyncController`, `ExternalGatewayExceptionHandler` и DTO из `gateway.model.*` для входящего gateway API;
- `CallbackPayload` и `HttpCallbackClient` для исходящего callback-контракта;
- `DashboardController` и `HomeController` только для явного решения о границах CR002.

Результат этапа фиксируется отдельным файлом `inventory_T001.md` рядом с планом и журналом CR002. В нем должны быть перечислены endpoints, HTTP-методы, заголовки, статусы, response schemas, DTO-поля, enum-значения и спорные места для следующих этапов.

## Затронутые файлы и границы компонентов

Планируемые изменения:

- `docs/external-service-gateway/chrequests/CR002/inventory_T001.md`;
- `docs/external-service-gateway/chrequests/CR002/execution-progress.md`;
- `docs/external-service-gateway/chrequests/CR002/review_T001.md` после senior architect review.

Runtime-модули `test-qwen-cli-app`, `dashboard-backend` и `dashboard-ui` не меняются. Границы компонентов, описанные в C4-документах, не меняются.

## Публичные контракты

Публичное HTTP-поведение не меняется. На этапе только фиксируются фактические контракты:

- sync API: `POST /v1/external/sync`;
- async API: `POST /v1/external/async`, `GET /v1/external/async/{taskId}`, `DELETE /v1/external/async/{taskId}`, `GET /v1/external/async/by-external-id/{externalId}`, `POST /v1/external/async/{taskId}/retry`;
- callback API сервиса-клиента: `POST /internal/external-gateway/callbacks`;
- dashboard и root endpoint явно помечаются как вне YAML `external-gateway-*.yaml` в рамках CR002.

Если в ходе инвентаризации найдено расхождение с YAML, оно не исправляется в T001, а переносится в соответствующие этапы T003-T005 или T009.

## Data, State, Deployment, Operations

Этап не меняет модель данных, состояние очередей, миграции Liquibase, deployment-модель, настройки Maven-сборки или операционные правила. Архитектурные документы не требуют изменения на этом этапе, потому что инвентаризация не меняет архитектурные договоренности.

## Тестовая стратегия

Так как этап документирует уже существующее поведение без изменения кода, обязательный запуск `mvn test` не требуется именно для T001. Проверка этапа выполняется ревью содержимого `inventory_T001.md` относительно контроллеров, DTO, exception handler, callback client и существующих controller/callback tests.

Если в ходе чтения кода будет обнаружен подозрительный дефект реализации, он не исправляется в T001 без отдельного решения и теста.

## Риски

- Риск неполной инвентаризации DTO из-за полей, которые задаются конструкторами record-классов или сериализуются как `null`.
- Риск смешать gateway API и dashboard API, хотя CR002 синхронизирует только external-service-gateway OpenAPI YAML.
- Риск ошибочно принять текущий YAML за источник истины вместо контроллеров и DTO.

Снижение рисков: сверять контроллеры вместе с DTO, exception handler, callback client и существующими тестами; спорные места фиксировать отдельно.

## Критерии отката

Откат этапа означает удаление `inventory_T001.md`, `review_T001.md` и записей о T001 из `execution-progress.md`. Runtime-код и YAML на этапе не меняются, поэтому функциональный rollback не требуется.
