# CR002: журнал выполнения

## Назначение

Файл хранит рабочий статус CR002: какие части OpenAPI уже сверены с контроллерами, какие документы изменены, какие проверки запускались и какие расхождения остались.

## Стартовое состояние

- CR002 заведена 2026-06-12.
- Цель: синхронизировать YAML из `docs/external-service-gateway/openapi` с фактическими контроллерами и DTO.
- Правило источника истины: контроллеры и модели приложения важнее YAML.
- Каталог `docs/external-service-gateway/openapi` содержит:
  - `external-gateway-sync.yaml`;
  - `external-gateway-async.yaml`;
  - `external-gateway-callback.yaml`.
- Предварительно обнаружен риск: `ExternalGatewayOpenApiContractTest` ищет `docs/openapi`, тогда как задача указывает `docs/external-service-gateway/openapi`.
- После синхронизации YAML нужно перенести спецификации в `test-qwen-cli-app/src/main/resources/openapi` и подключить генерацию кода через `org.openapitools:openapi-generator-maven-plugin`.

## Чеклист этапов

- [x] Завести CR002 и описать очередь работ.
- [x] Перед стартом каждого этапа создавать или актуализировать `plan_TXXX.md`.
- [x] Выполнить инвентаризацию контроллеров и DTO.
- [x] Получить human approval по CR002-T001.
- [x] Исправить путь чтения OpenAPI в contract test.
- [x] Синхронизировать `external-gateway-sync.yaml`.
- [x] Синхронизировать `external-gateway-async.yaml`.
- [x] Синхронизировать `external-gateway-callback.yaml`.
- [x] Перенести синхронизированные спецификации в `test-qwen-cli-app/src/main/resources/openapi`.
- [x] Подключить `openapi-generator-maven-plugin` к Maven-сборке `test-qwen-cli-app`.
- [x] Выполнить рефакторинг на использование generated OpenAPI-кода в выбранной роли.
- [ ] Усилить OpenAPI contract checks по стабильным частям контракта.
- [ ] Перед закрытием каждого этапа создавать `review_TXXX.md`.
- [ ] Обрабатывать замечания из `review_TXXX.md` после human approval.
- [ ] Проверить, требуется ли обновление архитектурной документации, и при необходимости внести точечные правки.
- [ ] Запустить `mvn test`.
- [ ] Зафиксировать финальный результат CR002.

## Журнал

1. 2026-06-12: CR002 заведена.
   - Создан `work-items.md` с первичной очередью задач CR002.
   - Создан стартовый `execution-progress.md`.
   - Production-код, тесты и YAML на этом шаге не изменялись.

2. 2026-06-12: в CR002 добавлен refactoring scope после синхронизации YAML.
   - Очередь расширена задачами переноса спецификаций в `test-qwen-cli-app/src/main/resources/openapi`.
   - Добавлена задача подключения `org.openapitools:openapi-generator-maven-plugin` к сборке.
   - Добавлена задача рефакторинга на использование generated OpenAPI-кода.
   - Production-код, тесты, `pom.xml` и YAML на этом шаге не изменялись.

3. 2026-06-12: в CR002 добавлена условная проверка архитектурной документации.
   - Добавлена задача CR002-T010: проверить, требуется ли обновление `docs/external-service-gateway/architecture` после выбора роли generated OpenAPI-кода.
   - Зафиксировано правило: если CR002 не меняет границы компонентов, runtime flow, persistence state или deployment model, архитектурные документы не переписываются.
   - Production-код, тесты, `pom.xml`, YAML и архитектурные документы на этом шаге не изменялись.

4. 2026-06-12: архитектурные решения перенесены в комплект архитектурной документации.
   - `decisions.md` перенесен из `docs/external-service-gateway/chrequests/PRE-WORK` в `docs/external-service-gateway/architecture`.
   - Добавлен ADR-012 с принятым решением по CR002: OpenAPI синхронизируется с контроллерами, а генерация подключается к Maven-сборке без изменения публичного поведения API.
   - Обновлена карта архитектурных документов в `docs/external-service-gateway/architecture/README.md`.

5. 2026-06-12: в CR002 добавлена обязательная stage-level валидация senior architect agent.
   - Зафиксировано правило: после реализации каждого этапа `CR002-TXXX` создается `review_TXXX.md` с проверкой соответствия реализации плану этапа, рисков производительности, безопасности и архитектурных приемов.
   - Зафиксировано правило: замечания из `review_TXXX.md` не расширяют scope CR002 автоматически; они принимаются в доработки, отклоняются или откладываются явным решением человека.
   - Для review добавлены локальные Codex-артефакты: `.codex/skills/cr-architecture-review/SKILL.md` и `.codex/agents/senior-architect-reviewer.md`.
   - Production-код, тесты, `pom.xml`, YAML и архитектурные документы на этом шаге не изменялись.

6. 2026-06-12: в CR002 добавлено требование stage-level `plan_TXXX.md` перед реализацией.
   - Зафиксировано правило: когда очередной этап `CR002-TXXX` берется в работу, до реализации создается или актуализируется `plan_TXXX.md`.
   - Общий `plan.md` для всего CR не используется.
   - Production-код, тесты, `pom.xml`, YAML и архитектурные документы на этом шаге не изменялись.

