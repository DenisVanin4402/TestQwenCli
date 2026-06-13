# CR002-T008: senior architect review

## Итог

Повторная stage-level проверка CR002-T008 выполнена после последней production-правки: по решению пользователя отменены contract-first publisher для `/v3/api-docs`, runtime merge sync/async YAML и отдельный `ExternalGatewayOpenApiController`. `GeneratedOpenApiOperationCustomizer` также не используется. `springdoc.api-docs.path` возвращен на стандартный `/v3/api-docs`.

Текущий verdict: passed. Блокирующих архитектурных замечаний нет. Реализация сохраняет публичное HTTP-поведение sync/async endpoints, оставляет generated `*DTO` только на controller boundary, маппит их в доменные `gateway.model.*` через MapStruct и не меняет persistence, queue lifecycle, callback delivery flow или deployment model.

Человек явно принял риск несовершенного runtime `/v3/api-docs`, потому что текущие внешние потребители gateway являются внутренними сервисами. Canonical OpenAPI contract для CR002 остается в YAML-файлах `test-qwen-cli-app/src/main/resources/openapi` и синхронном зеркале `docs/external-service-gateway/openapi`.

Проверено:

- `work-items.md`, `plan_T008.md`, `execution-progress.md`;
- ADR-009 и ADR-012 в `architecture/decisions.md`, а также релевантные `architecture/README.md`, `03-c4-components.md` и `08-deployment-operations.md`;
- фактический diff T008: `test-qwen-cli-app/pom.xml`, `ExternalSyncController`, `ExternalAsyncController`, `ExternalGatewayExceptionHandler`, `gateway/controller/mapper/*`, `application.properties`, OpenAPI YAML resources/docs mirror, `ExternalGatewayOpenApiContractTest` и controller tests;
- отсутствие `ExternalGatewayOpenApiController`, `GeneratedOpenApiOperationCustomizer`, `jackson-dataformat-yaml`, `/internal/springdoc-api-docs` и `springdoc.swagger-ui.url` в актуальном коде.

Учтены результаты проверок, переданные в review scope:

- `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest,TestQwenCliApplicationTests,ExternalSyncControllerTest,ExternalAsyncControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` - 36 tests, failures/errors/skipped 0;
- `mvn test` - 120 tests, failures/errors/skipped 0.

Тесты в ходе этого review повторно не запускались.

## Соответствие плану

Реализация соответствует актуализированному `plan_T008.md`.

`ExternalSyncController` и `ExternalAsyncController` реализуют generated `V1Api` interfaces. Ручные endpoint-level Spring MVC/OpenAPI annotations из production-контроллеров не возвращены; контроллеры выполняют роль Spring bean, вызывают service layer и делегируют boundary mapping MapStruct-мапперам.

`pom.xml` задает `modelNameSuffix=DTO` для sync, async и callback OpenAPI Generator executions, сохраняет `annotationLibrary=swagger2` для generated interfaces и `containerDefaultToNull=true` для прежней validation semantics required map-полей. Generated JavaBean DTO не протекают в service/repository/persistence-слои.

`application.properties` возвращает стандартный `springdoc.api-docs.path=/v3/api-docs`. Это согласовано с решением пользователя: runtime `/v3/api-docs` является diagnostic springdoc output, а не полной заменой рабочих YAML-контрактов.

OpenAPI YAML в resources и docs mirror сохраняют `pattern: '.*\S.*'` для `clientService`, чтобы generated request DTO поддерживали прежний non-blank validation behavior. `ExternalGatewayOpenApiContractTest` теперь проверяет доступность стандартного springdoc `/v3/api-docs` и наличие основных gateway paths, но не требует полного совпадения runtime OpenAPI с YAML.

Callback YAML намеренно остается отдельным canonical contract для endpoint сервиса-клиента, который gateway вызывает, а не входящим endpoint gateway. Dashboard endpoints также не входят в canonical `external-gateway-*.yaml` без отдельного архитектурного решения. Если стандартный springdoc diagnostic document показывает dashboard surface, это не делает dashboard частью canonical external-service-gateway OpenAPI.

## Производительность

Отказ от custom publisher снижает runtime complexity: приложение больше не читает OpenAPI YAML на startup, не выполняет merge `paths`/`components`, не переименовывает schemas и не хранит собственный собранный `ObjectNode` для `/v3/api-docs`.

`jackson-dataformat-yaml` удален как production dependency. Чтение YAML остается только в тестовом контуре через существующую test dependency/utility, а не в runtime path приложения.

MapStruct mapping остается compile-time generated и выполняется только на HTTP-boundary. OpenAPI Generator и MapStruct annotation processor увеличивают build-time работу, но не добавляют overhead в sync/async business path, lease-слоты, очереди, PostgreSQL operations, callback delivery или schedulers.

Стандартный springdoc `/v3/api-docs` может строить diagnostic model по runtime Spring MVC mappings. Это не влияет на обработку sync/async запросов и допустимо для текущего T008 scope.

