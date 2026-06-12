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
- [ ] Выполнить инвентаризацию контроллеров и DTO.
- [ ] Исправить путь чтения OpenAPI в contract test.
- [ ] Синхронизировать `external-gateway-sync.yaml`.
- [ ] Синхронизировать `external-gateway-async.yaml`.
- [ ] Синхронизировать `external-gateway-callback.yaml`.
- [ ] Перенести синхронизированные спецификации в `test-qwen-cli-app/src/main/resources/openapi`.
- [ ] Подключить `openapi-generator-maven-plugin` к Maven-сборке `test-qwen-cli-app`.
- [ ] Выполнить рефакторинг на использование generated OpenAPI-кода в выбранной роли.
- [ ] Усилить OpenAPI contract checks по стабильным частям контракта.
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

## Текущий результат

- Очередь CR002 готова к запуску.
- Принятое архитектурное решение по CR002 зафиксировано в `docs/external-service-gateway/architecture/decisions.md`.
- Фактическая синхронизация YAML и контроллеров еще не выполнялась.
- Подключение OpenAPI code generation и связанный рефакторинг только запланированы.
- Финальная проверка необходимости дополнительных архитектурных правок после реализации CR002 только запланирована.
- Проверки Maven не запускались, потому что текущий шаг меняет только постановочную документацию.

## Следующие шаги

- Начать с CR002-T001: составить матрицу фактических endpoints и DTO по контроллерам.
- Затем выполнить CR002-T002, чтобы дальнейшие проверки читали правильный каталог OpenAPI.