7. 2026-06-12: выполнена инвентаризация фактического HTTP API для CR002-T001.
   - Создан `plan_T001.md` до реализации этапа.
   - Создан `inventory_T001.md` со списком gateway endpoints, callback-контракта, dashboard/root endpoints вне CR002 YAML, error contract, DTO-полей, enum-значений и спорных мест для следующих этапов.
   - Зафиксированы источники истины: контроллеры и DTO для sync/async API, `CallbackPayload` и `HttpCallbackClient` для callback-контракта.
   - Dashboard API и root endpoint явно исключены из `external-gateway-*.yaml` в рамках CR002.
   - Production-код, тесты, `pom.xml`, YAML и архитектурные документы на этом шаге не изменялись.
   - `mvn test` не запускался, потому что этап является документационной инвентаризацией без изменения runtime-кода.
   - До закрытия этапа требуется `review_T001.md`.

8. 2026-06-12: выполнен senior architect review для CR002-T001.
   - Создан `review_T001.md`.
   - Итог review: блокирующих архитектурных замечаний нет, этап соответствует `plan_T001.md` и ADR-012.
   - Зафиксировано одно note-level замечание: path variables указаны через URL-шаблоны, но не вынесены отдельной таблицей типов и ограничений.
   - Статус замечания: `pending`, требуется human approval для принятия, отклонения или переноса в T004/T009.
   - Production-код, тесты, `pom.xml`, YAML и архитектурные документы на review не изменялись.

9. 2026-06-12: получен human approval по CR002-T001.
   - Решение человека: задача 1 принята, этап CR002-T001 закрыт без дополнительной документационной доработки в T001.
   - Note-level замечание review по явному описанию path variables не блокирует закрытие T001; детализация типов и ограничений path variables будет учтена при синхронизации async YAML в T004 и при усилении contract checks в T009.
   - Production-код, тесты, `pom.xml`, YAML и архитектурные документы на этом шаге не изменялись.

10. 2026-06-12: стартован CR002-T002.
   - Создан `plan_T002.md` до реализации этапа.
   - Выбран подход: точечно исправить резолвер каталога в `ExternalGatewayOpenApiContractTest`, убрать fallback на неподдерживаемый `docs/openapi` и читать `docs/external-service-gateway/openapi` при запуске из корня репозитория или из модуля `test-qwen-cli-app`.

11. 2026-06-12: реализован CR002-T002.
   - Обновлен `ExternalGatewayOpenApiContractTest.openApiDirectory()`: тест ищет `docs/external-service-gateway/openapi` из корня репозитория и из модуля `test-qwen-cli-app`.
   - Fallback на `docs/openapi` удален, потому что этот каталог не поддерживается в CR002 согласно ADR-012.
   - Production-код, `pom.xml`, OpenAPI YAML и архитектурные документы не изменялись.
   - Проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 2 теста без failures/errors/skipped.

12. 2026-06-12: выполнен senior architect review для CR002-T002.
   - Создан `review_T002.md`.
   - Итог review: замечаний нет, этап соответствует `plan_T002.md` и ADR-012.
   - Рекомендация review: закрыть CR002-T002 без дополнительных изменений и перейти к CR002-T003.

13. 2026-06-12: стартован CR002-T003.
   - Создан `plan_T003.md` до реализации этапа.
   - Выбран подход: синхронизировать только `docs/external-service-gateway/openapi/external-gateway-sync.yaml` с `ExternalSyncController`, sync DTO и `ExternalGatewayExceptionHandler`; production-код не менять.

14. 2026-06-12: реализован CR002-T003.
   - Обновлен `docs/external-service-gateway/openapi/external-gateway-sync.yaml`.
   - У `ExternalSyncRequest`, `ExternalSyncResponse` и `ErrorResponse` удален `additionalProperties: false`, потому что такое ограничение не задано контроллером, DTO или handler.
   - Для `ExternalSyncRequest.payload` явно указано `additionalProperties: true`, что соответствует `Map<String, Object>`.
   - Для `ExternalSyncResponse.upstreamStatus` указан `format: int32`, а недоказанные ограничения `minimum: 100` и `maximum: 599` удалены; DTO хранит поле как Java `int`.
   - Путь, HTTP-метод, `operationId`, headers, request body, response statuses и error response refs оставлены без изменений, потому что уже соответствовали `ExternalSyncController`.
   - Production-код, тесты, `pom.xml`, async/callback YAML и архитектурные документы не изменялись.
   - Проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 2 теста без failures/errors/skipped.

15. 2026-06-12: выполнен senior architect review для CR002-T003.
   - Создан `review_T003.md`.
   - Итог review: замечаний нет, этап соответствует `plan_T003.md` и ADR-012.
   - Рекомендация review: закрыть CR002-T003 без дополнительных изменений и перейти к CR002-T004.

16. 2026-06-12: стартован CR002-T004.
   - Создан `plan_T004.md` до реализации этапа.
   - Выбран подход: синхронизировать только `docs/external-service-gateway/openapi/external-gateway-async.yaml` с `ExternalAsyncController`, async DTO, enum-классами и `ExternalGatewayExceptionHandler`; production-код не менять.

