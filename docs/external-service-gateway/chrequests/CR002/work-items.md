# CR002: синхронизация OpenAPI YAML с контроллерами

## Назначение

CR002 фиксирует работу по приведению OpenAPI-документов из `docs/external-service-gateway/openapi` к фактическому HTTP API приложения.

Точка правды для sync и async API - Spring-контроллеры и модели приложения:

- `ExternalSyncController` для `external-gateway-sync.yaml`;
- `ExternalAsyncController` для `external-gateway-async.yaml`;
- `ExternalGatewayExceptionHandler` и `ErrorResponse` для error contract;
- DTO из `gateway.model.*` для request/response schemas.

Точка правды для callback-контракта - фактическая сериализация `CallbackPayload` и поведение `HttpCallbackClient`, потому что callback endpoint реализует внешний сервис-клиент, а gateway его вызывает.

Если YAML и контроллеры расходятся, в рамках CR002 по умолчанию исправляется YAML. Изменять контроллеры можно только если при сверке найден реальный дефект реализации; такое изменение должно быть оформлено явно и покрыто тестом.

## Объем

Включено:

- перед запуском каждого этапа `CR002-TXXX` подготовить stage-level `plan_TXXX.md`;
- сверить пути, HTTP-методы, `operationId`, параметры, заголовки, request body, response status codes и response schemas;
- сверить обязательные поля, enum-значения, форматы, минимальные и максимальные ограничения DTO;
- синхронизировать `external-gateway-sync.yaml`, `external-gateway-async.yaml`, `external-gateway-callback.yaml`;
- перенести синхронизированные спецификации в `test-qwen-cli-app/src/main/resources/openapi`;
- подключить генерацию кода из OpenAPI во время Maven-сборки через `org.openapitools:openapi-generator-maven-plugin`;
- выполнить рефакторинг приложения так, чтобы generated sources использовались в согласованной с проектом роли;
- исправить проверку OpenAPI-контракта так, чтобы она не читала устаревший `docs/openapi` и учитывала рабочий вход генератора;
- усилить contract test на дрейф между generated `/v3/api-docs` и YAML там, где это стабильно проверяется;
- после реализации каждого этапа `CR002-TXXX` провести senior architect review и оформить `review_TXXX.md`;
- проверить, требуется ли обновление архитектурной документации после выбора роли generated OpenAPI-кода;
- зафиксировать результат проверки через `mvn test`.

Не включено:

- изменение публичного поведения API ради совпадения с YAML;
- документирование dashboard API в этих YAML, если не будет принято отдельное решение включить dashboard в external-service-gateway contract;
- service-to-service authentication, подпись callback и другие новые протокольные возможности.

## Очередь запуска

| Порядок | Задача | Приоритет | Результат |
| --- | --- | --- | --- |
| 1 | CR002-T001: инвентаризация фактического HTTP API | P0 | Есть список endpoints, параметров, статусов и DTO по контроллерам. |
| 2 | CR002-T002: исправление пути OpenAPI contract test | P0 | Тест читает актуальный каталог OpenAPI, а не устаревший `docs/openapi`. |
| 3 | CR002-T003: синхронизация sync YAML | P0 | `external-gateway-sync.yaml` соответствует `ExternalSyncController` и sync DTO. |
| 4 | CR002-T004: синхронизация async YAML | P0 | `external-gateway-async.yaml` соответствует `ExternalAsyncController` и async DTO. |
| 5 | CR002-T005: синхронизация callback YAML | P0 | `external-gateway-callback.yaml` соответствует `CallbackPayload` и фактическим callback-заголовкам. |
| 6 | CR002-T006: перенос спецификаций в resources | P0 | Синхронизированные YAML лежат в `test-qwen-cli-app/src/main/resources/openapi`. |
| 7 | CR002-T007: генерация кода из OpenAPI при сборке | P0 | Maven подключает `org.openapitools:openapi-generator-maven-plugin`, generated sources компилируются. |
| 8 | CR002-T008: рефакторинг на generated OpenAPI-контракты | P1 | Приложение использует сгенерированный код в выбранных точках без изменения публичного поведения. |
| 9 | CR002-T009: усиление OpenAPI contract checks | P1 | Тест ловит базовый дрейф YAML от контроллеров по стабильным частям контракта. |
| 10 | CR002-T010: проверка архитектурной документации | P2 | Зафиксировано, требуется ли обновление `docs/external-service-gateway/architecture`; при необходимости внесены точечные правки. |
| 11 | CR002-T011: финальная проверка | P0 | `mvn test` проходит после синхронизации и рефакторинга. |

