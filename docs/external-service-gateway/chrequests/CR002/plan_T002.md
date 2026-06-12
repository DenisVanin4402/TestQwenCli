# CR002-T002: план исправления пути OpenAPI contract test

## Цель этапа

Исправить `ExternalGatewayOpenApiContractTest`, чтобы contract test читал актуальные OpenAPI-спецификации CR002 из `docs/external-service-gateway/openapi`, а не устаревший каталог `docs/openapi`.

## Выбранный подход

Этап выполняется как точечная правка тестовой инфраструктуры без изменения production-кода, контроллеров, DTO и OpenAPI YAML.

До переноса спецификаций в `test-qwen-cli-app/src/main/resources/openapi` рабочим источником для теста остается `docs/external-service-gateway/openapi`. Резолвер каталога должен поддерживать два ожидаемых запуска:

- из корня репозитория;
- из Maven-модуля `test-qwen-cli-app`.

Fallback на `docs/openapi` не сохраняется, потому что ADR-012 явно фиксирует, что этот каталог не считается поддерживаемым источником для CR002.

## Затронутые файлы и границы компонентов

Планируемые изменения:

- `test-qwen-cli-app/src/test/java/com/example/testqwencli/gateway/controller/ExternalGatewayOpenApiContractTest.java`;
- `docs/external-service-gateway/chrequests/CR002/execution-progress.md`;
- `docs/external-service-gateway/chrequests/CR002/review_T002.md` после senior architect review.

Runtime-компоненты gateway, dashboard и callback-доставка не меняются. Границы C4-компонентов, sequence flow, persistence state и deployment model не меняются.

## Публичные контракты

Публичное HTTP-поведение не меняется. Этап меняет только путь, по которому тест читает документированный OpenAPI-контракт.

Если после исправления пути тест обнаружит расхождение между текущими YAML и generated `/v3/api-docs`, это не будет исправляться в T002 через правку контроллеров или YAML. Такие расхождения относятся к T003-T005 или T009.

## Data, State, Deployment, Operations

Этап не меняет схему данных, состояние задач, миграции Liquibase, runtime-конфигурацию, Docker/Testcontainers-контур или операционные правила.

Maven lifecycle не меняется: тест остается обычным JUnit/Spring Boot Test в `mvn test`. Рабочий вход генератора появится позже в T006-T007.

## Тестовая стратегия

Основная проверка этапа:

- запустить точечный тест `ExternalGatewayOpenApiContractTest` через Maven из корня репозитория;
- убедиться, что при текущем расположении YAML тест читает `docs/external-service-gateway/openapi`.

Если точечный тест падает из-за уже существующего расхождения YAML с контроллерами, в `execution-progress.md` нужно зафиксировать, что путь исправлен, а содержательное расхождение переносится в следующие этапы CR002.

## Риски

- Риск сломать запуск теста из Maven-модуля, если резолвер будет завязан только на корень репозитория.
- Риск скрыть ошибку отсутствия актуального каталога через fallback на устаревший `docs/openapi`.
- Риск начать синхронизацию YAML в T002, хотя задача этапа ограничена путем чтения.

Снижение рисков: проверять только ожидаемые каталоги CR002, выдавать понятное сообщение об ошибке и не менять YAML в рамках T002.

## Критерии отката

Откат этапа означает возврат прежнего резолвера каталога в `ExternalGatewayOpenApiContractTest.java`, удаление `plan_T002.md`, `review_T002.md` и записей T002 из `execution-progress.md`. Production-код и YAML на этапе не меняются.