17. 2026-06-12: реализован CR002-T004.
   - Обновлен `docs/external-service-gateway/openapi/external-gateway-async.yaml`.
   - У `ExternalAsyncRequest`, `AsyncSubmitResponse`, `AsyncTask`, `TaskError` и `ErrorResponse` удален `additionalProperties: false`, потому что такое ограничение не задано контроллером, DTO или handler.
   - Для `ExternalAsyncRequest.payload` явно указано `additionalProperties: true`, что соответствует `Map<String, Object>`.
   - Для async result schema `ResultMap` значение изменено с `Map<String, String>` на `Map<String, Object>`, что соответствует `AsyncTask.result`.
   - Для submit API добавлена отдельная request-схема `ExternalAsyncDeliveryMode` со значениями `CALLBACK` и `POLLING`.
   - `AsyncTask.callbackDeliveryStatus` сделан non-null ref на `CallbackDeliveryStatus`, потому что DTO constructor требует значение.
   - Добавлены минимальные ограничения, которые прямо следуют из constructors: `statusUrl`, `AsyncTask.clientService`, `TaskError.code` и `TaskError.message` не blank.
   - Путь, HTTP-методы, `operationId`, headers, path variables, request body, response statuses и error response refs оставлены без изменений, потому что уже соответствовали `ExternalAsyncController`.
   - Production-код, тесты, `pom.xml`, sync/callback YAML и архитектурные документы не изменялись.
   - Проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 2 теста без failures/errors/skipped.
   - До закрытия этапа требуется `review_T004.md`.

18. 2026-06-12: выполнен senior architect review для CR002-T004.
   - Создан `review_T004.md`.
   - Итог review: блокирующих замечаний нет, этап соответствует `plan_T004.md` и ADR-012.
   - Зафиксированы два note-level замечания со статусом `pending`.
   - Замечание 1: ADR-008 говорит о `Map<String, String>` для async fallback GET result, а фактическая read-модель и YAML T004 фиксируют `Map<String, Object>`.
   - Замечание 2: response-схема `AsyncDeliveryMode` допускает `SYNC` ради соответствия Java enum/read-модели, но внешние async endpoints фактически наблюдаемы как `CALLBACK`/`POLLING`.
   - Рекомендация review: закрыть CR002-T004 после human approval без production-правок, а note-level замечания перенести в решение T009/T010 или отдельный follow-up.
   - По правилу остановки после этапа переход к CR002-T005 не выполняется до явной команды пользователя.

19. 2026-06-12: по human approval отработано замечание 2 из `review_T004.md`.
   - Решение человека: ввести отдельную response-схему для внешних async endpoints с `CALLBACK`/`POLLING`.
   - Обновлен `plan_T004.md`: зафиксировано, что `SYNC` не публикуется как значение внешних async response.
   - Обновлен `docs/external-service-gateway/openapi/external-gateway-async.yaml`.
   - Добавлена `ExternalAsyncResponseDeliveryMode` со значениями `CALLBACK` и `POLLING`.
   - `AsyncSubmitResponse.deliveryMode` и `AsyncTask.deliveryMode` теперь ссылаются на `ExternalAsyncResponseDeliveryMode`.
   - `SYNC` больше не публикуется как enum value внешнего async OpenAPI; он остается только текстово описанной внутренней Java-деталью sync trace-строк.
   - Обновлен `review_T004.md`: замечание 2 переведено в статус `accepted`.
   - Production-код, тесты, `pom.xml`, sync/callback YAML и архитектурные документы не изменялись.
   - Проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 2 теста без failures/errors/skipped.
   - Замечание 1 из `review_T004.md` по ADR-008 остается в статусе `pending`.

20. 2026-06-12: стартован CR002-T005.
   - Создан `plan_T005.md` до реализации этапа.
   - Выбран подход: синхронизировать только `docs/external-service-gateway/openapi/external-gateway-callback.yaml` с `CallbackPayload`, `HttpCallbackClient` и callback delivery retry/backoff flow; production-код не менять.
   - Явная команда пользователя разрешила начать T005; замечание 1 из `review_T004.md` по ADR-008 остается в статусе `pending` и не закрывается автоматически в рамках T005.

21. 2026-06-12: реализован CR002-T005.
   - Обновлен `docs/external-service-gateway/openapi/external-gateway-callback.yaml`.
   - Response contract callback endpoint заменен на `2XX`/`default`: gateway считает успешным любой HTTP 2xx, игнорирует body и headers ответа, а любой non-2xx/transport/runtime error обрабатывает через внутренние `retryBackoffMs` и `maxAttempts`.
   - Удалены callback response schemas `CallbackAck` и `ErrorResponse`, потому что `HttpCallbackClient` не читает response body сервиса-клиента.
   - Удалено обещание `Retry-After`, потому что callback dispatcher его не использует.
   - `finishedAt` изменен со `string/date-time` на `number/double` epoch seconds, что соответствует фактической JSON-сериализации `Instant` в `HttpCallbackClientTest`.
   - `ResultMap` теперь допускает string/null значения, потому что `CallbackPayload.fromTask` преобразует значения через `Objects.toString(value, null)`.
   - Для `clientService`, `CallbackError.code` и `CallbackError.message` добавлены `minLength: 1`, что соответствует constructor checks.
   - Production-код, тесты, `pom.xml`, sync/async YAML и архитектурные документы не изменялись.
   - Проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest,HttpCallbackClientTest,CallbackDeliveryFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 17 тестов без failures/errors/skipped.
   - Проверка: `mvn test` успешно выполнила 115 тестов без failures/errors/skipped.
   - До закрытия этапа требуется `review_T005.md`.