## Правила выполнения этапов

- Когда этап `CR002-TXXX` берется в работу, сначала создается или обновляется `docs/external-service-gateway/chrequests/CR002/plan_TXXX.md`.
- `plan_TXXX.md` должен фиксировать выбранный подход именно для этого этапа, затронутые файлы и модули, влияние на контракты, data/state/deployment/operations, тестовую стратегию, риски и критерии отката.
- После реализации этапа и до его закрытия senior architect agent создает `docs/external-service-gateway/chrequests/CR002/review_TXXX.md`.
- `review_TXXX.md` проверяет соответствие реализации `work-items.md`, `plan_TXXX.md`, ADR и архитектурной документации, а также риски производительности, безопасности и архитектурных приемов.
- Замечания из `review_TXXX.md` принимаются, отклоняются или откладываются только после human approval; решение фиксируется в `execution-progress.md` или в самом `review_TXXX.md`.

## Детализация задач

### CR002-T001: инвентаризация фактического HTTP API

Цель: зафиксировать текущее поведение контроллеров перед правкой YAML.

Объем работ:

- Выписать endpoints из `ExternalSyncController`, `ExternalAsyncController`, `DashboardController` и `HomeController`.
- Отметить, какие endpoints входят в `docs/external-service-gateway/openapi`, а какие остаются вне этих YAML.
- Сверить error responses по `ExternalGatewayExceptionHandler`.
- Сверить DTO и validation annotations для request/response schemas.
- Зафиксировать спорные места отдельными решениями, не смешивая их с механической синхронизацией YAML.

Критерии приемки:

- Для каждого YAML-файла указан контроллер или класс, который является источником истины.
- Dashboard и root endpoint явно отмечены как включенные или исключенные из CR002.
- Нет правок контроллеров без отдельного обоснования.

### CR002-T002: исправление пути OpenAPI contract test

Цель: убрать риск, что тест сверяет неактуальный каталог.

Объем работ:

- Обновить `ExternalGatewayOpenApiContractTest`, чтобы он находил актуальные OpenAPI-спецификации из корня репозитория и из Maven-модуля.
- До переноса спецификаций использовать `docs/external-service-gateway/openapi`; после переноса использовать `test-qwen-cli-app/src/main/resources/openapi` или явно проверять синхронность двух каталогов.
- Убрать или сохранить fallback на `docs/openapi` только если такой каталог действительно поддерживается проектом.
- Проверить диагностическое сообщение при отсутствии каталога.

Критерии приемки:

- Тест падает с понятным сообщением, если актуальный каталог OpenAPI отсутствует.
- Тест не зависит от локального `user.dir`, кроме ожидаемых вариантов запуска из корня и из `test-qwen-cli-app`.

### CR002-T003: синхронизация sync YAML

Цель: привести `external-gateway-sync.yaml` к `ExternalSyncController`.

Объем работ:

- Сверить `POST /v1/external/sync`.
- Сверить `operationId=callExternalSync`.
- Сверить заголовки `X-Request-Id` и `Idempotency-Key`.
- Сверить статусы `200`, `400`, `429`, `503`, `504`.
- Сверить схемы `ExternalSyncRequest`, `ExternalSyncResponse`, `ErrorResponse`.

Критерии приемки:

- YAML описывает фактические request/response схемы и ограничения DTO.
- Отличия от generated `/v3/api-docs` либо устранены в YAML, либо явно объяснены в тесте как намеренные.

### CR002-T004: синхронизация async YAML

Цель: привести `external-gateway-async.yaml` к `ExternalAsyncController`.

Объем работ:

