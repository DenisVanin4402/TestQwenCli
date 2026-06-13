# CR004: журнал выполнения

## Назначение

Файл фиксирует рабочий статус CR004: перенос sync waiters из отдельной таблицы `ext_sync_waiters` в lifecycle-записи `SYNC_REQUEST` таблицы `ext_request_queue`.

Основная постановка и очередь задач находятся в `work-items.md`.

## Чеклист этапов

- [ ] CR004-T001: инвентаризация текущих sync/async запросов и индексов.
- [ ] CR004-T002: stage plan и целевая модель state machine.
- [ ] CR004-T003: Liquibase migration для `record_type`, `idempotency_key`, `wait_expires_at`, constraints и indexes.
- [ ] CR004-T004: адаптация repository contracts и domain model.
- [ ] CR004-T005: прямая реализация request-queue sync waiter store.
- [ ] CR004-T006: recovery истекших `SYNC_REQUEST`.
- [ ] CR004-T007: фильтрация API/dashboard/statistics.
- [ ] CR004-T008: PostgreSQL parity и concurrency tests.
- [ ] CR004-T009: удаление `ext_sync_waiters` и старого кода.
- [ ] CR004-T010: rollout-документация и retention policy.
- [ ] CR004-T011: архитектурная документация и ADR.
- [ ] CR004-T012: финальная проверка.

## Статус задач CR004

| Задача | Статус | Результат |
| --- | --- | --- |
| CR004-T001: инвентаризация текущих sync/async запросов и индексов | Не начата | Требуется `plan_T001.md` перед выполнением. |
| CR004-T002: stage plan и целевая модель state machine | Не начата | Требуется `plan_T002.md` перед выполнением. |
| CR004-T003: Liquibase migration для `record_type`, `idempotency_key`, `wait_expires_at`, constraints и indexes | Не начата | Выполняется только после утверждения T002. |
| CR004-T004: адаптация repository contracts и domain model | Не начата | Требует готовой целевой модели. |
| CR004-T005: прямая реализация request-queue sync waiter store | Не начата | Требует схемы и repository contracts; feature flag не обязателен, так как система не в production. |
| CR004-T006: recovery истекших `SYNC_REQUEST` | Не начата | Обязательный этап перед включением request-queue режима. |
| CR004-T007: фильтрация API/dashboard/statistics | Не начата | Требуется, чтобы служебные sync rows не протекали в async API. |
| CR004-T008: PostgreSQL parity и concurrency tests | Не начата | Обязательный этап для доказательства sync priority. |
| CR004-T009: удаление `ext_sync_waiters` и старого кода | Не начата | Выполняется в рамках CR004 после успешных tests/review новой модели. |
| CR004-T010: rollout-документация и retention policy | Не начата | Требуется для будущего production-окружения и эксплуатации. |
| CR004-T011: архитектурная документация и ADR | Не начата | Требуется до закрытия CR004. |
| CR004-T012: финальная проверка | Не начата | Требуется перед закрытием CR004. |

## История выполнения

### 2026-06-13

1. Создана постановка CR004.
   - Результат: добавлен `work-items.md` с целевой моделью `SYNC_REQUEST` в `ext_request_queue`.
   - Результат: зафиксированы обязательные новые колонки `record_type`, `idempotency_key` и `wait_expires_at`.
   - Результат: описаны обязательные partial indexes для async claim, live sync waiter gate, recovery и retention.
   - Результат: описаны constraints, статусная модель sync lifecycle, recovery и rollout/rollback.

2. Зафиксирована предварительная целевая позиция.
   - Решение: `ext_request_queue` должна различать `ASYNC_TASK` и `SYNC_REQUEST` через `record_type`.
   - Решение: live sync waiter определяется только как `record_type='SYNC_REQUEST'`, `status='PENDING'`, `wait_expires_at > now`.
   - Решение: `SYNC_REQUEST/IN_PROGRESS` не является waiter-ом и влияет на async только через занятый `SYNC` lease в `ext_slots`.
   - Решение: повторные sync-запросы должны отсекаться по `client_service + idempotency_key` в `ext_request_queue`; будущая `@Idempotent` библиотека отвечает за replay/hash, а unique index остается DB-level guard.
   - Решение: удаление `ext_sync_waiters` относится к позднему этапу CR004 и допустимо после успешных tests/review новой модели.

3. Проверки не запускались.
   - Причина: изменялась только проектная документация CR004, production-код и тесты не менялись.

4. Уточнено, что система находится в фазе разработки и не работает в production.
   - Решение: CR004 больше не требует долгого production rollout с dual-write, shadow-read и обязательным feature flag.
   - Решение: переход может быть прямым: обновление схемы, перевод runtime-кода на `SYNC_REQUEST`, recovery, tests, затем удаление `ext_sync_waiters`.
   - Решение: удаление `ext_sync_waiters` допустимо в рамках CR004 после успешных проверок и review, потому что production rollback без потери данных сейчас не требуется.
   - Решение: rollback для dev-phase допускается через `git revert` и пересоздание локальной/Testcontainers БД.

## Текущий результат

- CR004 создан как отдельная очередь работ по замене `ext_sync_waiters` на sync lifecycle rows в `ext_request_queue`.
- Все этапы CR004 находятся в статусе "Не начата".
- Перед началом любого этапа требуется stage-level `plan_TXXX.md`.
- После реализации каждого этапа требуется senior architect review в `review_TXXX.md`.
- Текущий рабочий код и схема БД не изменялись.
- CR004 исходит из того, что система не в production; миграционная стратегия упрощена до прямого dev-phase перехода.

## Следующие шаги

- Начать с `CR004-T001`: создать `plan_T001.md` и выполнить инвентаризацию текущих SQL, индексов, repository methods и API/dashboard consumers.
- После T001 подготовить `CR004-T002`, выбрать вариант A или B для создания sync lifecycle row и зафиксировать точный state machine перед миграцией схемы.