22. 2026-06-12: выполнен senior architect review для CR002-T005.
   - Создан `review_T005.md`.
   - Итог review: блокирующих замечаний нет, этап соответствует `work-items.md`, `plan_T005.md` и ADR-012.
   - Зафиксировано одно note-level замечание со статусом `pending`: текущий callback OpenAPI contract test проверяет наличие полей, но не проверяет тип `finishedAt`, nested constraints и response keys `2XX`/`default`.
   - Рекомендация review: закрыть CR002-T005 после human approval без production-правок, а note-level замечание перенести в CR002-T009 или явно отложить отдельным решением человека.
   - По правилу остановки после этапа переход к CR002-T006 не выполняется до явной команды пользователя.

23. 2026-06-12: стартован CR002-T006.
   - Получена явная команда пользователя начать CR002-T006.
   - Создан `plan_T006.md` до реализации этапа.
   - Выбран подход: `test-qwen-cli-app/src/main/resources/openapi` становится рабочим входом Maven-сборки и будущего генератора, а `docs/external-service-gateway/openapi` остается документационным зеркалом с тестовой проверкой синхронности.
   - Note-level замечание из `review_T005.md` по усилению callback contract checks остается в статусе `pending` и не входит в scope T006.

24. 2026-06-12: реализован CR002-T006.
   - Создан каталог `test-qwen-cli-app/src/main/resources/openapi`.
   - В resources перенесены синхронизированные спецификации `external-gateway-sync.yaml`, `external-gateway-async.yaml`, `external-gateway-callback.yaml`.
   - Обновлен `ExternalGatewayOpenApiContractTest`: primary-каталог OpenAPI теперь `test-qwen-cli-app/src/main/resources/openapi`, fallback на `docs/openapi` не добавлялся.
   - В `ExternalGatewayOpenApiContractTest` добавлена проверка, что YAML из `docs/external-service-gateway/openapi` byte-for-byte совпадают с рабочими YAML из resources.
   - Обновлен `docs/external-service-gateway/architecture/README.md`: явно указаны рабочий OpenAPI-вход сборки и документационное зеркало.
   - Production-код, `pom.xml`, Maven lifecycle и публичное HTTP-поведение не изменялись.
   - Проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 3 теста без failures/errors/skipped.
   - Проверка: `mvn test` успешно выполнила 116 тестов без failures/errors/skipped.
   - До закрытия этапа требуется `review_T006.md`.

25. 2026-06-12: выполнен senior architect review для CR002-T006.
   - Создан `review_T006.md`.
   - Итог review: блокирующих и note-level замечаний нет, этап соответствует `work-items.md`, `plan_T006.md` и ADR-012.
   - Независимый senior architect review-agent подтвердил полноту `review_T006.md`; дополнительные правки не требуются.
   - Review подтвердил выбранный подход: `resources` является рабочим входом сборки и будущей генерации, `docs` остается документационным зеркалом под byte-for-byte проверкой.
   - Рекомендация review: закрыть CR002-T006 после human approval без дополнительных production-правок.
   - По правилу остановки после этапа переход к CR002-T007 не выполняется до явной команды пользователя.

26. 2026-06-12: получен human approval по CR002-T006 и стартован CR002-T007.
   - Явная команда пользователя продолжить CR002 трактуется как approval закрыть CR002-T006 и разрешение перейти к CR002-T007.
   - Создан `plan_T007.md` до реализации этапа.
   - Выбран подход: подключить `openapi-generator-maven-plugin` только как build-time генерацию компилируемых Spring API interfaces и model-классов из `test-qwen-cli-app/src/main/resources/openapi`, без refactoring production-контроллеров до CR002-T008.
   - Note-level замечания из `review_T004.md` и `review_T005.md` остаются в статусе `pending` и не входят в scope T007.

27. 2026-06-12: реализован CR002-T007.
   - Обновлен `test-qwen-cli-app/pom.xml`.
   - Добавлено свойство `openapi-generator.version=7.22.0`.
   - Подключен `org.openapitools:openapi-generator-maven-plugin` с тремя execution:
     - `generate-openapi-sync-contract` для `external-gateway-sync.yaml`;
     - `generate-openapi-async-contract` для `external-gateway-async.yaml`;
     - `generate-openapi-callback-contract` для `external-gateway-callback.yaml`.
   - Для sync, async и callback заданы отдельные generated packages под `com.example.testqwencli.generated.openapi.*`, чтобы одноименные схемы разных спецификаций не конфликтовали.
   - Generated sources направлены в `test-qwen-cli-app/target/generated-sources/openapi/<spec-name>` и не коммитятся.
   - Настроен `spring` generator в режиме `interfaceOnly`/`skipDefaultInterface` с `useSpringBoot3`, поэтому generated-код компилируется как API interfaces и модели, но не регистрирует новые controller beans и не меняет runtime mappings.
   - Production-контроллеры, DTO, сервисы, repositories, callback client, OpenAPI YAML и архитектурные документы не изменялись.
   - Проверка: `mvn -pl test-qwen-cli-app -am test` успешно выполнила 116 тестов без failures/errors/skipped.
   - Финальная проверка этапа: `mvn test` успешно выполнила 116 тестов без failures/errors/skipped.
   - Во время генерации остались ожидаемые non-blocking предупреждения generator: OpenAPI 3.1 support отмечен как beta, free-form `ResultMap` не генерируется как отдельная модель, complex example в request body игнорируется. Компиляцию и тесты это не ломает.
   - До закрытия этапа требуется `review_T007.md`.