- Сверить `POST /v1/external/async`.
- Сверить `GET /v1/external/async/{taskId}`.
- Сверить `DELETE /v1/external/async/{taskId}`.
- Сверить `GET /v1/external/async/by-external-id/{externalId}`.
- Сверить `POST /v1/external/async/{taskId}/retry`.
- Сверить заголовки `X-Request-Id` и `X-Client-Service`.
- Сверить статусы `200`, `202`, `400`, `404`, `409`.
- Сверить схемы `ExternalAsyncRequest`, `AsyncSubmitResponse`, `AsyncTask`, `TaskError`, `ErrorResponse` и связанные enum-значения.

Критерии приемки:

- YAML соответствует фактическим status codes контроллера.
- Для `X-Client-Service` явно отражено текущее временное поведение scope доступа.
- Поля async DTO и enum-значения совпадают с кодом.

### CR002-T005: синхронизация callback YAML

Цель: привести `external-gateway-callback.yaml` к фактическому callback payload, который отправляет gateway.

Объем работ:

- Сверить путь `POST /internal/external-gateway/callbacks` как контракт сервиса-клиента.
- Сверить заголовки `X-Callback-Attempt` и `X-Request-Id`.
- Сверить сериализуемые поля `CallbackPayload`.
- Сверить значения статусов async-задачи, которые могут прийти в callback.
- Проверить, что описание retry/backoff callback-доставки не обещает поведения, которого нет в gateway.

Критерии приемки:

- YAML соответствует `CallbackPayload`, `HttpCallbackClient` и тестовым callback-сценариям.
- Контракт ясно отделяет endpoint сервиса-клиента от endpoints самого gateway.

### CR002-T006: перенос спецификаций в resources

Цель: сделать синхронизированные OpenAPI-файлы входом сборки приложения.

Объем работ:

- Создать каталог `test-qwen-cli-app/src/main/resources/openapi`, если он отсутствует.
- Разместить там актуальные `external-gateway-sync.yaml`, `external-gateway-async.yaml`, `external-gateway-callback.yaml`.
- Решить, остаются ли файлы в `docs/external-service-gateway/openapi` исходной документацией с копией в resources или документация начинает ссылаться на resources.
- Обновить ссылки в тестах и документации так, чтобы не появилось две расходящиеся копии без проверки.

Критерии приемки:

- Maven-сборка видит OpenAPI-спецификации из `test-qwen-cli-app/src/main/resources/openapi`.
- В проекте явно понятно, какой путь является рабочим входом для генератора.
- Если сохраняются две копии YAML, есть тест или проверка, которая ловит их расхождение.

### CR002-T007: генерация кода из OpenAPI при сборке

Цель: подключить `org.openapitools:openapi-generator-maven-plugin` к сборке `test-qwen-cli-app`.

Объем работ:

- Добавить `openapi-generator-maven-plugin` в `test-qwen-cli-app/pom.xml`.
- Настроить генерацию из sync, async и callback спецификаций.
- Выбрать генератор и режим, подходящие для Spring Boot 3 и Java 21.
- Направить generated sources в `target/generated-sources/openapi`, не коммитя сгенерированный код без отдельного решения.
- Настроить Maven так, чтобы generated sources участвовали в компиляции.
- Зафиксировать выбранные package names для generated API/model классов.

Критерии приемки:

- `mvn test` запускает генерацию до компиляции.
- Сгенерированные классы компилируются на Java 21.
- Генератор не ломает текущую ручную реализацию контроллеров до шага рефакторинга.
- В `pom.xml` нет неиспользуемых или конфликтующих зависимостей.

### CR002-T008: рефакторинг на generated OpenAPI-контракты

Цель: начать использовать сгенерированный OpenAPI-код без изменения публичного поведения API.

Объем работ:

- Определить целевую роль generated sources: интерфейсы контроллеров, DTO, API delegates или клиентские модели.
- Сравнить сгенерированные DTO с текущими `gateway.model.*` и выбрать минимальный путь миграции.
- Если используются generated API interfaces, подключить их к текущим контроллерам через `implements` или adapter слой.
- Если используются generated models, обновить mapping и тесты без изменения JSON-контракта.
- Сохранить старые доменные модели там, где они нужны бизнес-логике и persistence-слою.
- Не смешивать большой доменный рефакторинг с механическим переходом на generated OpenAPI-контракты.

