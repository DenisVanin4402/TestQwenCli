# CR001: задачи по тестовому покрытию перед рефакторингом

## Назначение

Этот список разбивает план `test-coverage-plan.md` на последовательные задачи для запуска в работу. Задачи идут в рекомендуемом порядке: сначала инфраструктура и PostgreSQL-контур, затем e2e, затем расширение контрактов и вспомогательных слоев. Performance- и нагрузочные проверки намеренно исключены.

Детальный перечень недостающих тестов по текущей разработанной функциональности находится в `missing-tests-plan.md`. `work-items.md` остается верхнеуровневой очередью, а `missing-tests-plan.md` фиксирует конкретные сценарии, классы тестов, контуры запуска и порядок закрытия пробелов.

## Очередь запуска

| Порядок | Задача | Приоритет | Результат |
| --- | --- | --- | --- |
| 1 | CR001-T001: инфраструктура integration-тестов | P0 | Docker-зависимые тесты можно запускать отдельно от `mvn test`. |
| 2 | CR001-T002: PostgreSQL smoke и Liquibase | P0 | Подтвержден старт приложения и схема БД в postgres mode. |
| 3 | CR001-T003: test support для PostgreSQL/e2e | P0 | Есть общие фабрики, очистка БД и ожидания async-событий. |
| 4 | CR001-T004: контракт `SlotRepository` | P0 | Memory и PostgreSQL реализации проверяются одинаковым поведением. |
| 5 | CR001-T005: контракт `AsyncTaskRepository` | P0 | Очередь, idempotency, retry/cancel и sync trace закреплены тестами. |
| 6 | CR001-T006: контракт `CallbackDeliveryRepository` | P0 | Callback lifecycle закреплен для PostgreSQL и memory. |
| 7 | CR001-T007: e2e happy path sync и async polling | P0 | Реальные HTTP-запросы проходят через приложение и PostgreSQL. |
| 8 | CR001-T008: e2e async callback | P0 | Callback отправляется на тестовый HTTP endpoint с нужным телом и заголовками. |
| 9 | CR001-T009: e2e негативные API-сценарии | P0 | Ошибочные sync/async сценарии закреплены в postgres mode. |
| 10 | CR001-T010: LISTEN/NOTIFY integration | P1 | PostgreSQL notification path проверен отдельно от polling. |
| 11 | CR001-T011: контракт `HttpCallbackClient` | P1 | HTTP client проверен против реального тестового сервера. |
| 12 | CR001-T012: расширение controller/API тестов в memory mode | P1 | Быстрый `mvn test` ловит основные HTTP-регрессии без Docker. |
| 13 | CR001-T013: тесты `dashboard-backend` | Исключена | Не выполняется в рамках CR001 по решению от 2026-06-12. |
| 14 | CR001-T014: OpenAPI и error contract | P1 | Документированный контракт сверяется с фактическим API. |
| 15 | CR001-T015: functional-тесты scheduler-слоя | P2 | Scheduler logic проверяется без ожидания реального времени. |
| 16 | CR001-T016: concurrency correctness | P2 | `PostgresConcurrencyIT` закрепляет отсутствие дублей обработки и сохранение sync reserve. |
| 17 | CR001-T017: configuration binding и test output hygiene | P2 | Конфигурация и тестовый вывод становятся устойчивыми к рефакторингу. |
| 18 | CR001-T018: static UI и browser smoke | P2 | Dashboard UI проверяется минимальным функциональным smoke-тестом. |

## Детализация задач

### CR001-T001: инфраструктура integration-тестов

Цель: отделить быстрые unit/Spring тесты от Docker-зависимых integration/e2e тестов.

Объем работ:
- Добавить зависимости `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`.
- Настроить Maven Failsafe для `*IT`.
- Добавить профиль `integration-tests` или аналогичный явный запуск.
- Оставить `mvn test` быстрым и без Docker.
- Задокументировать команды запуска.

Критерии приемки:
- `mvn test` проходит без запуска контейнеров.
- `mvn verify -Pintegration-tests` запускает `*IT`.
- Пустой integration-набор не ломает reactor.

### CR001-T002: PostgreSQL smoke и Liquibase

Цель: доказать, что postgres mode приложения стартует на реальной PostgreSQL БД.