28. 2026-06-12: выполнен senior architect review для CR002-T007.
   - Создан `review_T007.md`.
   - Итог review: блокирующих замечаний нет, этап соответствует `work-items.md`, `plan_T007.md`, ADR-012 и карте архитектурной документации.
   - Review подтвердил, что T007 ограничен build-time генерацией и не меняет runtime mappings, публичный HTTP API, persistence, callback flow или security scope.
   - Зафиксированы два note-level замечания со статусом `pending`.
   - Замечание 1: предупреждения OpenAPI Generator по OpenAPI 3.1 beta, free-form `ResultMap` и ignored complex example могут стать значимыми при выборе роли generated models в CR002-T008 и checks в CR002-T009.
   - Замечание 2: при подключении generated interfaces в CR002-T008 нужно явно избежать duplicate mappings и подтвердить это controller/OpenAPI contract tests.
   - Рекомендация review: закрыть CR002-T007 после human approval без дополнительных правок, а note-level замечания учесть в CR002-T008/CR002-T009 или явно отложить.
   - По правилу остановки после этапа переход к CR002-T008 не выполняется до явной команды пользователя.

29. 2026-06-12: получен human approval по CR002-T007.
   - Решение человека: закрыть CR002-T007 без дополнительных правок в коде или документации.
   - Оба note-level замечания из `review_T007.md` не блокируют закрытие этапа.
   - Замечание 1 по ограничениям OpenAPI Generator переведено в статус `deferred` и должно быть учтено при выборе роли generated-кода в CR002-T008 и усилении contract checks в CR002-T009.
   - Замечание 2 по риску duplicate mappings переведено в статус `deferred` и должно быть учтено при CR002-T008.
   - CR002-T007 закрыт.
   - По правилу остановки после этапа переход к CR002-T008 не выполняется до явной команды пользователя.

30. 2026-06-13: стартован CR002-T008.
   - Получена явная команда пользователя продолжить CR002-T008.
   - Создан и актуализирован `plan_T008.md` до реализации этапа.
   - Выбран подход: sync/async production-контроллеры реализуют generated API interfaces, generated DTO используются только на HTTP-boundary, а доменные модели `gateway.model.*` остаются контрактом service/repository/persistence-слоев.
   - Для mapping generated DTO <-> domain выбран MapStruct.
   - Callback generated API не подключается как Spring controller bean, потому что callback OpenAPI описывает endpoint сервиса-клиента, вызываемый gateway.

31. 2026-06-13: реализован CR002-T008.
   - Обновлен `test-qwen-cli-app/pom.xml`: добавлен MapStruct runtime dependency и annotation processor, а для generated OpenAPI-кода включен `annotationLibrary=swagger2`, чтобы OpenAPI-аннотации находились в generated interfaces.
   - `ExternalSyncController` теперь реализует `com.example.testqwencli.generated.openapi.sync.api.V1Api`, принимает generated `ExternalSyncRequest`, возвращает generated `ExternalSyncResponse` и больше не содержит ручных Spring MVC/OpenAPI mapping-аннотаций endpoint.
   - `ExternalAsyncController` теперь реализует `com.example.testqwencli.generated.openapi.asyncapi.api.V1Api`, принимает/возвращает generated async DTO и больше не содержит ручных Spring MVC/OpenAPI mapping-аннотаций endpoint.
   - Добавлены MapStruct-мапперы `ExternalSyncOpenApiMapper` и `ExternalAsyncOpenApiMapper` для преобразования generated DTO на границе контроллера в доменные модели и обратно.
   - Для async response mapping явно запрещена публикация внутреннего `AsyncDeliveryMode.SYNC` во внешнем async API.
   - Добавлен `GeneratedOpenApiOperationCustomizer`: springdoc output получает `operationId`, summary, description, tags и responses из `@Operation`/`@ApiResponse` generated API interfaces, а не из ручных аннотаций controllers.
   - Обновлен `ExternalGatewayExceptionHandler`, чтобы validation errors от generated request DTO сохраняли прежние русскоязычные клиентские сообщения.
   - Публичные paths, HTTP methods, status codes, response body и persistence/runtime state не менялись.
   - Первичная проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalSyncControllerTest,ExternalAsyncControllerTest,ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 22 теста без failures/errors/skipped.
   - Первичная финальная проверка этапа: `mvn test` успешно выполнила 116 тестов без failures/errors/skipped.
   - До закрытия этапа требуется `review_T008.md`.

