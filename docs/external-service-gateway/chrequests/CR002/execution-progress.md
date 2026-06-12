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
- [ ] Исправить путь чтения OpenAPI в contract test.
- [ ] Синхронизировать `external-gateway-sync.yaml`.
- [ ] Синхронизировать `external-gateway-async.yaml`.
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

## Текущий результат

- CR002-T001 реализована как документационная инвентаризация, прошла senior architect review и ожидает human approval по одному note-level замечанию.
- Принятое архитектурное решение по CR002 зафиксировано в `docs/external-service-gateway/architecture/decisions.md`.
- Для этапов CR002 требуется stage-level `plan_TXXX.md` перед стартом и `review_TXXX.md` перед закрытием.
- Фактическая синхронизация YAML и контроллеров еще не выполнялась; T001 только зафиксировала текущее поведение и спорные места.
- Подключение OpenAPI code generation и связанный рефакторинг только запланированы.
- Stage-level senior architect review создан для T001; reviews для следующих этапов только запланированы.
- Финальная проверка необходимости дополнительных архитектурных правок после реализации CR002 только запланирована.
- Проверки Maven после T001 не запускались, потому что текущий шаг меняет только документацию CR.

## Следующие шаги

- Получить human approval по note-level замечанию из `review_T001.md`: принять в T001, отклонить или отложить детализацию path variables до T004/T009.
- Затем выполнить CR002-T002, чтобы дальнейшие проверки читали правильный каталог OpenAPI.
