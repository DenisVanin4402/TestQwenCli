# CR002-T001: senior architect review

## Итог

Этап CR002-T001 соответствует stage-level плану: реализована документационная инвентаризация фактического HTTP API без изменения production-кода, тестов, `pom.xml` и OpenAPI YAML.

Блокирующих архитектурных замечаний нет. Зафиксировано одно note-level замечание по явности описания path variables, которое не расширяет scope этапа и требует решения человека перед закрытием или переносом в следующие этапы.

## Соответствие плану

Проверены `work-items.md`, `plan_T001.md`, `inventory_T001.md`, `execution-progress.md`, профиль reviewer и ADR-012.

`inventory_T001.md` фиксирует источники истины для каждого YAML: `ExternalSyncController` и sync DTO для sync API, `ExternalAsyncController` и async DTO для async API, `CallbackPayload` и `HttpCallbackClient` для callback-контракта. Dashboard API и root endpoint явно исключены из `external-gateway-*.yaml`, что соответствует ADR-012 и плану T001.

Сверка с контроллерами и DTO подтвердила основные endpoints, HTTP-методы, `operationId`, заголовки, response status codes, error contract, DTO-поля, enum-значения и callback-сериализацию. `execution-progress.md` корректно фиксирует, что T001 является документационной инвентаризацией, `mvn test` не запускался, а YAML-синхронизация перенесена на T003-T005.

Фактический diff на момент review показывает только документационные изменения CR/PRE-WORK и новые документы T001. Изменений в Java production-коде, тестах, Maven-настройках и YAML не обнаружено.

## Производительность

Этап не меняет runtime path, scheduler/polling, очереди, generated sources или Maven lifecycle. Нового runtime overhead, роста времени сборки и риска нестабильных тестов stage не добавляет.

Остаточный performance-риск связан только со следующими этапами CR002: при генерации OpenAPI-кода нужно отдельно контролировать размер generated sources, фазу Maven-сборки и отсутствие лишнего runtime binding. Для T001 этот риск не реализован.

## Безопасность

Этап не расширяет публичный API и не меняет validation, callback delivery, service-to-service scope или defaults.

Инвентаризация корректно отмечает временный характер `X-Client-Service` для read/cancel/retry и текущий факт: при отсутствии заголовка lookup не ограничивается сервисом-клиентом. Это важно для следующих YAML-этапов, чтобы документация не обещала более строгий access scope, чем реализован.

Инвентаризация также корректно отделяет callback endpoint сервиса-клиента от gateway endpoints и не смешивает его с generated `/v3/api-docs` приложения. Секреты и локальные credential values в review-проверенных артефактах не обнаружены.

## Архитектурные приемы

Решение T001 согласовано с ADR-012: контроллеры и DTO остаются источником истины, YAML не используется как первичный контракт для изменения поведения API, dashboard API не включается в `external-gateway-*.yaml` без отдельного архитектурного решения.

Границы компонентов сохранены: gateway HTTP API, dashboard surface и callback contract описаны раздельно. Разделение contract/domain/persistence не менялось, data/state/deployment/operations не затронуты. Архитектурная документация на этом этапе не требует правок, потому что инвентаризация не меняет C4-границы, sequence flow, модель состояния или deployment-инварианты.

## Замечания

1. severity: `note`
   source: `docs/external-service-gateway/chrequests/CR002/inventory_T001.md`, разделы `Gateway endpoints` и `Спорные места`; `docs/external-service-gateway/chrequests/CR002/work-items.md`, задача `CR002-T001`
   risk: path variables указаны через шаблоны URL (`{taskId}`, `{externalId}`), но их типы и ограничения не вынесены отдельной таблицей параметров. Для T001 это не блокер, однако на T004/T009 есть риск пропустить `taskId` как `int64` с `minimum=1`, `externalId` как `UUID` и нестабильность `ErrorResponse` для ошибок конвертации path variables.
   action: перед синхронизацией async YAML либо добавить в inventory отдельный краткий блок по path variables, либо явно принять текущее описание как достаточное и перенести детализацию в `plan_T004.md`/T004 implementation.
   status: `pending`

## Рекомендация

Рекомендую закрывать CR002-T001 после human approval по note-level замечанию выше. Доработка, если будет выбрана, должна оставаться документационной и не должна менять production-код, тесты, `pom.xml` или YAML в рамках T001.

## Human approval

Ожидается решение человека по замечанию:

- `pending`: принять замечание в документационную доработку T001, отклонить как несущественное для inventory или отложить детализацию до T004/T009 с явной записью в `execution-progress.md` или этом review.