Объем работ:
- Поднять `PostgreSQLContainer`.
- Передать `external-gateway.postgres.jdbc-url`, username, password, schema через `@DynamicPropertySource`.
- Запустить Spring context с `external-gateway.repository.type=postgres`.
- Проверить создание schema, таблиц, индексов, foreign key и check/unique constraints.
- Проверить начальные строки `ext_slots`.

Критерии приемки:
- Integration-тест падает при сломанном changelog.
- Проверяются минимум `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`.
- После теста контейнер и Spring context корректно закрываются.

### CR001-T003: test support для PostgreSQL/e2e

Цель: убрать дублирование и снизить риск flaky-тестов перед расширением набора.

Объем работ:
- Создать helpers для `PostgreSQLContainer` и динамических properties.
- Добавить очистку таблиц между тестами.
- Добавить фабрики sync/async/callback запросов.
- Добавить управляемые ожидания async-состояний без произвольных `Thread.sleep`.
- Добавить фиксированный `Clock` там, где это возможно без переписывания production-кода.

Критерии приемки:
- Новые PostgreSQL/e2e тесты переиспользуют support-код.
- Очистка БД не зависит от порядка тестов.
- Ожидания имеют понятный timeout и диагностическое сообщение.

### CR001-T004: контракт `SlotRepository`

Цель: закрепить одинаковое поведение memory и PostgreSQL реализаций слотов.

Объем работ:
- Вынести общий behavior suite для `SlotRepository`.
- Покрыть sync acquire до лимита `total`.
- Покрыть async reserve для sync и live sync waiter.
- Покрыть `release`, `heartbeat`, защиту `lease_id`.
- Покрыть `reapExpiredLeases`.
- Добавить параллельный захват слотов без выдачи одного slot двум владельцам.

Критерии приемки:
- Один набор сценариев проходит для `MemorySlotRepository`.
- Тот же набор сценариев проходит для `PostgresSlotRepository`.
- Ошибки PostgreSQL SQL/lock semantics ловятся integration-тестами.

### CR001-T005: контракт `AsyncTaskRepository`

Цель: закрепить поведение async-очереди, idempotency и request trace.

Объем работ:
- Проверить submit `PENDING` задачи с JSONB payload.
- Проверить повторный submit с тем же `clientService + externalId`.
- Проверить idempotency conflict для payload, priority и deliveryMode.
- Проверить `claimNextPending`: priority, `available_at`, отсутствие повторного claim.
- Проверить `complete`, `failTransient`, `returnClaimToPending`, `cancel`, `retry`.
- Проверить `recordSyncTrace` с несколькими SYNC trace на один `externalId`.
- Проверить `stats` без смешивания sync trace и async queue.

Критерии приемки:
- Memory и PostgreSQL реализации проходят одинаковые ключевые сценарии.
- PostgreSQL тесты реально проверяют JSONB serialization/deserialization.
- Частичный unique index для async idempotency проверен поведением.

### CR001-T006: контракт `CallbackDeliveryRepository`

Цель: закрепить lifecycle callback-доставки на уровне репозитория.

Объем работ:
- Проверить `createPending` и `createDead`.
- Проверить upsert по unique `task_id`.
- Проверить `claimNextPending`: `DELIVERING`, `attempts`, обновление `eventId`.
- Проверить `markDelivered`, `markRetryOrDead`, `markDead`.
- Проверить `recoverTimedOutDeliveries` в `RETRY` и `DEAD`.
- Проверить `stats`.

Критерии приемки:
- PostgreSQL ограничения callback-таблицы проверяются через поведение.
- Состояние task и delivery не расходится в общих flow-тестах.
- Повторный claim одной delivery не происходит.

### CR001-T007: e2e happy path sync и async polling

Цель: проверить публичный HTTP API на реальном порту с PostgreSQL backend.

Объем работ:
- Запустить приложение с `webEnvironment = RANDOM_PORT`.
- Выполнить `POST /v1/external/sync`.
- Проверить HTTP `200`, response body и sync trace в PostgreSQL.
- Выполнить `POST /v1/external/async` в polling mode.
- Дождаться обработки dispatcher.
- Проверить `GET /v1/external/async/{taskId}` со статусом `DONE`.

Критерии приемки:
- Тест использует реальный HTTP client, не `MockMvc`.
- Проверяются и HTTP-ответы, и persisted state.
- Сценарий стабильно проходит без performance-допущений.

### CR001-T008: e2e async callback

Цель: проверить полный callback flow через реальный HTTP endpoint.

