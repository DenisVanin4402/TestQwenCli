# CR003: упрощение ядра gateway без потери функционала

## Назначение

CR003 фиксирует работу по снижению сложности текущего ядра `external-service-gateway` без изменения публичного поведения, архитектурных инвариантов и production-семантики PostgreSQL-координатора.

Цель CR003 - сделать реализацию проще в сопровождении за счет:

- общего dispatch loop для async-задач и callback-доставки;
- общего retry decision для async и callback lifecycle;
- управляемых Spring executors/schedulers вместо ручного управления `ExecutorService`;
- уменьшения ручного JDBC-маппинга;
- проверки, какие не критичные операции с БД можно перевести на Spring Data JPA Repository;
- отдельного доказательства, можно ли критичные транзакционные операции выразить через Spring Data JPA custom fragments/native queries без потери атомарности.

## Входные материалы

- Архитектурные инварианты: `docs/external-service-gateway/architecture/README.md`.
- ADR: `ADR-002`, `ADR-003`, `ADR-004`, `ADR-005`, `ADR-010` в `docs/external-service-gateway/architecture/decisions.md`.
- Предварительный JPA-анализ: `docs/external-service-gateway/chrequests/PRE-WORK/postgres-jpa-repository-plan.md`.
- Текущие горячие точки реализации:
  - `ExternalAsyncDispatcherImpl`;
  - `CallbackDeliveryDispatcherImpl`;
  - `PostgresSlotRepository`;
  - `PostgresAsyncTaskRepository`;
  - `PostgresCallbackDeliveryRepository`.

## Инварианты, которые нельзя менять

- Gateway API для sync, async polling, cancel, retry и callback-контракт не меняются.
- PostgreSQL остается координатором v1 для slots, sync waiters, async queue, sync trace и callback delivery.
- Глобальный лимит внешнего сервиса остается `5 concurrent calls`.
- Слот удерживается lease-записью, а не долгим DB lock во время upstream-вызова.
- `release` и `heartbeat` работают только по паре `slotId + leaseId`.
- Sync имеет приоритет над стартом новых async-вызовов.
- Async-задачи сохраняют результат или ошибку в БД.
- Callback retry/dead state остается persisted, а не переносится в HTTP-клиент.
- Сервисы-клиенты не получают прямой доступ к таблицам gateway.

## Объем

Включено:

- инвентаризация повторяющихся частей dispatcher/retry/JDBC-кода;
- рефакторинг без изменения HTTP-контрактов и state machine;
- проверка возможности использовать Spring-managed executor/scheduler lifecycle;
- выделение общих retry/backoff решений при сохранении разных статусов async и callback;
- уменьшение ручного JDBC-маппинга в текущем PostgreSQL-слое;
- исследование и, при подтверждении безопасности, экспериментальное внедрение JPA для не критичных DB-операций;
- отдельный spike по критичным JPA/native-query операциям;
- расширение contract/integration/concurrency tests там, где рефакторинг затрагивает ядро;
- уточнение contract/code guardrail tests после перехода на generated OpenAPI client/server, чтобы не дублировать compile-time проверки, но сохранить runtime-инварианты;
- проверка, нужны ли изменения архитектурной документации или ADR.

Не включено:

- замена PostgreSQL queue layer на RabbitMQ, Kafka, Temporal или db-scheduler;
- изменение публичного REST/OpenAPI-контракта;
- изменение алгоритма sync priority;
- перенос persisted retry в Resilience4j/Feign/HTTP-client retry;
- удаление текущего JDBC PostgreSQL-режима без отдельного принятого решения;
- production-переключение на JPA без parity, concurrency и performance-проверки.

## Предварительная позиция по Spring Data JPA

Spring Data JPA может упростить часть кода, но не должен механически заменить текущий JDBC-слой.

Кандидаты для JPA Repository:

- простые lookup-запросы по `taskId`, `externalId`, `clientService`;
- read-only projections для polling, dashboard и статистики;
- простые insert/delete sync waiter, если тестами подтверждено сохранение sync priority gate;
- read-модели callback delivery;
- schema validation через Hibernate при `hbm2ddl.auto=validate`, если это не конфликтует с Liquibase.

Операции, которые считаются критичными и не должны переводиться на обычный `find -> mutate -> save`:

- sync/async slot acquire с `FOR UPDATE SKIP LOCKED`, sync reserve и live sync waiters;
- conditional `release`/`heartbeat` по `slotId + leaseId`;
- `reapExpiredLeases`;
- async submit с идемпотентностью и `ON CONFLICT`;
- async claim с `FOR UPDATE SKIP LOCKED`;
- async complete/fail/cancel/retry с conditional update и `RETURNING`;
- callback upsert по `taskId`;
- callback claim с `FOR UPDATE SKIP LOCKED` и обновлением `eventId` внутри payload;
- callback retry/dead/recovery;
- транзакция обработки async, где claim и финальный status update должны сохранять текущую семантику.

Критичные операции можно считать совместимыми с JPA только если custom repository fragment или `EntityManager.createNativeQuery(...)` сохраняет:

- один атомарный SQL statement там, где он есть сейчас;
- те же lock semantics;
- те же transaction boundaries;
- те же условия status transition;
- тот же результат `RETURNING` или эквивалент без дополнительной гонки;
- прохождение существующих repository contracts и PostgreSQL concurrency integration tests.

## Очередь запуска

| Порядок | Задача | Приоритет | Результат |
| --- | --- | --- | --- |
| 1 | CR003-T001: инвентаризация ядра и DB-операций | P0 | Есть карта повторяющегося dispatcher/retry/JDBC-кода и классификация DB-операций на критичные и не критичные. |
| 2 | CR003-T002: общий dispatch loop | P0 | Async dispatcher и callback dispatcher используют общий каркас batch-dispatch без изменения доменной обработки. |
| 3 | CR003-T003: управляемые Spring executors | P0 | Ручные `Executors.newFixedThreadPool` заменены на Spring-managed executor beans с сохранением bounded parallelism. |
| 4 | CR003-T004: общий retry decision | P0 | Retry/backoff/dead decision выделен в общий компонент, но async и callback статусы остаются явными. |
| 5 | CR003-T005: уменьшение ручного JDBC-маппинга | P1 | Повторяющиеся row/json/status mapping helpers унифицированы без изменения SQL-атомарности. |
| 6 | CR003-T006: JPA-дизайн для не критичных DB-операций | P1 | Зафиксировано, какие операции можно безопасно перевести на Spring Data JPA Repository и какие нельзя. |
| 7 | CR003-T007: экспериментальный JPA read/non-critical adapter | P2 | При подтверждении T006 добавлен или спланирован `postgres-jpa` режим для не критичных операций без production-переключения. |
| 8 | CR003-T008: spike критичных JPA/native операций | P2 | Доказано или отклонено использование JPA custom fragments/native queries для hot-path операций. |
| 9 | CR003-T009: parity, contract guardrails и concurrency проверки | P0 | Рефакторинг проходит существующие contract, e2e и PostgreSQL concurrency tests; при JPA добавлены отдельные parity tests; после generated OpenAPI client/server сохранены только нужные contract/code guardrails. |
| 10 | CR003-T010: проверка архитектурной документации и ADR | P1 | Зафиксировано, меняются ли архитектурные документы; при необходимости добавлен ADR. |
| 11 | CR003-T011: финальная проверка | P0 | `mvn test` проходит, а для DB-изменений запущен или явно отложен `mvn verify -Pintegration-tests`. |

## Детализация задач

### CR003-T001: инвентаризация ядра и DB-операций

Цель: перед рефакторингом зафиксировать, где именно находится сложность и какие операции нельзя упрощать без риска гонок.

Объем работ:

- Выписать общий код batch-dispatch в `ExternalAsyncDispatcherImpl` и `CallbackDeliveryDispatcherImpl`.
- Выписать retry/backoff/dead правила async и callback.
- Выписать ручной mapping в PostgreSQL repositories.
- Разделить DB-операции на:
  - критичные atomic/locking operations;
  - не критичные read/simple write operations;
  - статистику/dashboard queries;
  - операции, требующие `LISTEN/NOTIFY`.
- Сверить классификацию с ADR и `postgres-jpa-repository-plan.md`.

Критерии приемки:

- Создан `inventory_T001.md`.
- Для каждой DB-операции указано: текущий класс, SQL-семантика, риск JPA-переноса, рекомендуемый путь.
- Нет реализации до согласования классификации.

### CR003-T002: общий dispatch loop

Цель: убрать дублирование batch worker orchestration без смешивания async и callback бизнес-логики.

Объем работ:

- Спроектировать общий каркас для `dispatchBatch(maxIterations)`.
- Сохранить отдельные `dispatchOnce` для async и callback.
- Сохранить разные error handling и state transition.
- Не менять scheduler API.
- Покрыть существующими dispatcher/scheduler tests.

Критерии приемки:

- `ExternalAsyncDispatcherImpl` и `CallbackDeliveryDispatcherImpl` больше не дублируют orchestration futures/batch accounting.
- Доменные операции claim/call/complete/fail остаются читаемыми в своих классах.
- `mvn test` проходит.

### CR003-T003: управляемые Spring executors

Цель: заменить ручное создание thread pools на управляемые Spring beans.

Объем работ:

- Вынести executor configuration для async dispatcher и callback delivery.
- Сохранить отдельные лимиты `dispatchBatchSize` и `deliveryBatchSize`.
- Сохранить bounded parallelism и отсутствие неконтролируемой очереди.
- Проверить graceful shutdown.
- Обновить tests, которые зависят от прямого создания dispatcher.

Критерии приемки:

- Production-код не создает `Executors.newFixedThreadPool` внутри dispatcher constructors.
- Thread names и lifecycle задаются конфигурацией.
- При остановке контекста worker threads корректно завершаются.
- `mvn test` проходит.

### CR003-T004: общий retry decision

Цель: сделать retry/backoff/dead правила единообразными и тестируемыми.

Объем работ:

- Выделить общий value object или service для решения `retry vs dead`.
- Учитывать `attempts`, `maxAttempts`, `backoff`, retryable error и текущий момент времени.
- Оставить async и callback разные статусы и разные persisted fields.
- Покрыть decision unit tests.

Критерии приемки:

- В async и callback коде нет разъехавшейся арифметики attempts/backoff.
- Ошибки callback-доставки не меняют итоговый upstream-status async-задачи.
- Существующие retry/dead flow tests проходят.

### CR003-T005: уменьшение ручного JDBC-маппинга

Цель: упростить текущий JDBC PostgreSQL-слой без изменения SQL-алгоритмов.

Объем работ:

- Найти дублирующиеся row mappers, enum conversions, timestamp conversions и JSONB conversions.
- Вынести общие helpers там, где это сокращает код без скрытия SQL-смысла.
- Сохранить `PostgresTableNames` и явную schema handling.
- Не переписывать критичные CTE/native SQL на `find -> save`.

Критерии приемки:

- Поведение `PostgresSlotRepository`, `PostgresAsyncTaskRepository`, `PostgresCallbackDeliveryRepository` не меняется.
- Repository contract tests для memory и postgres остаются зелеными.
- Код стал короче или локально проще, без нового универсального mini-framework.

### CR003-T006: JPA-дизайн для не критичных DB-операций

Цель: принять проверяемое решение, где Spring Data JPA действительно упрощает код.

Объем работ:

- Сопоставить операции из `inventory_T001.md` с возможностями Spring Data JPA Repository, projections, `@Lock`, `@QueryHints`, `@Modifying`, native queries и custom fragments.
- Выделить операции, где JPA дает пользу без риска гонок.
- Выделить операции, где нужен только native SQL через custom fragment.
- Зафиксировать, нужен ли отдельный режим `external-gateway.repository.type=postgres-jpa` или только read-side JPA внутри текущего `postgres`.
- Описать rollback: возврат к текущему JDBC PostgreSQL-режиму.

Критерии приемки:

- Создан `jpa_scope_T006.md`.
- Для каждой операции указано решение: JPA repository, JPA native fragment, оставить JDBC, отложить.
- Критичные операции не помечены как обычный CRUD/JPA save.

### CR003-T007: экспериментальный JPA read/non-critical adapter

Цель: если T006 подтверждает пользу, добавить JPA только там, где риск минимален.

Объем работ:

- Добавить `spring-boot-starter-data-jpa` только при принятом плане.
- Настроить JPA infrastructure без автоматического изменения схемы.
- Использовать Liquibase как источник schema.
- Реализовать read-only projections или простые repository methods.
- Не переключать production default.

Критерии приемки:

- `memory` остается дефолтом.
- `postgres` JDBC-режим остается рабочим.
- Если добавлен `postgres-jpa`, он включается отдельным property value.
- JPA entities не протекают в service/controller/domain models.

### CR003-T008: spike критичных JPA/native операций

Цель: доказать, можно ли уместить критичные транзакционные операции в Spring Data JPA без потери корректности.

Объем работ:

- Выбрать минимальный набор hot-path операций для spike:
  - async `claimNextPending`;
  - callback `claimNextPending`;
  - async slot acquire или sync slot acquire.
- Реализовать через custom repository fragment/native query или `EntityManager`.
- Сравнить SQL shape с текущим JDBC.
- Проверить `FOR UPDATE SKIP LOCKED`, `RETURNING`, `ON CONFLICT`, JSONB и transaction boundaries.
- Зафиксировать решение: продолжать, ограничить JPA read-side, или отклонить критичный JPA-путь.

Критерии приемки:

- Spike проходит PostgreSQL integration/concurrency tests или явно фиксирует причину отказа.
- Нет production-переключения без отдельного human approval.
- Если критичный JPA-путь отклонен, это не блокирует остальные упрощения CR003.

### CR003-T009: parity, contract guardrails и concurrency проверки

Цель: доказать, что рефакторинг ядра не изменил поведение.

Объем работ:

- Запустить быстрые unit/Spring tests.
- Запустить repository contract tests.
- Для DB-path изменений запустить PostgreSQL integration tests.
- Для dispatcher/slot изменений запустить concurrency tests.
- При JPA-режиме добавить parity tests к тем же behavior suites.
- После перехода CR002 на generated OpenAPI client/server пересмотреть `ExternalGatewayOpenApiContractTest`: убрать ручное широкое сравнение YAML с generated `/v3/api-docs`, если его заменяет compile-time contract generated interfaces/DTO, и оставить узкие guardrail-проверки.
- Зафиксировать, нужен ли `spec freshness` test: одна source-of-truth спецификация не требует проверки копий, а наличие копий в `docs` и `src/main/resources/openapi` требует CI/test-защиты от drift.
- Зафиксировать, нужен ли `generation freshness` test: generated sources в `target/generated-sources` проверяются Maven-сборкой, а commited generated sources требуют verify-step на отсутствие diff после регенерации.
- Сохранить runtime server conformance checks для production-инвариантов, которые compile-time генерация не гарантирует: status codes, error body, nullable fields, headers, `additionalProperties`, serialization форматы и ключевые workflow.
- Сохранить callback client conformance checks, потому что gateway в callback-контракте выступает HTTP-клиентом: `X-Callback-Attempt`, optional `X-Request-Id`, wire-format `finishedAt`, игнорирование response body и retry на non-2xx.

Критерии приемки:

- `mvn test` проходит.
- Для PostgreSQL изменений `mvn verify -Pintegration-tests` проходит или блокер явно зафиксирован.
- Нет ухудшения публичного HTTP/API поведения.
- Есть явное решение, какие contract/code sync tests оставлены после generated OpenAPI client/server и какие удалены как дублирующие compile-time генерацию.

### CR003-T010: проверка архитектурной документации и ADR

Цель: определить, стал ли CR003 архитектурным изменением или остался внутренним рефакторингом.

Объем работ:

- Проверить, изменились ли component boundaries, data/state, deployment/operations или production invariants.
- Если появился `postgres-jpa` режим, обновить architecture docs и добавить ADR или дополнение к ADR-002.
- Если изменения только внутренние и поведение не меняется, явно зафиксировать отсутствие архитектурных правок в `execution-progress.md`.

Критерии приемки:

- Есть явное решение по architecture docs.
- ADR добавлен только при устойчивом архитектурном решении, а не для spike.

### CR003-T011: финальная проверка

Цель: закрыть CR003 только после полного подтверждения поведения.

Объем работ:

- Запустить `mvn test`.
- Для PostgreSQL/JPA изменений запустить `mvn verify -Pintegration-tests`.
- Зафиксировать результат в `execution-progress.md`.
- Зафиксировать итоговое решение по JPA.

Критерии приемки:

- Все P0 задачи закрыты.
- Результаты проверок записаны.
- Оставшиеся P1/P2 задачи либо закрыты, либо явно отложены с причиной.

## Общие правила приемки CR003

- Перед реализацией каждого этапа `CR003-TXXX` создается или обновляется `plan_TXXX.md`.
- После реализации каждого этапа и до закрытия создается `review_TXXX.md` через senior architect review.
- Публичный API не меняется без отдельного явного решения и тестов.
- Критичные DB-операции не переводятся на обычный JPA CRUD без доказательства атомарности.
- Текущий JDBC PostgreSQL-режим остается rollback path для любых JPA-экспериментов.
- Все новые документы и комментарии написаны на русском языке.
