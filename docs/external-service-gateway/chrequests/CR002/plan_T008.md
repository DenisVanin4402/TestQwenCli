# CR002-T008: план рефакторинга на generated OpenAPI-контракты

## Цель этапа

Начать использовать сгенерированный OpenAPI-код в runtime-слое gateway без изменения публичного HTTP-поведения.

Выбранная роль generated sources для T008:

- generated API interfaces являются источником Spring MVC и validation-аннотаций для входящих sync/async endpoints;
- generated request/response models генерируются с Java-постфиксом `DTO` и используются только на границе контроллеров;
- текущие доменные record-модели `gateway.model.*` остаются контрактом service/repository/persistence-слоев;
- преобразование между generated DTO и доменными моделями выполняется через MapStruct;
- публичный `/v3/api-docs` остается стандартным springdoc diagnostic output и не считается полной заменой рабочих YAML-контрактов;
- callback generated API не подключается к Spring controller beans, потому что `external-gateway-callback.yaml` описывает endpoint сервиса-клиента, который gateway вызывает, а не входящий endpoint самого gateway.

## Архитектурный подход

`ExternalSyncController` реализует `com.example.testqwencli.generated.openapi.sync.api.V1Api`, а `ExternalAsyncController` реализует `com.example.testqwencli.generated.openapi.asyncapi.api.V1Api`.

Ручные `@RequestMapping`, `@PostMapping`, `@GetMapping`, `@DeleteMapping`, `@Operation`, `@ApiResponses`, `@Parameter` и `@Schema` удаляются из production-контроллеров. В контроллерах остается только регистрация Spring bean через `@RestController`, внедрение сервисов и MapStruct-мапперов, а также реализация методов generated-интерфейсов.

MapStruct-мапперы размещаются в отдельном пакете `gateway.controller.mapper`, потому что это boundary mapping между HTTP-контрактом и доменным слоем, а не часть бизнес-логики или persistence. Для enum-значений используется совпадение имен, для `Instant <-> OffsetDateTime` добавляется явный helper с UTC offset.

Generated Spring API interfaces должны содержать Spring MVC/validation annotations. Конфигурация OpenAPI Generator сохраняет `annotationLibrary=swagger2`, потому что generated-код остается contract-facing артефактом и может участвовать в стандартном springdoc scanning. Для generated model-классов задается `modelNameSuffix=DTO`, чтобы Java-классы boundary-слоя не совпадали по именам с доменными record-моделями и мапперы могли импортировать обе стороны без fully qualified names. Для request-моделей включается `containerDefaultToNull`, чтобы required map-поля без JSON-свойства оставались `null` и проходили через `@NotNull`, а не превращались в пустую map до валидации. Non-blank инвариант `clientService` выражается в OpenAPI через `pattern`, чтобы generated DTO сохраняли прежнюю boundary validation без ручных аннотаций в контроллерах.

`GeneratedOpenApiOperationCustomizer` и собственный controller для `/v3/api-docs` не используются. По решению человека от 2026-06-13 стандартный springdoc `/v3/api-docs` может быть неполным и может отличаться от рабочих YAML-контрактов. Причина: текущие потребители являются внутренними сервисами, а поддержка полного runtime merge sync/async YAML добавляет больше сложности, чем пользы на T008.

Canonical OpenAPI для sync/async/callback остается в `test-qwen-cli-app/src/main/resources/openapi` и зеркале `docs/external-service-gateway/openapi`. `external-gateway-callback.yaml` по-прежнему описывает endpoint сервиса-клиента, а не входящий endpoint gateway. Dashboard endpoints не входят в эти YAML без отдельного архитектурного решения; при этом стандартный springdoc `/v3/api-docs` может показывать runtime endpoints шире, чем external-service-gateway YAML.

## Затронутые модули и границы

Затрагивается только Maven-модуль `test-qwen-cli-app`.

Планируемые файлы:

- `test-qwen-cli-app/pom.xml` - добавить MapStruct runtime dependency и annotation processor;
- `test-qwen-cli-app/pom.xml` - включить OpenAPI annotations в generated API interfaces через `annotationLibrary=swagger2`;
- `test-qwen-cli-app/pom.xml` - включить `modelNameSuffix=DTO` для generated model-классов sync, async и callback спецификаций;
- `test-qwen-cli-app/pom.xml` - включить `containerDefaultToNull` для сохранения прежней валидации missing map fields;
- `test-qwen-cli-app/src/main/resources/application.properties` - оставить стандартный springdoc endpoint `/v3/api-docs`;
- `test-qwen-cli-app/src/main/resources/openapi/external-gateway-sync.yaml` и `external-gateway-async.yaml` - добавить `pattern` для non-blank `clientService`;
- `docs/external-service-gateway/openapi/external-gateway-sync.yaml` и `external-gateway-async.yaml` - синхронно обновить документационное зеркало;
- `ExternalSyncController` - реализовать generated sync API interface и использовать mapper;
- `ExternalAsyncController` - реализовать generated async API interface и использовать mapper;
- `ExternalGatewayExceptionHandler` - сохранить прежние русскоязычные validation messages для generated request DTO;
- `gateway.controller.mapper.ExternalSyncOpenApiMapper` - mapping sync generated/domain;
- `gateway.controller.mapper.ExternalAsyncOpenApiMapper` - mapping async generated/domain;
- controller tests и OpenAPI contract test - проверить прежнее поведение, доступность `/v3/api-docs` и отсутствие duplicate mappings.

Не затрагиваются:

- сервисные интерфейсы `ExternalSyncService`, `ExternalAsyncService`;
- repository interfaces и PostgreSQL/memory реализации;
- callback client/runtime flow;
- dashboard API runtime endpoints.

## Публичные контракты

Публичные paths, HTTP methods, status codes, headers и JSON body должны остаться прежними:

- `POST /v1/external/sync`;
- `POST /v1/external/async`;
- `GET /v1/external/async/{taskId}`;
- `DELETE /v1/external/async/{taskId}`;
- `GET /v1/external/async/by-external-id/{externalId}`;
- `POST /v1/external/async/{taskId}/retry`.

Изменение JSON-формы или статусов не входит в scope T008. Если generated annotations выявят фактическое расхождение, оно не исправляется молча в T008: сначала фиксируется причина и выбирается отдельная доработка.

Публичный `/v3/api-docs` остается стандартным springdoc scanning результатом. Он не является canonical contract для CR002 и может быть неполной или более широкой диагностической проекцией runtime endpoints. Человек явно принял риск несовершенного `/v3/api-docs`, чтобы не поддерживать runtime merge YAML и дополнительный custom publisher.

## Data/state/deployment/operations

Data/state не меняются: таблицы, repository contracts, queue lifecycle, callback delivery state и slot state остаются прежними.

Deployment model не меняется: generated sources по-прежнему создаются во время Maven-сборки в `target/generated-sources/openapi`.

Operations не меняются: новых scheduler-ов, внешних соединений, секретов или runtime publisher для YAML этап не добавляет.

## Тестовая стратегия

Минимальная проверка этапа:

- `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalSyncControllerTest,ExternalAsyncControllerTest,ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`;
- при успешной точечной проверке запустить `mvn test`.

Проверки должны подтвердить:

- controller endpoints отвечают прежними статусами и JSON-полями;
- `/v3/api-docs` доступен как стандартный springdoc diagnostic output и содержит основные gateway paths;
- Spring не регистрирует duplicate mappings после подключения generated interfaces.
- validation errors для generated request DTO не меняют клиентские сообщения, включая missing `payload` и blank `clientService`.

## Риски

- Spring MVC может иначе трактовать аннотации на interface methods; это проверяется controller tests.
- Generated DTO используют JavaBean-модели, а доменный слой использует records; MapStruct mapping должен быть явным и покрытым controller tests.
- Java-постфикс `DTO`, dashboard paths или другие runtime детали могут отображаться в стандартном `/v3/api-docs`; этот риск принят человеком, потому что canonical contract остается в YAML.
- `AsyncDeliveryMode.SYNC` остается внутренним доменным значением и не должен попадать во внешние async responses.
- Новая annotation processor dependency может потребовать загрузки из Maven local/remote repository.

## Критерии отката

Откат этапа:

- вернуть контроллеры к прямому использованию доменных DTO и ручных Spring/OpenAPI-аннотаций;
- удалить MapStruct dependency и annotation processor из `test-qwen-cli-app/pom.xml`;
- удалить mapper-классы, добавленные в T008.
- вернуть ручные Spring/OpenAPI annotations в controllers, если generated-interface подход откатывается.

Такой откат не требует миграции данных, изменения OpenAPI YAML или rollback БД, потому что T008 не меняет state и persistence.