Объем работ:
- Поднять тестовый HTTP endpoint для приема callback.
- Настроить allow-list callback URL для тестового client service.
- Создать async задачу в callback mode.
- Дождаться `DONE` задачи и callback delivery.
- Проверить callback body, `X-Callback-Attempt`, `X-Request-Id`.
- Проверить финальные статусы `DELIVERED`.

Критерии приемки:
- Callback действительно проходит по HTTP.
- Повторная доставка не происходит в happy path.
- В PostgreSQL совпадают task status и callback delivery status.

### CR001-T009: e2e негативные API-сценарии

Цель: закрепить внешние ошибки API в postgres mode.

Объем работ:
- Sync при занятых слотах: `429`, `Retry-After`, trace `FAILED`.
- Malformed JSON: стабильный `INVALID_REQUEST`.
- Validation error: стабильный `VALIDATION_ERROR`.
- Async idempotency conflict: `409`.
- Cancel pending task и повторный cancel.
- Retry для неподходящего статуса: state conflict.

Критерии приемки:
- Проверяются status code, error code, retryable, requestId и details.
- Проверяется persisted state для сценариев, где он должен меняться.
- Негативные сценарии не зависят от порядка запуска.

### CR001-T010: LISTEN/NOTIFY integration

Цель: проверить PostgreSQL wait strategy с notification path.

Объем работ:
- Запустить postgres mode с `sync-acquire-wait-mode=listen_notify`.
- Проверить, что ожидающий sync acquire просыпается после release.
- Проверить fallback при потерянной notification.
- Проверить start/stop listener без утечки фоновых потоков.

Критерии приемки:
- LISTEN/NOTIFY реально проходит через PostgreSQL.
- Тест не зависит от длинного polling interval.
- В тестовом выводе нет ожидаемого WARN от недоступного `DataSource`.

### CR001-T011: контракт `HttpCallbackClient`

Цель: отдельно проверить низкоуровневый HTTP callback client.

Объем работ:
- Проверить `POST` method, JSON body, target URI.
- Проверить `X-Callback-Attempt`.
- Проверить `X-Request-Id`, когда requestId задан.
- Проверить отсутствие пустого `X-Request-Id`.
- Проверить обработку `2xx`.
- Проверить `4xx/5xx`, connect/read timeout как вход для retry/dead dispatcher flow.

Критерии приемки:
- Тест использует тестовый HTTP server, не mock `RestClient`.
- Ошибки сети воспроизводимы и не требуют внешнего доступа.
- Dispatcher flow покрывает retry/dead последствия HTTP-ошибок.

### CR001-T012: расширение controller/API тестов в memory mode

Цель: усилить быстрый набор `mvn test` без Docker.

Объем работ:
- Дополнить `ExternalAsyncControllerTest`: not found, чужой `X-Client-Service`, malformed JSON, missing headers.
- Проверить polling response после `DONE`.
- Проверить manual retry для `DEAD` retryable задачи.
- Дополнить `ExternalSyncControllerTest`: upstream timeout, interruption/error, повторные SYNC trace с одинаковым `externalId`.
- Проверить `X-Request-Id` в ошибках.
- Проверить стабильность JSON field names публичных responses.

Критерии приемки:
- Новые тесты остаются в `mvn test`.
- Не используются реальные sleep дольше минимально необходимого.
- Ошибки controller layer ловятся до запуска Docker-набора.

### CR001-T013: тесты `dashboard-backend`

Статус: не выполняется в рамках CR001 по решению от 2026-06-12. Очередь работ после CR001-T012 переходит сразу к CR001-T014.

Цель: дать dashboard backend собственный тестовый контур в своем модуле.

Объем работ:
- Добавить test dependencies в `dashboard-backend`, если нужно.
- Покрыть `DashboardMetricsRegistry`: счетчики, active requests, rate window, percentiles, reset.
- Покрыть `DashboardLoadRunner`: start/stop/update profile, генерация sync/async запросов, обработка исключений клиента.
- Покрыть `DashboardSnapshotService`.
- Покрыть `DashboardController`: snapshot, health, profile update validation, simulation settings validation, reset.

Критерии приемки:
- `dashboard-backend` больше не выводит `No tests to run`.
- Controller-тесты проверяют HTTP status и JSON body.
- Тесты не запускают настоящую функциональную нагрузку.

### CR001-T014: OpenAPI и error contract

