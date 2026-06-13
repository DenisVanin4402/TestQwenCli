# CR004: перенос sync waiters в жизненный цикл `ext_request_queue`

## Назначение

CR004 фиксирует изменение модели данных и state machine sync-запросов: отказаться от отдельной таблицы `ext_sync_waiters` и хранить ожидание sync-слота как часть жизненного цикла записи `SYNC_REQUEST` в `ext_request_queue`.

Цель - сохранить гарантированный приоритет ожидающих sync-запросов над стартом новых async-вызовов, но убрать отдельную таблицу waiters и получить одну запись sync-запроса от ожидания слота до финального `DONE` или `FAILED`.

## Допущение CR004: система не в production

CR004 планируется для фазы разработки. Production-трафика и production-данных, которые нужно мигрировать без downtime, сейчас нет.

Следствия:

- не требуется долгий production rollout с dual-write, shadow-read и отдельным окном удаления таблицы;
- feature flag для выбора старого или нового хранилища waiters не является обязательным;
- `ext_sync_waiters` можно удалить в рамках CR004 после того, как новая модель пройдет быстрые и PostgreSQL integration/concurrency tests;
- rollback в dev-phase допускается через `git revert` и пересоздание локальной/Testcontainers БД;
- Liquibase все равно должен описывать целевую схему явно, потому что тесты и будущие окружения должны стартовать воспроизводимо;
- архитектурная документация и ADR все равно обязательны, потому что меняется модель данных и state machine sync-запросов.

## Текущее состояние

Сейчас используются две разные сущности:

- `ext_sync_waiters` - короткоживущие строки ожидания sync-слота;
- `ext_request_queue` с `delivery_mode='SYNC'` - финальные trace-строки уже завершенных sync-запросов.

Текущий flow:

1. Sync-запрос сразу пробует взять `SYNC` lease в `ext_slots`.
2. Если слот найден, gateway вызывает upstream и после результата вставляет финальный sync trace в `ext_request_queue`.
3. Если слот не найден и timeout ожидания больше нуля, gateway вставляет строку в `ext_sync_waiters`.
4. Пока в `ext_sync_waiters` есть live waiter, `acquireAsyncSlot` не стартует новый async-вызов.
5. После успеха, timeout или interruption waiter удаляется в `finally`.
6. Финальный sync trace все равно вставляется отдельно в `ext_request_queue` как `DONE` или `FAILED`.

Неуспешные запросы уже сохраняются:

- sync ошибки сохраняются в `ext_request_queue` как `delivery_mode='SYNC'`, `status='FAILED'`;
- async ошибки сохраняются в `ext_request_queue` как `FAILED`, `DEAD` или `CANCELLED`;
- callback ошибки сохраняются в `ext_callback_delivery`, а агрегированный статус отражается в `ext_request_queue.callback_delivery_status`.

## Целевое состояние

`ext_request_queue` становится общей таблицей request lifecycle:

- async-задачи остаются в ней как `ASYNC_TASK`;
- sync-запросы хранятся в ней как `SYNC_REQUEST`;
- отдельная таблица `ext_sync_waiters` после переходного периода больше не нужна.

Целевая запись sync-запроса проходит lifecycle:

```text
PENDING      - sync-запрос ждет слот и блокирует старт новых async-вызовов
IN_PROGRESS  - sync-запрос получил слот и выполняет upstream-вызов
DONE         - upstream успешно завершился, result заполнен
FAILED       - слот не получен, ожидание истекло, upstream упал или recovery закрыл зависшую запись
```

Ключевое правило: только `SYNC_REQUEST` в `PENDING` с неистекшим `wait_expires_at` считается live sync waiter и блокирует старт новых async-вызовов.

`SYNC_REQUEST` в `IN_PROGRESS` не должен считаться waiter-ом. Он уже занял `SYNC` slot, и его влияние на async учитывается через `ext_slots.kind='SYNC'` и существующую формулу sync reserve.

## Обязательная модель данных

### Новые или измененные колонки `ext_request_queue`

Добавить:

```text
record_type varchar(24) not null
idempotency_key varchar(160) null
wait_expires_at timestamp with time zone null
```

Рекомендуемые значения `record_type`:

```text
ASYNC_TASK
SYNC_REQUEST
```

Backfill существующих строк:

```text
delivery_mode IN ('CALLBACK', 'POLLING') -> record_type='ASYNC_TASK'
delivery_mode = 'SYNC'                  -> record_type='SYNC_REQUEST'
```

### Целевая интерпретация полей

Для `ASYNC_TASK`:

- `delivery_mode` остается `CALLBACK` или `POLLING`;
- `idempotency_key` не используется; async idempotency продолжает опираться на `client_service + external_id` и будущую `@Idempotent` обвязку из CR003;
- `status` остается `PENDING`, `IN_PROGRESS`, `DONE`, `FAILED`, `DEAD`, `CANCELLED`;
- `available_at` остается временем доступности async-задачи для claim;
- `wait_expires_at` всегда `NULL`.

Для `SYNC_REQUEST`:

- `delivery_mode` всегда `SYNC`;
- `idempotency_key` хранит `Idempotency-Key` sync-запроса и используется для отсечения повторов;
- `callback_delivery_status` всегда `NOT_REQUIRED`;
- `max_attempts` всегда `1`;
- `retryable` на уровне строки остается `FALSE`, потому что manual retry API не работает с sync trace;
- `attempts=0`, если upstream не вызывался;
- `attempts=1`, если upstream был вызван;
- `available_at` не используется для gate и может равняться `created_at`;
- `wait_expires_at` заполнен только в `PENDING`;
- `wait_expires_at=NULL` для `IN_PROGRESS`, `DONE`, `FAILED`.

## Обязательные ограничения

Добавить check constraints или их эквивалент:

```sql
record_type IN ('ASYNC_TASK', 'SYNC_REQUEST')
```

```sql
(record_type = 'ASYNC_TASK' AND delivery_mode IN ('CALLBACK', 'POLLING'))
OR
(record_type = 'SYNC_REQUEST' AND delivery_mode = 'SYNC')
```

```sql
record_type = 'ASYNC_TASK'
OR
status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED')
```

```sql
record_type = 'ASYNC_TASK'
OR
callback_delivery_status = 'NOT_REQUIRED'
```

```sql
record_type = 'ASYNC_TASK'
OR
max_attempts = 1
```

```sql
(record_type = 'SYNC_REQUEST' AND status = 'PENDING' AND wait_expires_at IS NOT NULL)
OR
(record_type = 'SYNC_REQUEST' AND status <> 'PENDING' AND wait_expires_at IS NULL)
OR
record_type = 'ASYNC_TASK'
```

Существующий async idempotency index должен стать async-only:

```sql
CREATE UNIQUE INDEX uq_ext_request_queue_async_idempotency
  ON ext_request_queue (client_service, external_id)
  WHERE record_type = 'ASYNC_TASK'
    AND delivery_mode IN ('CALLBACK', 'POLLING');
```

Для sync request-level idempotency нужен отдельный unique guard:

```sql
CREATE UNIQUE INDEX uq_ext_request_queue_sync_idempotency
  ON ext_request_queue (client_service, idempotency_key)
  WHERE record_type = 'SYNC_REQUEST'
    AND idempotency_key IS NOT NULL;
```

В CR004-T002 нужно явно выбрать политику для sync-запроса без `Idempotency-Key`: отклонять запрос, выполнять без идемпотентности или использовать резервный ключ. Предпочтение для надежного sync API - требовать `Idempotency-Key` для идемпотентного режима.

## Обязательные индексы

Async claim не должен видеть sync lifecycle rows:

```sql
CREATE INDEX idx_ext_request_queue_async_claim
  ON ext_request_queue (priority_weight DESC, available_at ASC, id ASC)
  WHERE record_type = 'ASYNC_TASK'
    AND status = 'PENDING';
```

Sync waiter gate должен проверяться без скана годовой истории:

```sql
CREATE INDEX idx_ext_request_queue_live_sync_waiters
  ON ext_request_queue (wait_expires_at)
  WHERE record_type = 'SYNC_REQUEST'
    AND status = 'PENDING';
```

Recovery зависших sync in-progress строк:

```sql
CREATE INDEX idx_ext_request_queue_sync_in_progress_started
  ON ext_request_queue (started_at)
  WHERE record_type = 'SYNC_REQUEST'
    AND status = 'IN_PROGRESS';
```

Retention/архивация финальных строк:

```sql
CREATE INDEX idx_ext_request_queue_finished_retention
  ON ext_request_queue (finished_at)
  WHERE status IN ('DONE', 'FAILED', 'DEAD', 'CANCELLED');
```

Lookup по external id должен явно учитывать тип записи там, где это важно:

```sql
CREATE INDEX idx_ext_request_queue_record_external_id
  ON ext_request_queue (record_type, external_id, id);
```

## Целевые SQL-правила

### Live sync waiter check

`acquireAsyncSlot` должен блокировать новый async старт, если есть живой sync waiter:

```sql
SELECT 1
FROM ext_request_queue
WHERE record_type = 'SYNC_REQUEST'
  AND status = 'PENDING'
  AND wait_expires_at > :now
LIMIT 1
```

### Async claim

Async dispatcher должен выбирать только `ASYNC_TASK`:

```sql
WHERE record_type = 'ASYNC_TASK'
  AND delivery_mode IN ('CALLBACK', 'POLLING')
  AND status = 'PENDING'
  AND available_at <= :now
```

### Sync waiting insert

Если немедленный sync acquire не получил слот, создается `SYNC_REQUEST`:

```text
record_type = 'SYNC_REQUEST'
delivery_mode = 'SYNC'
status = 'PENDING'
callback_delivery_status = 'NOT_REQUIRED'
wait_expires_at = now + sync_waiter_ttl
idempotency_key = headers.idempotencyKey
attempts = 0
max_attempts = 1
```

Если `idempotency_key` уже существует для того же `client_service`, повторный sync-запрос не должен занимать второй слот и не должен повторно вызывать upstream. После подключения внутренней библиотеки `@Idempotent` replay ответа делает библиотека, а unique index в `ext_request_queue` остается нижним DB-level предохранителем.

### Sync acquire after wait

Когда ожидающий sync получил слот, та же строка переводится:

```text
PENDING -> IN_PROGRESS
wait_expires_at = NULL
started_at = now
attempts = 1
updated_at = now
```

### Sync final success

```text
IN_PROGRESS -> DONE
result = upstream result
finished_at = now
updated_at = now
last_error = NULL
```

### Sync final failure

```text
PENDING или IN_PROGRESS -> FAILED
error = TaskError
finished_at = now
updated_at = now
last_error = error.message
wait_expires_at = NULL
```

Если failure произошел до upstream-вызова, `attempts=0`. Если upstream был вызван, `attempts=1`.

## Варианты создания строки sync-запроса

### Вариант A: строка создается только после неудачного immediate acquire

Flow:

1. Sync сначала пробует взять слот.
2. Если слот есть, поведение остается близким к текущему: upstream вызывается, финальный trace пишется в `ext_request_queue`.
3. Если слота нет, создается `SYNC_REQUEST/PENDING`, который блокирует async.
4. При получении слота эта же строка становится `IN_PROGRESS`, затем `DONE` или `FAILED`.

Плюсы:

- меньше writes для быстрых sync-запросов без ожидания;
- нет короткого overblocking async при каждом sync-запросе;
- ближе к текущей реализации.

Минусы:

- sync-запросы без ожидания не имеют `IN_PROGRESS` lifecycle row;
- есть два code path для sync trace.

### Вариант B: строка создается для каждого sync-запроса до первой попытки acquire

Flow:

1. В начале sync создается `SYNC_REQUEST/PENDING`.
2. Если слот сразу получен, строка быстро переводится в `IN_PROGRESS`.
3. Если слот не получен, строка остается `PENDING` до timeout или successful retry.
4. Финал всегда обновляет эту же строку.

Плюсы:

- одна модель для всех sync-запросов;
- виден полный lifecycle sync-запроса;
- проще аудит и recovery.

Минусы:

- больше writes: минимум `INSERT + UPDATE + UPDATE`;
- каждый sync на короткое время создает live waiter и может консервативно блокировать async;
- нужно особенно аккуратно настроить recovery для `PENDING`.

Предпочтение CR004: начать с варианта A как менее рискованного перехода. Вариант B можно принять позже отдельным решением, если полный lifecycle для каждого sync-запроса важнее минимального вмешательства.

## Recovery и рестарты

Зависшие `SYNC_REQUEST/PENDING` не должны вечно блокировать async.

Обязательные правила:

- async gate всегда проверяет `wait_expires_at > :now`;
- startup/scheduled recovery переводит истекшие `SYNC_REQUEST/PENDING` в `FAILED`;
- recovery не удаляет lifecycle row, если строка уже является sync trace;
- error code для такого случая должен быть стабильным, например `SYNC_WAIT_EXPIRED`.

Пример recovery:

```sql
UPDATE ext_request_queue
SET status = 'FAILED',
    error = CAST(:error AS jsonb),
    finished_at = :now,
    updated_at = :now,
    last_error = :message,
    wait_expires_at = NULL
WHERE record_type = 'SYNC_REQUEST'
  AND status = 'PENDING'
  AND wait_expires_at <= :now
RETURNING id
```

Для `SYNC_REQUEST/IN_PROGRESS` нужен отдельный recovery:

- если JVM умерла после получения слота, строка может остаться `IN_PROGRESS`;
- slot lease освобождается через существующий `reapExpiredLeases`;
- sync trace нужно закрыть в `FAILED` после отдельного `sync-in-progress-timeout`;
- timeout должен быть больше максимального допустимого времени upstream-вызова и lease/retry особенностей.

Пример условия:

```sql
WHERE record_type = 'SYNC_REQUEST'
  AND status = 'IN_PROGRESS'
  AND started_at <= :staleBefore
```

Рекомендуемый error code: `SYNC_IN_PROGRESS_RECOVERED`.

## Производительность и retention

Свободные слоты продолжают вычисляться только по `ext_slots`, поэтому размер `ext_request_queue` не влияет на подсчет slot capacity.

Размер `ext_request_queue` влияет на:

- live sync waiter check;
- async claim;
- dashboard/statistics;
- lookup по `externalId`;
- retention cleanup.

Обязательные условия производительности:

- live waiter check использует partial index `idx_ext_request_queue_live_sync_waiters`;
- async claim использует partial index `idx_ext_request_queue_async_claim`;
- статистика не сканирует всю годовую историю без необходимости;
- финальные строки подлежат retention или архивированию;
- autovacuum должен справляться с частыми update `PENDING -> IN_PROGRESS -> DONE/FAILED`.

Рекомендуемая retention-политика:

- `SYNC_REQUEST DONE/FAILED`: хранить 30-90 дней, если нет аудиторского требования на год;
- `ASYNC_TASK DONE/FAILED/DEAD/CANCELLED`: хранить по требованиям к повторному чтению результата и аудиту;
- `SYNC_REQUEST PENDING/IN_PROGRESS`: не должны жить дольше recovery windows;
- старые финальные строки при необходимости переносить в архивную таблицу или партиционировать `ext_request_queue` по `created_at`.

## Влияние на публичное поведение

Публичный REST/OpenAPI-контракт не должен измениться.

Внутренние изменения:

- `GET` async endpoints должны продолжать видеть только `ASYNC_TASK`;
- sync trace lookup, если используется для диагностики, должен по умолчанию возвращать финальные `SYNC_REQUEST DONE/FAILED`, а не активные `PENDING/IN_PROGRESS`, если это не отдельный dashboard view;
- dashboard может получить отдельные метрики:
  - live sync waiters;
  - active sync requests;
  - recovered expired sync waiters;
  - recovered stale sync in-progress.

## Переход и rollback в dev-phase

Так как система не в production, целевой переход может быть прямым:

1. Зафиксировать точную целевую state machine и SQL-инварианты в `plan_T002.md`.
2. Обновить Liquibase: добавить `record_type`, `idempotency_key`, `wait_expires_at`, constraints и partial indexes.
3. Перевести код `SlotRepository`/`SlotManager` на `SYNC_REQUEST` в `ext_request_queue`.
4. Добавить recovery для зависших `SYNC_REQUEST/PENDING` и `SYNC_REQUEST/IN_PROGRESS`.
5. Обновить API/dashboard/statistics фильтры.
6. Запустить быстрые и PostgreSQL integration/concurrency tests.
7. Удалить старый код работы с `ext_sync_waiters`.
8. Удалить таблицу `ext_sync_waiters` отдельным Liquibase changeset в рамках CR004.
9. Обновить архитектурную документацию и ADR.

Feature flag `external-gateway.sync-waiter-store` можно добавить только если он реально упрощает поэтапную разработку или тестирование двух вариантов. Для CR004 он не является обязательным требованием.

Rollback в dev-phase:

- до удаления `ext_sync_waiters` можно откатить код и оставить старую таблицу неиспользованной;
- после удаления таблицы основной rollback - `git revert` и пересоздание dev/Testcontainers БД;
- поддержка обратимой миграции для production downtime-free rollback не требуется, пока система остается в разработке;
- если до появления production-окружения CR004 не будет завершен, статус миграции нужно пересмотреть перед rollout.

## Очередь запуска

| Порядок | Задача | Приоритет | Результат |
| --- | --- | --- | --- |
| 1 | CR004-T001: инвентаризация текущих sync/async запросов и индексов | P0 | Есть точная карта текущих SQL, фильтров, статусов и API/dashboard consumers. |
| 2 | CR004-T002: stage plan и целевая модель state machine | P0 | Зафиксирован выбранный вариант A или B, статусы, constraints, recovery и rollback. |
| 3 | CR004-T003: Liquibase migration для `record_type`, `idempotency_key`, `wait_expires_at`, constraints и indexes | P0 | Схема поддерживает новую модель `SYNC_REQUEST`; миграция рассчитана на dev-phase без production downtime требований. |
| 4 | CR004-T004: адаптация repository contracts и domain model | P0 | Доменные порты поддерживают sync lifecycle row без удаления старого режима. |
| 5 | CR004-T005: прямая реализация request-queue sync waiter store | P0 | Sync waiting пишет/обновляет `SYNC_REQUEST`, async gate читает новую модель; feature flag не обязателен. |
| 6 | CR004-T006: recovery истекших `SYNC_REQUEST` | P0 | Startup/scheduled recovery закрывает зависшие `PENDING` и `IN_PROGRESS`. |
| 7 | CR004-T007: фильтрация API/dashboard/statistics | P1 | Публичные API не показывают служебные активные sync rows, dashboard получает новые метрики. |
| 8 | CR004-T008: PostgreSQL parity и concurrency tests | P0 | Доказано, что async не забирает слот у live sync waiter, а истекший waiter не блокирует async. |
| 9 | CR004-T009: удаление `ext_sync_waiters` и старого кода | P1 | После успешных проверок старая таблица и старый код удалены в рамках dev-phase CR004. |
| 10 | CR004-T010: rollout-документация и retention policy | P1 | Описаны dev-phase rollback, retention, autovacuum/partitioning considerations. |
| 11 | CR004-T011: архитектурная документация и ADR | P0 | Обновлены data/state, sequence diagrams, operations и ADR. |
| 12 | CR004-T012: финальная проверка | P0 | `mvn test` и PostgreSQL integration checks зафиксированы в журнале. |

## Детализация задач

### CR004-T001: инвентаризация текущих sync/async запросов и индексов

Цель: перед изменением схемы зафиксировать все места, которые читают или пишут `ext_sync_waiters` и `ext_request_queue`.

Объем работ:

- Выписать SQL из `PostgresSlotRepository`, `PostgresAsyncTaskRepository`, `MemorySlotRepository` и связанных tests.
- Выписать текущие индексы `ext_request_queue` и `ext_sync_waiters`.
- Зафиксировать consumers `findRequestTracesByExternalId`, async polling, dashboard health/stats.
- Определить, где нужен `record_type` filter.

Критерии приемки:

- Создан `inventory_T001.md`.
- Для каждого запроса указано, как он должен измениться в целевой модели.

### CR004-T002: stage plan и целевая модель state machine

Цель: выбрать окончательный вариант реализации перед кодом.

Объем работ:

- Выбрать вариант A или B.
- Утвердить статусную модель `SYNC_REQUEST`.
- Утвердить error codes recovery.
- Утвердить, нужен ли временный feature flag для разработки и тестирования.
- Утвердить dev-phase rollback path.
- Описать влияние на memory mode.

Критерии приемки:

- Создан `plan_T002.md`.
- План согласован с ADR-002, ADR-003, ADR-004 и ADR-010.

### CR004-T003: Liquibase migration для `record_type`, `idempotency_key`, `wait_expires_at`, constraints и indexes

Цель: подготовить схему к новой модели без переключения поведения.

Объем работ:

- Добавить `record_type`, `idempotency_key` и `wait_expires_at`.
- Backfill существующих строк.
- Пересоздать async claim index как async-only.
- Пересоздать async idempotency unique index как async-only через `record_type`.
- Добавить live sync waiter partial index.
- Добавить sync idempotency unique index.
- Добавить recovery/retention indexes.
- Добавить constraints.

Критерии приемки:

- PostgreSQL smoke проверяет новые колонки, constraints и индексы.
- Существующий runtime с default flag продолжает работать.

### CR004-T004: адаптация repository contracts и domain model

Цель: добавить доменную модель sync lifecycle без протекания SQL-деталей в сервисы.

Объем работ:

- Ввести модель `SyncRequestLifecycle` или аналог.
- Разделить async task operations и sync request lifecycle operations.
- Решить, остается ли sync lifecycle в `AsyncTaskRepository` или выделяется отдельный порт.
- Обновить memory и postgres реализации.

Критерии приемки:

- Sync lifecycle не выглядит как async task в сервисном коде.
- Async endpoints продолжают работать только с async tasks.

### CR004-T005: прямая реализация request-queue sync waiter store

Цель: заменить `ext_sync_waiters` на `ext_request_queue` в runtime-коде без production feature flag.

Объем работ:

- Перевести регистрацию ожидающего sync-запроса на создание или обновление `SYNC_REQUEST`.
- Перевести `acquireAsyncSlot` на live waiter check из `ext_request_queue`.
- Для request-queue режима обновлять `SYNC_REQUEST` вместо удаления waiter.
- Удалить зависимость service flow от `waiterId`, если она больше не нужна.
- Добавить feature flag только если без него невозможно удобно провести тестирование переходного состояния.

Критерии приемки:

- Новый режим проходит расширенные tests.
- Новая реализация не меняет публичный HTTP API.

### CR004-T006: recovery истекших `SYNC_REQUEST`

Цель: исключить вечную блокировку async и вечные `IN_PROGRESS` sync rows.

Объем работ:

- Добавить recovery для `PENDING` с истекшим `wait_expires_at`.
- Добавить recovery для stale `IN_PROGRESS`.
- Добавить startup recovery.
- Добавить scheduled recovery.
- Добавить dashboard metrics для recovery count.

Критерии приемки:

- JVM restart после создания `SYNC_REQUEST/PENDING` не блокирует async дольше TTL.
- JVM restart после `IN_PROGRESS` не оставляет вечный незавершенный trace.

### CR004-T007: фильтрация API/dashboard/statistics

Цель: не показать служебные строки там, где потребители ждут только async tasks или финальные sync traces.

Объем работ:

- Проверить async polling, cancel, retry, lookup by external id.
- Обновить stats на async-only фильтры.
- Добавить dashboard view для live sync waiters и active sync, если нужно.
- Проверить OpenAPI impact: публичный контракт не меняется.

Критерии приемки:

- `SYNC_REQUEST/PENDING` и `SYNC_REQUEST/IN_PROGRESS` не возвращаются async API.
- Sync diagnostic views явно документируют, показывают ли активные sync lifecycle rows.

### CR004-T008: PostgreSQL parity и concurrency tests

Цель: доказать корректность новой модели под конкурентной нагрузкой.

Объем работ:

- Проверить, что live `SYNC_REQUEST/PENDING` блокирует async acquire.
- Проверить, что expired `SYNC_REQUEST/PENDING` не блокирует async acquire.
- Проверить, что при освобождении одного слота из 10 sync waiters lease получает ровно один sync.
- Проверить, что async dispatcher не claim-ит `SYNC_REQUEST/PENDING`.
- Проверить startup recovery.
- Проверить rollback flag.
- Проверить query plan или косвенный performance guard для live waiter check на фоне большого числа финальных строк.

Критерии приемки:

- `mvn test` проходит.
- `mvn verify -Pintegration-tests` проходит для измененных PostgreSQL сценариев.

### CR004-T009: удаление `ext_sync_waiters` и старого кода

Цель: завершить dev-phase переход после успешной проверки новой модели.

Объем работ:

- Удалить методы и SQL, которые больше работают с `ext_sync_waiters`.
- Удалить `ext_sync_waiters` из Liquibase целевой схемы или добавить отдельный drop changeset, если схема уже создавалась предыдущими changeset.
- Обновить smoke tests на отсутствие `ext_sync_waiters` в целевой схеме.
- Обновить repository contract tests на новую модель.

Критерии приемки:

- Production-код больше не читает и не пишет `ext_sync_waiters`.
- Новая схема стартует на чистой БД без `ext_sync_waiters`.
- Старые тесты заменены request-queue equivalents.

### CR004-T010: rollout-документация и retention policy

Цель: подготовить эксплуатационные правила для будущего production-окружения.

Объем работ:

- Описать dev-phase rollback.
- Описать retention final rows.
- Описать autovacuum/partitioning considerations.
- Описать метрики и alert conditions.

Критерии приемки:

- Operations docs содержат правила для `SYNC_REQUEST/PENDING`, `SYNC_REQUEST/IN_PROGRESS` и retention.

### CR004-T011: архитектурная документация и ADR

Цель: синхронизировать архитектуру с новой моделью.

Объем работ:

- Обновить `04-data-and-state.md`.
- Обновить sync/async sequence diagrams.
- Обновить C4 components, если меняются ответственности repositories.
- Обновить operations metrics.
- Добавить ADR о переносе sync waiters в `ext_request_queue`.

Критерии приемки:

- Архитектурные документы больше не описывают `ext_sync_waiters` как целевую таблицу после завершения миграции.
- ADR фиксирует причины, последствия и rollback considerations.

### CR004-T012: финальная проверка

Цель: подтвердить поведение после завершения этапов.

Объем работ:

- Запустить `mvn test`.
- Запустить `mvn verify -Pintegration-tests`.
- Зафиксировать результат в `execution-progress.md`.
- Зафиксировать итоговое решение по удалению или сохранению `ext_sync_waiters`.

Критерии приемки:

- Все P0 задачи закрыты.
- Проверки зафиксированы.
- Оставшиеся P1/P2 задачи либо закрыты, либо явно отложены.

## Общие правила приемки CR004

- Перед реализацией каждого этапа `CR004-TXXX` создается или обновляется `plan_TXXX.md`.
- После реализации каждого этапа создается `review_TXXX.md` через senior architect review.
- Публичный HTTP API не меняется без отдельного решения.
- Async claim и async idempotency всегда фильтруются по `record_type='ASYNC_TASK'`.
- Sync waiter gate всегда фильтруется по `record_type='SYNC_REQUEST'`, `status='PENDING'` и `wait_expires_at > now`.
- Зависшие `SYNC_REQUEST` не должны блокировать async дольше TTL/recovery window.
- В dev-phase `ext_sync_waiters` может быть удалена в рамках CR004 после успешных tests и review.
- Все новые документы и комментарии пишутся на русском языке.