32. 2026-06-13: по результатам `review_T008.md` устранены validation gaps в boundary generated DTO.
   - Senior architect review для CR002-T008 выявил риск изменения публичного validation behavior: missing `payload` мог стать пустой map из-за default generated DTO, а whitespace-only `clientService` мог пройти generated `@Size`.
   - Обновлен `test-qwen-cli-app/pom.xml`: для OpenAPI Generator добавлен `containerDefaultToNull=true`, чтобы required map-поля без JSON-свойства оставались `null` до Bean Validation.
   - Обновлены рабочие OpenAPI YAML в `test-qwen-cli-app/src/main/resources/openapi` и документационное зеркало в `docs/external-service-gateway/openapi`: для request field `clientService` добавлен `pattern: '.*\S.*'`.
   - Обновлен `ExternalGatewayExceptionHandler`: generated `Pattern` violation и blank `Size` violation для `clientService` возвращают прежнее русскоязычное сообщение `clientService обязателен`.
   - Добавлены быстрые MockMvc checks в `ExternalSyncControllerTest` и `ExternalAsyncControllerTest`: missing `payload`, empty `clientService` и whitespace-only `clientService` дают `400 VALIDATION_ERROR` и не создают sync trace / async task.
   - Проверка generated sources: sync/async generated `External*Request.payload` больше не инициализируется через `new HashMap<>()`, а `clientService` содержит `@Pattern(regexp = ".*\\S.*")`.
   - Проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalSyncControllerTest,ExternalAsyncControllerTest,ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 26 тестов без failures/errors/skipped.
   - Финальная проверка этапа после устранения замечаний: `mvn test` успешно выполнила 120 тестов без failures/errors/skipped.

33. 2026-06-13: выполнена повторная senior architect проверка CR002-T008 после validation fix.
   - Обновлен `review_T008.md`.
   - Итог review: активных `critical`, `high` или `medium` замечаний после post-review validation fix нет.
   - Review подтвердил, что missing `payload` снова отклоняется как `400 VALIDATION_ERROR`, а empty/whitespace-only `clientService` отклоняется на HTTP-boundary до вызова service/repository слоя.
   - Review подтвердил, что production-код в ходе повторной проверки не менялся.
   - Рекомендация review: закрыть CR002-T008 после human approval без дополнительных production-правок.
   - По правилу остановки после этапа переход к CR002-T009 не выполняется до явной команды пользователя.

34. 2026-06-13: по уточнению пользователя generated model-классы T008 переведены на Java-постфикс `DTO`.
   - Обновлен `test-qwen-cli-app/pom.xml`: для sync, async и callback OpenAPI Generator executions добавлен `modelNameSuffix=DTO`.
   - `ExternalSyncController` и `ExternalAsyncController` обновлены на generated классы `ExternalSyncRequestDTO`, `ExternalSyncResponseDTO`, `ExternalAsyncRequestDTO`, `AsyncSubmitResponseDTO`, `AsyncTaskDTO`.
   - `ExternalSyncOpenApiMapper` и `ExternalAsyncOpenApiMapper` обновлены так, чтобы маппить generated `*DTO` на доменные record-модели и обратно без fully qualified names в сигнатурах мапперов.
   - `GeneratedOpenApiOperationCustomizer` расширен: response schemas из generated DTO добавляются в OpenAPI components, а Java-постфикс `DTO` убирается из публичных schema names и `$ref`, чтобы `/v3/api-docs` продолжал публиковать прежние contract names без DTO.
   - Обновлен `plan_T008.md`: зафиксирована роль `modelNameSuffix=DTO` и правило сохранения прежних OpenAPI schema names.
   - Чистая targeted-проверка: `mvn -pl test-qwen-cli-app -am clean test "-Dtest=ExternalSyncControllerTest,ExternalAsyncControllerTest,ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false"` успешно выполнила 26 тестов без failures/errors/skipped и подтвердила генерацию только `*DTO` model-классов после `clean`.
   - Финальная проверка после DTO suffix: `mvn test` успешно выполнила 120 тестов без failures/errors/skipped.
   - Из-за production-изменения после предыдущего review требуется обновить `review_T008.md` перед human approval.

35. 2026-06-13: выполнена повторная senior architect проверка CR002-T008 после DTO suffix.
   - Обновлен `review_T008.md`.
   - Итог review: `passed`, активных блокирующих архитектурных замечаний после `modelNameSuffix=DTO` нет.
   - Review подтвердил, что generated `*DTO` остаются только на HTTP/controller boundary, доменные `gateway.model.*` модели остаются контрактом service/repository/persistence-слоев, а MapStruct явно связывает эти слои.
   - Review подтвердил, что `GeneratedOpenApiOperationCustomizer` сохраняет публичные OpenAPI schema names без `DTO`, поэтому Java naming convention не утекает во внешний контракт.
   - Зафиксирован note-level остаточный риск: при CR002-T009 желательно добавить negative check на отсутствие `DTO` в public schema refs после будущих обновлений springdoc/OpenAPI Generator.
   - Production-код, тесты, `pom.xml` и другие документы в ходе review не менялись.
   - Рекомендация review: закрыть CR002-T008 после human approval без дополнительных production-правок.
   - По правилу остановки после этапа переход к CR002-T009 не выполняется до явной команды пользователя.

