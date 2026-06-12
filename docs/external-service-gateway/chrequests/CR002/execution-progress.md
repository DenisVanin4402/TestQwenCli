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
- [ ] Синхронизировать `external-gateway-callback.yaml`.
- [ ] Перенести синхронизированные спецификации в `test-qwen-cli-app/src/main/resources/openapi`.
- [ ] Подключить `openapi-generator-maven-plugin` к Maven-сборке `test-qwen-cli-app`.
- [ ] Выполнить рефакторинг на использование generated OpenAPI-кода в выбранной роли.
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
   - Для submit API добавлена отдельная схема `ExternalAsyncDeliveryMode` со значениями `CALLBACK` и `POLLING`; для read-модели `AsyncDeliveryMode` оставлены значения Java enum `CALLBACK`, `POLLING`, `SYNC`.
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

## Текущий результат

- CR002-T001 реализована как документационная инвентаризация, прошла senior architect review и принята человеком.
- Note-level замечание CR002-T001 по path variables перенесено в контекст T004/T009.
- Принятое архитектурное решение по CR002 зафиксировано в `docs/external-service-gateway/architecture/decisions.md`.
- Для этапов CR002 требуется stage-level `plan_TXXX.md` перед стартом и `review_TXXX.md` перед закрытием.
- CR002-T002 реализована и прошла senior architect review без замечаний: OpenAPI contract test читает актуальный каталог `docs/external-service-gateway/openapi`.
- CR002-T003 реализована и прошла senior architect review без замечаний: `external-gateway-sync.yaml` синхронизирован с фактическими sync DTO по ограничениям схем.
- CR002-T004 реализована и прошла senior architect review без блокеров: `external-gateway-async.yaml` синхронизирован с фактическими async DTO по ограничениям схем и enum-значениям.
- В `review_T004.md` есть два note-level замечания со статусом `pending`; требуется human approval для закрытия или переноса замечаний.
- Синхронизация callback YAML еще не выполнялась.
- Подключение OpenAPI code generation и связанный рефакторинг только запланированы.
- Stage-level senior architect review создан для T001, T002, T003 и T004.
- Финальная проверка необходимости дополнительных архитектурных правок после реализации CR002 только запланирована.
- После T002, T003 и T004 запускался точечный `ExternalGatewayOpenApiContractTest`; полный `mvn test` еще не запускался.

## Следующие шаги

- Получить human approval по CR002-T004: закрыть этап с двумя note-level замечаниями, принять, отклонить или отложить каждое замечание.
- После human approval дождаться явной команды пользователя на продолжение к CR002-T005.
