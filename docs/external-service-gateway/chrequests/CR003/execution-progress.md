# CR003: журнал выполнения

## Назначение

Файл фиксирует рабочий статус CR003: какие этапы выполнены, какие решения приняты, какие проверки запускались и какие блокеры обнаружены. Основная очередь задач находится в `work-items.md`.

## Чеклист этапов

- [x] CR003-T001: упрощение request-level idempotency через `@Idempotent`.
- [ ] CR003-T002: инвентаризация ядра и DB-операций.
- [ ] CR003-T003: общий dispatch loop.
- [ ] CR003-T004: управляемые Spring executors.
- [ ] CR003-T005: общий retry decision.
- [ ] CR003-T006: уменьшение ручного JDBC-маппинга.
- [ ] CR003-T007: JPA-дизайн для не критичных DB-операций.
- [ ] CR003-T008: экспериментальный JPA read/non-critical adapter.
- [ ] CR003-T009: spike критичных JPA/native операций.
- [ ] CR003-T010: parity, contract guardrails и concurrency проверки.
- [ ] CR003-T011: проверка архитектурной документации и ADR.
- [ ] CR003-T012: финальная проверка.

## Статус задач CR003

| Задача | Статус | Результат |
| --- | --- | --- |
| CR003-T001: упрощение request-level idempotency через `@Idempotent` | Завершена | Ручная async submit idempotency-обвязка удалена; DB/memory duplicate guard сохранен; duplicate submit до подключения библиотеки fail-closed возвращает `409`; `review_T001.md` обновлен. |
| CR003-T002: инвентаризация ядра и DB-операций | Не начата | Требуется `plan_T002.md` перед выполнением. |
| CR003-T003: общий dispatch loop | Не начата | Требуется `plan_T003.md` перед выполнением. |
| CR003-T004: управляемые Spring executors | Не начата | Требуется `plan_T004.md` перед выполнением. |
| CR003-T005: общий retry decision | Не начата | Требуется `plan_T005.md` перед выполнением. |
| CR003-T006: уменьшение ручного JDBC-маппинга | Не начата | Требуется `plan_T006.md` перед выполнением. |
| CR003-T007: JPA-дизайн для не критичных DB-операций | Не начата | Требуется `plan_T007.md` перед выполнением. |
| CR003-T008: экспериментальный JPA read/non-critical adapter | Не начата | Выполняется только после принятого результата CR003-T007. |
| CR003-T009: spike критичных JPA/native операций | Не начата | Выполняется только как доказательство или отказ, не как production-переключение. |
| CR003-T010: parity, contract guardrails и concurrency проверки | Не начата | Выполняется после затрагивающих ядро изменений; включает решение по contract/code guardrail tests после generated OpenAPI client/server. |
| CR003-T011: проверка архитектурной документации и ADR | Не начата | Требуется после определения итогового scope CR003. |
| CR003-T012: финальная проверка | Не начата | Требуется перед закрытием CR003. |

## История выполнения

### 2026-06-12

1. Создана постановка CR003.
   - Результат: добавлен `work-items.md` с очередью этапов по упрощению ядра: общий dispatch loop, общий retry decision, управляемые Spring executors, уменьшение ручного JDBC-маппинга.
   - Результат: предложение по Spring Data JPA оформлено как отдельная проверяемая гипотеза, а не как уже принятое решение.
   - Результат: существующий `docs/external-service-gateway/chrequests/PRE-WORK/postgres-jpa-repository-plan.md` указан как входной материал для CR003.

2. Зафиксирована предварительная позиция по JPA.
   - Решение: не критичные read/simple-write операции можно рассматривать как кандидаты для Spring Data JPA Repository.
   - Решение: критичные операции с `FOR UPDATE SKIP LOCKED`, `ON CONFLICT`, `RETURNING`, JSONB update и строгими transaction boundaries должны остаться JDBC или быть доказаны через JPA custom fragments/native queries.
   - Решение: production default не переключается на JPA в рамках постановки CR003.

3. Проверки не запускались.
   - Причина: на этом шаге изменялась только проектная документация CR003, production-код и тесты не менялись.

4. В CR003-T010 добавлена работа по contract/code guardrail tests после generated OpenAPI client/server.
   - Решение: после CR002 с генерацией клиента и сервера не сохранять широкое ручное сравнение YAML с `/v3/api-docs`, если его заменяет compile-time contract generated interfaces/DTO.
   - Решение: оставить или добавить только узкие guardrails на source/generation freshness, runtime server conformance и callback client wire-format там, где генерация не гарантирует production-семантику.
   - Production-код, тесты и OpenAPI YAML на этом шаге не менялись.
   - Проверки не запускались, потому что изменялась только постановка CR003.

### 2026-06-13

1. CR003-T001 поставлен первым этапом CR003.
   - Результат: в `work-items.md` добавлен этап упрощения request-level idempotency через будущую внутреннюю библиотеку `@Idempotent`.
   - Решение: библиотека недоступна в репозитории, поэтому зависимость не добавляется; места будущего применения фиксируются комментариями у service boundary методов.
   - Решение: для async submit ключ будущей аннотации - `request.clientService + request.externalId`, hash fields - `request.payload`, `request.priority`, `request.deliveryMode`; `requestId` не входит в hash.
   - Решение: для sync ключ будущей аннотации - `request.clientService + headers.idempotencyKey`, hash fields - `request.externalId`, `request.payload`; `headers.requestId` не входит в hash.
   - Решение: DB unique guard в `ext_request_queue` остается нижним предохранителем от дублей.
   - Решение: повторные sync-запросы должны отсекаться по ключу через `ext_request_queue` и будущую `SYNC_REQUEST` lifecycle-модель CR004.
   - Решение: целевое поведение для sync без `Idempotency-Key` - отклонять запрос до получения слота и upstream-вызова, но только после отдельного изменения OpenAPI/валидации; fallback key из `requestId` или `externalId` запрещен.