36. 2026-06-13: по решению пользователя T008 переведен с springdoc customizer на contract-first публикацию `/v3/api-docs`.
   - Выбран второй вариант: публичный `/v3/api-docs` строится из рабочих OpenAPI YAML в `test-qwen-cli-app/src/main/resources/openapi`, а не из springdoc scanning и не через `GeneratedOpenApiOperationCustomizer`.
   - Удален `GeneratedOpenApiOperationCustomizer`.
   - Добавлен `ExternalGatewayOpenApiController`: он читает `external-gateway-sync.yaml` и `external-gateway-async.yaml`, объединяет `paths`, `components`, `tags` и `servers`, нормализует split-document schema names `ResultMap` в `ExternalSyncResultMap`/`ExternalAsyncResultMap` и fail-fast падает на конфликтующих components.
   - Callback OpenAPI не включается в `/v3/api-docs`, потому что описывает endpoint внешнего сервиса-клиента; dashboard endpoints также не включаются в external-service-gateway contract.
   - `springdoc.api-docs.path` перенесен на `/internal/springdoc-api-docs`, а Swagger UI направлен на contract-first `/v3/api-docs`.
   - В `test-qwen-cli-app/pom.xml` добавлена runtime dependency `jackson-dataformat-yaml`, нужная production publisher для чтения YAML из classpath resources.
   - `ExternalGatewayOpenApiContractTest` обновлен: `/v3/api-docs` сверяется с contract-first sync/async YAML, проверяется отсутствие публичных schema names с Java-постфиксом `DTO` и отсутствие dashboard path.
   - Generated interfaces и generated `*DTO` остаются runtime source для Spring MVC/validation на controller boundary; доменные модели по-прежнему связываются через MapStruct.
   - Targeted-проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest,TestQwenCliApplicationTests,ExternalSyncControllerTest,ExternalAsyncControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 36 тестов без failures/errors/skipped.
   - Финальная проверка после contract-first switch: `mvn test` успешно выполнила 120 тестов без failures/errors/skipped.
   - Из-за production-изменения после предыдущего review требуется обновить `review_T008.md` перед human approval.

37. 2026-06-13: выполнена повторная senior architect проверка CR002-T008 после contract-first switch.
   - Обновлен `review_T008.md`.
   - Итог review: `passed`, активных `critical`, `high` или `medium` замечаний нет.
   - Review подтвердил соответствие `plan_T008.md`, ADR-009 и ADR-012: generated interfaces/DTO остаются controller boundary, доменный слой отделен через MapStruct, а публичный `/v3/api-docs` публикуется из contract-first sync/async YAML.
   - Review подтвердил, что callback YAML и dashboard endpoints обоснованно не входят в external-service-gateway `/v3/api-docs`.
   - Review подтвердил, что `ExternalGatewayOpenApiController` строит документ один раз на startup, fail-fast падает при конфликтующих components и не добавляет overhead в sync/async business path.
   - Зафиксировано low-level замечание: `/internal/springdoc-api-docs` является перенесенным springdoc scanning endpoint, но не access-control механизмом; решение принять, отклонить или перенести в follow-up/T010 требует human approval.
   - Зафиксировано note-level замечание: в CR002-T009 стоит усилить negative checks на callback path, полный набор dashboard paths и `DTO` в public `$ref`.
   - Production-код, `pom.xml`, тесты и другие документы в ходе review не менялись.
   - Рекомендация review: закрыть CR002-T008 после human approval без дополнительных production-правок.
   - По правилу остановки после этапа переход к CR002-T009 не выполняется до явной команды пользователя.

38. 2026-06-13: зафиксировано human decision по замечанию 1 из `review_T008.md`.
   - Замечание 1 по `/internal/springdoc-api-docs` отклонено человеком: ничего не делаем в рамках T008.
   - `review_T008.md` обновлен: статус замечания 1 переведен в `rejected`.
   - Замечание 2 по усилению negative checks остается в статусе `pending`: требуется пояснение и отдельное решение человека.
   - Production-код, `pom.xml` и тесты не менялись.