## Безопасность

Публичные sync/async endpoints, headers, status codes и JSON body не расширены. Новых входных callback endpoints или произвольных callback URL не добавлено; ADR-006 не затронут.

Validation behavior сохранен:

- omitted `payload` остается `null` в generated request DTO и отклоняется как `400 VALIDATION_ERROR`;
- empty и whitespace-only `clientService` отклоняются на boundary через generated validation из `pattern: '.*\S.*'`;
- `ExternalGatewayExceptionHandler` сохраняет прежние русскоязычные сообщения `payload обязателен` и `clientService обязателен`.

Стандартный springdoc `/v3/api-docs` может быть шире или менее точным, чем canonical YAML: он может показать runtime endpoints вне `external-gateway-*.yaml`, а также может раскрыть Java naming details generated `*DTO` в schema names или `$ref`. Этот риск принят человеком для T008. Важное ограничение: такой документ не считается canonical external contract; точка правды для потребителей CR002 остается в YAML resources/docs mirror.

Риск callback SSRF, callback allow-list и временного `X-Client-Service` scope не изменены этим этапом.

## Архитектурные приемы

Подход согласован с ADR-012: generated OpenAPI-код используется в явно выбранной роли, а публичное поведение API не меняется ради генерации.

Разделение слоев сохранено:

- generated API interfaces задают Spring MVC/validation boundary;
- generated `*DTO` остаются boundary-моделями;
- доменные records остаются контрактом service/repository/persistence-слоев;
- MapStruct явно фиксирует mapping между generated DTO и доменными моделями;
- canonical OpenAPI остается в YAML, а runtime `/v3/api-docs` является diagnostic projection Spring MVC state.

Отказ от YAML merge/custom publisher убирает дополнительный runtime слой, который требовал ручного слияния компонентов, разрешения конфликтов schema names и поддержки отдельного `/v3/api-docs` controller. Цена этого упрощения - менее строгий runtime OpenAPI document, что принято человеком.

Прежние замечания review про `ExternalGatewayOpenApiController`, merge sync/async YAML, normalization `ResultMap` и negative checks именно для custom publisher сняты как неактуальные: соответствующий production-код удален.

Решение не меняет C4 boundaries, sync/async sequence flow, data/state, deployment/operations или production invariants. Обязательная финальная проверка необходимости архитектурных правок остается в CR002-T010; для T008 отдельная правка architecture docs не требуется.

## Замечания

Активных `critical`, `high` или `medium` замечаний нет. Активных `pending` замечаний по T008 после последней правки нет.

1. severity: note  
   ссылка: `docs/external-service-gateway/chrequests/CR002/plan_T008.md`, разделы "Архитектурный подход" и "Публичные контракты"; `test-qwen-cli-app/src/main/resources/application.properties`; `test-qwen-cli-app/src/test/java/com/example/testqwencli/gateway/controller/ExternalGatewayOpenApiContractTest.java`  
   риск: стандартный springdoc `/v3/api-docs` может быть неполной или более широкой diagnostic view относительно canonical YAML. В частности, в нем могут появляться dashboard paths, Java schema names с постфиксом `DTO` или другие runtime details, которые не являются частью `external-gateway-*.yaml`. Если tooling начнет считать этот endpoint canonical contract, возможен дрейф ожиданий потребителей от рабочих YAML-контрактов.  
   предлагаемое действие: не делать production-правок в T008. Считать YAML resources/docs mirror canonical contract, а `/v3/api-docs` - вспомогательным diagnostic endpoint. В CR002-T009 при необходимости усилить проверки именно вокруг YAML и явно не требовать полного совпадения стандартного springdoc output с YAML.  
   статус human approval: accepted. Решение человека от 2026-06-13: риск несовершенного `/v3/api-docs` принят, потому что текущие внешние потребители gateway являются внутренними сервисами.

Ранее выявленные validation gaps по omitted `payload` и blank `clientService` технически устранены в рамках T008 и повторно проверены controller tests. Активным архитектурным риском они больше не считаются.

## Рекомендация

Закрыть CR002-T008 после human approval без дополнительных production-правок. Текущая реализация соответствует выбранному компромиссу: generated interfaces/DTO используются на controller boundary, domain mapping выполнен через MapStruct, canonical contract остается в YAML, а стандартный `/v3/api-docs` принят как несовершенный diagnostic output.

По правилу CR не начинать CR002-T009 до явной команды пользователя.

## Human approval

Статус review: passed.

Статус замечаний:

- замечание 1: accepted, риск стандартного springdoc `/v3/api-docs` принят человеком без production-доработок в T008.

Активных замечаний со статусом `pending` нет.

Требуется human approval на закрытие CR002-T008, если оно еще не было дано отдельно.

Production-код, `pom.xml`, тесты и другие документы в ходе этого review не изменялись.
