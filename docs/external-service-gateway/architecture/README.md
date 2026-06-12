# Архитектура external-service-gateway

Дата фиксации: 2026-06-12.

Этот комплект описывает `external-service-gateway` как production-grade внутренний gateway для синхронных и асинхронных вызовов внешнего сервиса с глобальным лимитом `5 concurrent calls`. Документы используют C4-модель и Mermaid. Dynamic view намеренно разбит на отдельные sequence-диаграммы по сценариям: успешный путь, альтернативный успешный путь, timeout, конфликт, retry, callback failure и recovery.

## Область

В scope входят:

- HTTP API gateway: sync, async submit, async polling, cancel, retry;
- HTTP callback из gateway в сервисы-клиенты;
- PostgreSQL-координатор слотов, async-очереди, sync trace и callback-доставки;
- dashboard как встроенный диагностический и нагрузочный инструмент;
- production-инварианты, риски и требования к эксплуатации.

Вне scope:

- внутренняя доменная логика `invest-pay` и `user-expertise`;
- реализация внешнего сервиса;
- Kubernetes/Helm-манифесты, так как в репозитории их пока нет;
- окончательная service-to-service authentication, так как текущий код еще использует `clientService` из payload и временный `X-Client-Service`.

## Карта документов

- [01-c4-context.md](01-c4-context.md) - C4 Level 1, системный контекст, доверенные границы и ключевые инварианты.
- [02-c4-containers.md](02-c4-containers.md) - C4 Level 2, runtime-контейнеры, режимы `memory` и `postgres`.
- [03-c4-components.md](03-c4-components.md) - C4 Level 3, компоненты Spring Boot приложения и их ответственность.
- [04-data-and-state.md](04-data-and-state.md) - модель данных, ER-диаграмма, статусы и ограничения.
- [05-sequence-sync.md](05-sequence-sync.md) - sequence-диаграммы sync-сценариев.
- [06-sequence-async.md](06-sequence-async.md) - sequence-диаграммы async submit, dispatch, polling, cancel и retry.
- [07-sequence-callback.md](07-sequence-callback.md) - sequence-диаграммы callback-доставки и recovery.
- [08-deployment-operations.md](08-deployment-operations.md) - deployment view, эксплуатация, наблюдаемость, rollout и отказоустойчивость.
- [09-production-readiness.md](09-production-readiness.md) - разрыв между текущей реализацией и enterprise production target.
- [decisions.md](decisions.md) - принятые архитектурные решения ADR.

## Источники истины

Контракты API, рабочий вход Maven-сборки и будущей OpenAPI-генерации:

- [external-gateway-sync.yaml](../../../test-qwen-cli-app/src/main/resources/openapi/external-gateway-sync.yaml)
- [external-gateway-async.yaml](../../../test-qwen-cli-app/src/main/resources/openapi/external-gateway-async.yaml)
- [external-gateway-callback.yaml](../../../test-qwen-cli-app/src/main/resources/openapi/external-gateway-callback.yaml)

Документационное зеркало тех же контрактов:

- [external-gateway-sync.yaml](../openapi/external-gateway-sync.yaml)
- [external-gateway-async.yaml](../openapi/external-gateway-async.yaml)
- [external-gateway-callback.yaml](../openapi/external-gateway-callback.yaml)

Синхронность рабочего входа и документационного зеркала проверяет `ExternalGatewayOpenApiContractTest`.

Ключевые реализации:

- `test-qwen-cli-app/src/main/java/com/example/testqwencli/gateway/controller`
- `test-qwen-cli-app/src/main/java/com/example/testqwencli/gateway/services`
- `test-qwen-cli-app/src/main/java/com/example/testqwencli/gateway/repository/postgres`
- `test-qwen-cli-app/src/main/resources/db/changelog/external-gateway/db.changelog-master.yaml`
- `dashboard-backend/src/main/java/com/example/testqwencli/dashboard`

Архитектурные решения:

- [decisions.md](decisions.md)

## Архитектурные инварианты

- Все вызовы внешнего сервиса проходят через gateway.
- Глобальный лимит внешнего сервиса равен `5 concurrent calls`.
- В production-кластере все экземпляры gateway должны использовать один логический PostgreSQL-координатор слотов.
- Слот удерживается lease-записью, а не открытой транзакцией на время upstream-вызова.
- Освобождение и heartbeat слота выполняются только по паре `slot_id + lease_id`.
- Async-задачи не стартуют, если есть живые sync waiters.
- Async сохраняет результат в БД и доставляет callback только после финального статуса.
- Ошибка callback-доставки не меняет итоговый статус upstream-задачи.
- Сервисы-клиенты не читают таблицы gateway напрямую.

## Текущая реализация и target state

Текущая реализация уже содержит memory/postgres repository modes, Spring MVC API, PostgreSQL schema, slot lease, async dispatcher, callback dispatcher и dashboard. Для production target требуется закрыть несколько обязательных разрывов: реальный upstream HTTP client, service-to-service identity, полная наблюдаемость, deployment-манифесты, нагрузочные проверки и эксплуатационные runbook-и. Подробная матрица находится в [09-production-readiness.md](09-production-readiness.md).