Критерии приемки:

- Публичные endpoints, status codes и JSON body остаются прежними.
- Контроллеры или adapter слой используют generated OpenAPI-код в явно выбранной роли.
- Быстрые controller/OpenAPI contract tests подтверждают отсутствие регрессии.

### CR002-T009: усиление OpenAPI contract checks

Цель: сделать дрейф YAML от контроллеров заметным в быстром тестовом контуре.

Объем работ:

- Расширить `ExternalGatewayOpenApiContractTest` на стабильные части параметров, required fields, response schemas и enum-значений.
- Не проверять шумные части generated OpenAPI, которые часто меняются из-за порядка полей или внутренних деталей springdoc.
- Сохранить callback-проверку через сериализацию `CallbackPayload`.
- Явно решить, должны ли dashboard paths проверяться в этом тесте или вынесены в отдельный dashboard contract.

Критерии приемки:

- Изменение публичного пути, метода, `operationId`, response status или DTO-поля ломает тест.
- Тест остается быстрым и выполняется в `mvn test`.

### CR002-T010: проверка архитектурной документации

Цель: определить, меняет ли CR002 архитектурные договоренности, которые должны быть отражены в `docs/external-service-gateway/architecture`.

Объем работ:

- Сверить фактический результат CR002 с архитектурными документами: C4 components, sequence diagrams, data/state и operations.
- Если generated OpenAPI-код используется только как build-time contract/interface без изменения границ компонентов, runtime flow, persistence state или deployment model, явно зафиксировать, что архитектурная документация не требует правок.
- Если выбранная роль generated sources меняет ответственность компонентов, последовательности вызовов, входы сборки или операционные правила, обновить только затронутые архитектурные документы.
- Зафиксировать решение в `execution-progress.md`.

Критерии приемки:

- Есть явное решение: архитектурная документация либо не требует изменений, либо обновлена точечно.
- В архитектурные документы не добавлены низкоуровневые детали реализации, если они не влияют на архитектурные договоренности.
- `execution-progress.md` перечисляет измененные архитектурные файлы или фиксирует, что изменений не потребовалось.

### CR002-T011: финальная проверка

Цель: подтвердить, что синхронизация, генерация и рефакторинг не сломали быстрый контур.

Объем работ:

- Запустить `mvn test`.
- Зафиксировать результат в `execution-progress.md`.
- Если найдено расхождение до включения генерации, обновить YAML или тест согласно правилу "точка правды - контроллеры".
- Если найдено расхождение после включения генерации, сначала определить, ошибка в спецификации, настройке генератора или adapter/refactoring слое.

Критерии приемки:

- `mvn test` проходит.
- В `execution-progress.md` указаны измененные YAML-файлы, Maven-настройки, refactoring-изменения, измененные тесты и результат проверки.

## Общие правила приемки CR002

- Все новые документы и комментарии написаны на русском языке.
- Контроллеры и DTO являются источником истины для gateway API.
- YAML из `docs/external-service-gateway/openapi` не описывает несуществующие endpoints, статусы, поля или ограничения.
- YAML, используемый генератором из `test-qwen-cli-app/src/main/resources/openapi`, синхронизирован с документированным OpenAPI-контрактом.
- `openapi-generator-maven-plugin` участвует в сборке `test-qwen-cli-app` и не требует ручного запуска отдельной команды.
- Contract test читает рабочие спецификации из `test-qwen-cli-app/src/main/resources/openapi` или явно проверяет их синхронность с `docs/external-service-gateway/openapi`.
- Перед реализацией каждого этапа CR002 создан или актуализирован соответствующий `plan_TXXX.md`.
- Перед закрытием каждого этапа CR002 создан соответствующий `review_TXXX.md`, а замечания обработаны после human approval.
- Любое изменение поведения API сопровождается отдельным явным обоснованием и тестом.