2. Добавлены code-level TODO-маркеры.
   - Результат: у `ExternalAsyncServiceImpl.submit` добавлен комментарий с будущим `@Idempotent` key/hash contract.
   - Результат: у `ExternalSyncServiceImpl.sync` добавлен комментарий с будущим `@Idempotent` key/hash contract, требованием не занимать второй слот при повторе и целевым reject без `Idempotency-Key` после отдельного контрактного изменения.

3. Выполнен senior architect review CR003-T001.
   - Результат: создан `review_T001.md`.
   - Результат: блокирующих замечаний нет.
   - Результат: замечание уровня `note` про фиксацию решения по отсутствующему sync `Idempotency-Key` принято в рамках закрытия этапа и отражено в этом журнале.

4. Проверки.
   - `git diff --check` выполнен без whitespace-ошибок; Git показал только предупреждение о будущей нормализации LF/CRLF в рабочем дереве Windows.
   - `mvn test` не запускался, потому что production-поведение, OpenAPI, схема БД и тесты не менялись.

5. CR003-T001 переоткрыт после уточнения решения.
   - Причина: ручная async idempotency-обвязка должна быть удалена сейчас, а внутренняя библиотека `@Idempotent` будет подключена позже вне этого репозитория.
   - Решение: production-запуск без библиотеки идемпотентности запрещен; текущая реализация должна явно показывать gap, а не маскировать его частичной ручной реализацией.
   - Решение: до подключения библиотеки duplicate async submit по `clientService + externalId` отклоняется `409 IDEMPOTENCY_CONFLICT` через DB/memory guard, без successful replay и без `conflictingFields`.
   - Решение: `AsyncSubmitResponse.alreadyExisted` сохраняется в OpenAPI/DTO для будущего replay, но успешный submit в текущем fail-closed режиме возвращает `false`.

6. Удалена ручная async submit idempotency-логика.
   - Результат: `AsyncSubmitResult` больше не содержит `existingTaskId`, `conflictingFields`, `alreadyExisted`.
   - Результат: `AsyncSubmitResultType` содержит `SUBMITTED` и `DUPLICATE_REJECTED`.
   - Результат: `MemoryAsyncTaskRepository.submit` больше не сравнивает `payload`, `priority`, `deliveryMode` и не возвращает существующую задачу.
   - Результат: `PostgresAsyncTaskRepository.submit` оставляет атомарный `INSERT ... ON CONFLICT DO NOTHING`, но больше не делает `SELECT existing` и hash/diff compare.
   - Результат: `ExternalAsyncServiceImpl.submit` мапит `DUPLICATE_REJECTED` в `AsyncIdempotencyConflictException`; успешный response возвращает `alreadyExisted=false`.

7. Обновлены контракты, тесты и архитектурная документация.
   - Результат: `external-gateway-async.yaml` в рабочем входе Maven и документационном зеркале описывает `202` как новую задачу и `409` как duplicate guard до `@Idempotent`.
   - Результат: repository/controller/PostgreSQL concurrency/integration tests обновлены под fail-closed duplicate behavior.
   - Результат: обновлены `03-c4-components.md`, `04-data-and-state.md`, `06-sequence-async.md`, `09-production-readiness.md` и `README.md`; async `@Idempotent` добавлен как production readiness gate.

8. Проверки после удаления алгоритма.
   - `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalAsyncControllerTest,MemoryAsyncTaskRepositoryTest,ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` - успешно, 29 tests.
   - `mvn -pl test-qwen-cli-app -am test` - успешно, 120 tests.
   - `mvn -pl test-qwen-cli-app -am -Pintegration-tests verify` - успешно, 48 integration tests.
   - `git diff --check` выполнен без whitespace-ошибок; Git показал только предупреждение о будущей нормализации LF/CRLF в рабочем дереве Windows.

9. Обновлен senior architect review CR003-T001 после реализации.
   - Результат: `review_T001.md` обновлен под реализационный scope.
   - Результат: замечание `low` по stale async state machine принято и исправлено в `04-data-and-state.md`: duplicate submit больше не является переходом `PENDING -> PENDING`.
   - Результат: замечание `note` по верхнеуровневому scope CR003 принято и исправлено в `work-items.md`: HTTP/OpenAPI shape сохраняется, а изменение duplicate async submit behavior явно отмечено как dev-phase исключение CR003-T001.

## Текущий результат

- CR003 создан как новая очередь работ по внутреннему упрощению ядра gateway.
- CR003-T001 завершен как реализационный этап: ручная async submit idempotency удалена, DB/memory duplicate guard сохранен, `@Idempotent` зафиксирован как обязательный production gate.
- Перед началом любого этапа требуется stage-level `plan_TXXX.md`.
- После реализации этапа требуется senior architect review в `review_TXXX.md`.
- JPA-перенос ограничен исследованием и proof-by-tests; текущий JDBC PostgreSQL-режим остается базовым rollback path.
- В CR003-T010 явно добавлена проверка, какие contract/code sync tests нужны после generated OpenAPI client/server, а какие должны быть удалены как дублирующие compile-time генерацию.

## Следующие шаги

- Остановиться после CR003-T001 и ждать явной команды пользователя на продолжение.
- После команды перейти к `CR003-T002`: создать `plan_T002.md` и выполнить инвентаризацию dispatcher/retry/JDBC/JPA-кандидатов.