39. 2026-06-13: по решению пользователя T008 возвращен на стандартную springdoc публикацию `/v3/api-docs`.
   - Человек принял риск несовершенного `/v3/api-docs`, потому что текущие внешние потребители gateway являются внутренними сервисами.
   - Удален `ExternalGatewayOpenApiController`; runtime merge sync/async YAML и переопределение `/v3/api-docs` больше не используются.
   - Удалена dependency `jackson-dataformat-yaml`, так как production-код больше не читает OpenAPI YAML.
   - `springdoc.api-docs.path` возвращен на `/v3/api-docs`; отдельный `/internal/springdoc-api-docs` больше не используется.
   - `ExternalGatewayOpenApiContractTest` ослаблен: он проверяет доступность стандартного springdoc `/v3/api-docs` и наличие основных gateway paths, но больше не требует полного совпадения `/v3/api-docs` с рабочими YAML.
   - Строгими проверками остаются синхронность OpenAPI YAML в resources/docs mirror и соответствие callback YAML сериализуемому `CallbackPayload`.
   - Generated interfaces, generated `*DTO`, MapStruct-мапперы и validation fixes остаются без изменений.
   - Targeted-проверка: `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest,TestQwenCliApplicationTests,ExternalSyncControllerTest,ExternalAsyncControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнила 36 тестов без failures/errors/skipped.
   - Финальная проверка после возврата к стандартному springdoc: `mvn test` успешно выполнила 120 тестов без failures/errors/skipped.
   - Из-за production-изменения после предыдущего review требуется обновить `review_T008.md` перед human approval.

40. 2026-06-13: выполнена повторная senior architect проверка CR002-T008 после возврата к стандартному springdoc `/v3/api-docs`.
   - Обновлен `review_T008.md`.
   - Итог review: `passed`, активных `critical`, `high`, `medium` или `pending` замечаний нет.
   - Review подтвердил, что generated interfaces/DTO остаются только на controller boundary, доменные модели отделены через MapStruct, а canonical OpenAPI contract остается в YAML resources/docs mirror.
   - Review подтвердил, что `ExternalGatewayOpenApiController`, runtime YAML merge, `GeneratedOpenApiOperationCustomizer`, `jackson-dataformat-yaml`, `/internal/springdoc-api-docs` и `springdoc.swagger-ui.url` отсутствуют в актуальном коде.
   - Прежние замечания про YAML merge/custom publisher сняты как неактуальные, потому что соответствующий production-код удален.
   - Единственное note-level замечание имеет статус `accepted`: риск несовершенного стандартного `/v3/api-docs` принят человеком без production-доработок в T008.
   - Production-код, `pom.xml`, тесты и другие документы в ходе review не менялись.
   - Рекомендация review: закрыть CR002-T008 после human approval без дополнительных production-правок.
   - По правилу остановки после этапа переход к CR002-T009 не выполняется до явной команды пользователя.

## Текущий результат

- CR002-T001 реализована как документационная инвентаризация, прошла senior architect review и принята человеком.
- Note-level замечание CR002-T001 по path variables перенесено в контекст T004/T009.
- Принятое архитектурное решение по CR002 зафиксировано в `docs/external-service-gateway/architecture/decisions.md`.
- Для этапов CR002 требуется stage-level `plan_TXXX.md` перед стартом и `review_TXXX.md` перед закрытием.
- CR002-T002 реализована и прошла senior architect review без замечаний: OpenAPI contract test читает актуальный каталог `docs/external-service-gateway/openapi`.
- CR002-T003 реализована и прошла senior architect review без замечаний: `external-gateway-sync.yaml` синхронизирован с фактическими sync DTO по ограничениям схем.
- CR002-T004 реализована и прошла senior architect review без блокеров: `external-gateway-async.yaml` синхронизирован с фактическими async DTO по ограничениям схем и enum-значениям.
- В `review_T004.md` замечание 2 по `AsyncDeliveryMode.SYNC` принято человеком и отработано; замечание 1 по ADR-008 остается в статусе `pending`.
- CR002-T005 реализована и прошла senior architect review без блокеров: `external-gateway-callback.yaml` синхронизирован с `CallbackPayload`, `HttpCallbackClient` и callback delivery retry/backoff flow.
- В `review_T005.md` есть note-level замечание по усилению callback contract checks; статус `pending`, требуется human approval.
- CR002-T006 реализована, прошла senior architect review без замечаний и получила human approval: OpenAPI YAML перенесены в `test-qwen-cli-app/src/main/resources/openapi`, contract test читает resources как primary и проверяет byte-for-byte синхронность с `docs/external-service-gateway/openapi`.
- CR002-T007 реализована, прошла senior architect review без блокеров и получила human approval: `openapi-generator-maven-plugin` подключен к Maven lifecycle `test-qwen-cli-app`, generated sources из трех OpenAPI YAML создаются в `target/generated-sources/openapi` и компилируются.
- Два note-level замечания из `review_T007.md` не блокируют T007 и переведены в статус `deferred`: ограничения генератора должны быть учтены в T008/T009, риск duplicate mappings должен быть учтен в T008.
- CR002-T008 реализована: sync/async контроллеры используют generated API interfaces, generated model-классы имеют Java-постфикс `DTO`, generated DTO маппятся в доменные модели через MapStruct, а публичный `/v3/api-docs` оставлен стандартным springdoc diagnostic output с принятым риском неполного соответствия YAML.
- Замечания `review_T008.md` по missing `payload` и blank `clientService` технически устранены до закрытия этапа; повторная senior architect проверка подтвердила отсутствие активных блокеров.
- После уточнения пользователя про `DTO` suffix выполнена дополнительная production-правка T008; после обсуждения `GeneratedOpenApiOperationCustomizer` не используется, contract-first publisher также удален по решению пользователя.
- Повторный senior architect review после возврата к стандартному springdoc имеет статус `passed`; активных pending-замечаний нет.
- Stage-level senior architect review создан для T001, T002, T003, T004, T005, T006, T007 и T008.
- Для CR002-T008 создан и обновлен `review_T008.md`; после возврата к стандартному springdoc review имеет статус `passed`, требуется human approval на закрытие этапа.
- Финальная проверка необходимости дополнительных архитектурных правок после реализации CR002 только запланирована.
- После T002, T003 и T004 запускался точечный `ExternalGatewayOpenApiContractTest`; после T005 запускались точечные callback/OpenAPI tests и полный `mvn test`; после T006 запускались точечный `ExternalGatewayOpenApiContractTest` и полный `mvn test`; после T008 запускались точечные controller/OpenAPI tests и полный `mvn test`, итоговый полный набор после возврата к стандартному springdoc - 120 тестов.

## Следующие шаги

- Ожидать human approval по CR002-T008. CR002-T009 не начинать без явной команды пользователя.