Цель: связать документацию API с фактическим поведением приложения.

Объем работ:
- Проверить `/v3/api-docs` на наличие основных paths.
- Проверить схемы основных responses и errors.
- Сверить обязательные поля с `docs/openapi/*.yaml`.
- Добавить snapshot-style проверку только стабильных частей OpenAPI.

Критерии приемки:
- Изменение публичного пути или поля ломает тест.
- Тест не чувствителен к порядку JSON-полей.
- Документы OpenAPI и generated API не расходятся по базовым контрактам.

### CR001-T015: functional-тесты scheduler-слоя

Цель: проверить scheduler orchestration без performance-замеров.

Объем работ:
- Проверить `ExternalAsyncDispatcherScheduler`.
- Проверить `CallbackDeliveryDispatcherScheduler`.
- Проверить `SlotLeaseReaperScheduler`.
- Проверить запись метрик для успешных итераций.
- Проверить disabled flags.
- Использовать управляемый clock/scheduler или прямой вызов tick-методов, если это потребует небольшой тестируемой декомпозиции.

Критерии приемки:
- Scheduler behavior проверяется без ожидания реального расписания.
- Метрики обновляются только при положительном count.
- Disabled scheduler не вызывает dispatcher/reaper.

### CR001-T016: concurrency correctness

Статус: выполнена в рамках CR001 2026-06-12. Добавлен `PostgresConcurrencyIT` в Docker-зависимый Failsafe-контур.

Цель: проверить корректность параллельной обработки без performance-метрик.

Объем работ:
- Два dispatcher instance с одной PostgreSQL БД не обрабатывают одну задачу дважды.
- Две callback worker группы не доставляют один callback дважды.
- Конкурентный async submit с одним idempotency key создает одну запись.
- Конкурентные sync и async lease не нарушают reserve для sync.

Критерии приемки:
- Проверяется корректность результата, не скорость.
- Тесты имеют ограниченные timeouts и диагностические сообщения.
- Нет случайных зависимостей от порядка thread scheduling.

Результат:
- Два `ExternalAsyncDispatcherImpl` поверх одной PostgreSQL БД завершают одну pending-задачу ровно один раз.
- Две `CallbackDeliveryDispatcherImpl` worker-группы доставляют одну pending callback delivery ровно один раз.
- 12 конкурентных async submit с одним `clientService + externalId` создают одну строку и возвращают согласованный `taskId`.
- Ожидающий sync acquire забирает освобожденный slot, а конкурентные async lease attempts не нарушают reserve для sync.

### CR001-T017: configuration binding и test output hygiene

Цель: закрепить конфигурацию и убрать шум, который мешает видеть реальные падения.

Объем работ:
- Проверить defaults `ExternalGateway*Properties`.
- Проверить parsing duration values из `.properties`.
- Проверить conditional beans для memory/postgres.
- Проверить enabled/disabled scheduler flags.
- Вынести проверку отсутствия `DataSource` и Liquibase в memory mode в отдельный configuration test.
- Убрать ожидаемый WARN из `PostgresSlotNotificationConfigurationTest`.
- Настроить Mockito agent или убрать inline-mocking там, где он не нужен.

Критерии приемки:
- `mvn test` не содержит ожидаемых WARN из фоновых потоков.
- Mockito dynamic agent warning устранен или документирован как отдельный технический долг.
- Ошибка в property binding ломает быстрый тест.

### CR001-T018: static UI и browser smoke

Цель: минимально проверить dashboard UI как пользовательскую поверхность.

Объем работ:
- Проверить `/dashboard/index.html` и root элементы.
- Поднять приложение локально или в e2e-контексте.
- Открыть dashboard в браузерном smoke-тесте.
- Дождаться `/dashboard/api/snapshot`.
- Изменить simulation settings.
- Выполнить start/stop функциональной нагрузки без проверки throughput/latency.

Критерии приемки:
- UI smoke проверяет реальные browser interactions.
- Не проверяются FPS, throughput, latency under load.
- Тест стабильно завершается и останавливает запущенные процессы.

## Общие правила приемки CR001

- Все новые документы и комментарии написаны на русском языке.
- `mvn test` остается обязательной проверкой для каждого изменения.
- Docker-зависимые тесты запускаются отдельной командой.
- Performance-тесты не добавляются в рамках CR001.
- Перед большим рефакторингом должны быть завершены все P0 задачи.
