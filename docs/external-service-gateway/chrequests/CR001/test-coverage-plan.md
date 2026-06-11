# План тестового покрытия перед большим рефакторингом

## Цель

Перед рефакторингом нужно зафиксировать поведение external-service-gateway на всех практичных уровнях: unit, Spring integration, PostgreSQL/Testcontainers, HTTP-контракты, callback-доставка и dashboard. Нагрузочные и performance-тесты в этот план не входят.

## Текущее состояние

- `mvn test` проходит: 110 тестов, 0 failures, 0 errors, 0 skipped.
- `mvn verify -Pintegration-tests` проходит: 110 Surefire-тестов и 48 Failsafe integration-тестов, 0 failures, 0 errors, 0 skipped.
- Собственные тесты есть только в модуле `test-qwen-cli-app`.
- `dashboard-backend` и `dashboard-ui` не имеют `src/test` и в reactor выводят `No tests to run`.
- Текущий быстрый набор покрывает in-memory бизнес-логику: слоты, async submit, idempotency, retry/dead переходы, callback lifecycle, scheduler orchestration, controller/API contracts и OpenAPI/error contract.
- PostgreSQL/Testcontainers-контур покрывает Liquibase, repository contracts, e2e sync/async/callback flows, негативные API-сценарии, LISTEN/NOTIFY, row locking, JSONB-маппинг и concurrency correctness.
- HTTP-проверки включают быстрые `@SpringBootTest` + `MockMvc` сценарии в memory mode и e2e-сценарии на реальном HTTP-порту в PostgreSQL mode.
- Testcontainers, `PostgreSQLContainer` и loopback HTTP server используются в выделенном Docker-зависимом контуре; внешний сетевой доступ тестам не нужен.

## Риски текущего покрытия

- Большой рефакторинг persistence-слоя теперь ловится Docker-зависимыми контрактами `PostgresSlotRepository`, `PostgresAsyncTaskRepository`, `PostgresCallbackDeliveryRepository` и Liquibase smoke, но требует запуска `mvn verify -Pintegration-tests`, а не только `mvn test`.
- Callback HTTP-клиент проверяется против реального loopback HTTP endpoint; retry/dead последствия HTTP-ошибок закреплены dispatcher flow-тестом.
- Dashboard backend не имеет локальных unit/controller тестов, хотя содержит REST API, генератор функциональной нагрузки и метрики; риск принят, CR001-T013 исключена из работ по решению от 2026-06-12.
- OpenAPI YAML в `docs/openapi` сверяется с generated `/v3/api-docs` быстрым MockMvc-тестом по стабильным paths, параметрам, status codes, schema refs и публичным полям gateway API; callback YAML сверяется с сериализуемым `CallbackPayload`.
- Scheduler-слой проверяется быстрым functional-набором без ожидания реального расписания: async/callback dispatch ticks, callback recovery, slot lease reaper, enabled/disabled flags и запись dashboard-метрик только при положительном count.
- PostgreSQL concurrency correctness проверяется `PostgresConcurrencyIT`: два async dispatcher instance, две callback worker группы, конкурентный idempotent submit и sync reserve против конкурентных async lease attempts.
- Конкурентные тесты используют bounded `CountDownLatch`, `Future.get(timeout)` и диагностические сообщения; при дальнейшем расширении набора стоит вынести общие concurrent helpers, чтобы не дублировать обвязку.
- `PostgresSlotNotificationConfigurationTest` создает listener с недоступным `DataSource`, из-за чего в `mvn test` появляется ожидаемый WARN из фонового потока. Это не падение, но шумит и может скрывать реальные проблемы.
- Mockito в текущем запуске предупреждает о dynamic agent loading. Перед обновлением JDK/Mockito нужно явно настроить mockito agent или убрать inline-mocking там, где он не нужен.

## Рекомендуемая структура тестов

- Быстрые unit и Spring/MockMvc тесты остаются в `mvn test`.
- Docker-зависимые проверки вынести в `*IT` и запускать через Maven Failsafe: `mvn verify -Pintegration-tests` или аналогичный профиль.
- Для PostgreSQL добавить `org.springframework.boot:spring-boot-testcontainers`, `org.testcontainers:junit-jupiter` и `org.testcontainers:postgresql` без явных версий, через Spring Boot BOM.
- Для HTTP callback e2e выбрать один инструмент: WireMock/MockWebServer для JVM-only тестов или Testcontainers с легким HTTP-сервером, если нужен сетевой сценарий ближе к production.
- Общие фабрики запросов, фиксированный `Clock`, очистку БД и ожидания асинхронных событий вынести в `src/test/java/.../support`.

## План работ

### P0: блокирующие тесты перед рефакторингом

1. Добавить Testcontainers smoke для PostgreSQL profile.
   - Поднять `postgres:16` или совместимую версию через `PostgreSQLContainer`.
   - Передать `external-gateway.postgres.*` через `@DynamicPropertySource`.
   - Проверить старт Spring context с `external-gateway.repository.type=postgres`.
   - Проверить, что Liquibase создает schema, `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`, индексы и ограничения.

2. Покрыть `PostgresSlotRepository` теми же контрактами, что `MemorySlotRepository`.
   - `acquireSyncSlot` заполняет максимум `total` слотов.
   - `acquireAsyncSlot` оставляет reserve для sync и учитывает live sync waiter.
   - `release` и `heartbeat` защищены `lease_id`.
   - `reapExpiredLeases` освобождает истекшие lease.
   - Параллельный захват не выдает один слот двум владельцам.

3. Покрыть `PostgresAsyncTaskRepository` контрактными integration-тестами.
   - submit создает `PENDING` задачу с корректным payload JSONB.
   - повторный submit с тем же `clientService + externalId` возвращает существующую задачу.
   - повторный submit с другим payload/priority/deliveryMode возвращает idempotency conflict.
   - `claimNextPending` выбирает HIGH перед LOW и не берет future `available_at`.
   - `complete`, `failTransient`, `returnClaimToPending`, `cancel`, `retry` меняют статусы и поля времени корректно.
   - `recordSyncTrace` допускает несколько SYNC trace с одним `externalId`, не конфликтуя с async idempotency.
   - `stats` считает только async-режимы и не смешивает sync trace с очередью.

4. Покрыть `PostgresCallbackDeliveryRepository`.
   - `createPending` и `createDead` создают delivery с корректным payload.
   - повторное создание для одной задачи обновляет запись по unique `task_id`.
   - `claimNextPending` выставляет `DELIVERING`, увеличивает `attempts`, обновляет `eventId`.
   - `markDelivered`, `markRetryOrDead`, `markDead` соблюдают allowed state transitions.
   - `recoverTimedOutDeliveries` переводит зависшие доставки в `RETRY` или `DEAD`.
   - `stats` считает статусы и oldest pending timestamp.

5. Добавить e2e happy path в PostgreSQL mode.
   - Поднять приложение на random port с Testcontainers PostgreSQL.
   - `POST /v1/external/sync` возвращает `SUCCEEDED` и пишет sync trace в PostgreSQL.
   - `POST /v1/external/async` создает задачу, dispatcher обрабатывает ее, polling endpoint возвращает `DONE`.
   - Для callback mode поднять тестовый HTTP endpoint, дождаться callback, проверить тело и заголовки.

6. Добавить e2e негативные сценарии в PostgreSQL mode.
   - Все sync слоты заняты: `429`, `Retry-After`, trace со статусом `FAILED`.
   - Некорректный JSON и validation error возвращают стабильный error contract.
   - Async idempotency conflict возвращает `409`.
   - Cancel pending task возвращает `CANCELLED`, повторный cancel идемпотентен.
   - Retry для неподходящего статуса возвращает state conflict.

### P1: важные тесты для устойчивости после P0

1. LISTEN/NOTIFY integration.
   - В PostgreSQL mode с `sync-acquire-wait-mode=listen_notify` ожидающий sync acquire просыпается после release.
   - Потерянная notification компенсируется fallback timeout.
   - Listener корректно стартует, останавливается и не оставляет фоновых потоков после закрытия context.

2. HTTP callback client contract.
   - `HttpCallbackClient` отправляет `POST`, JSON body, `X-Callback-Attempt`, `X-Request-Id`.
   - `2xx` считается успешным ответом.
   - `4xx/5xx`, connect timeout и read timeout приводят к retry/dead через dispatcher.

3. Controller/API coverage в memory mode.
   - Дополнить `ExternalAsyncControllerTest`: not found, чужой `X-Client-Service`, malformed JSON, missing headers, polling flow после `DONE`, manual retry для `DEAD` retryable задачи.
   - Дополнить `ExternalSyncControllerTest`: upstream timeout, interruption/error, повторные SYNC trace с одинаковым `externalId`, проверка `X-Request-Id` в ошибках.
   - Проверить стабильность JSON field names для всех публичных responses.

4. Dashboard backend tests.
   - Статус: не выполняется в рамках CR001 по решению от 2026-06-12.
   - Unit-тесты `DashboardMetricsRegistry`: счетчики, active requests, rate window, percentiles, reset.
   - Unit-тесты `DashboardLoadRunner`: start/stop/update profile, генерация sync/async запросов, обработка исключений клиента.
   - `@WebMvcTest` или `@SpringBootTest` для `DashboardController`: snapshot, health, profile update validation, simulation settings validation, reset.

5. OpenAPI contract tests.
   - Проверить, что `/v3/api-docs` содержит основные paths: sync, async submit/status/cancel/retry, dashboard API.
   - Сверить обязательные поля ошибок и основных responses с `docs/openapi/*.yaml`.
   - Добавить snapshot-style проверку generated OpenAPI только для стабильных частей, чтобы не ловить шум от порядка полей.

### P2: расширенное функциональное покрытие

1. Repository contract suite.
   - Создать общий набор контрактных тестов для `SlotRepository`, `AsyncTaskRepository`, `CallbackDeliveryRepository`.
   - Прогонять один и тот же behavior suite для memory и postgres реализаций.

2. Scheduler functional tests.
   - `ExternalAsyncDispatcherScheduler` вызывает batch и пишет метрики.
   - `CallbackDeliveryDispatcherScheduler` вызывает batch/recovery и пишет метрики.
   - `SlotLeaseReaperScheduler` освобождает истекшие lease и пишет метрики.
   - Проверять без реального ожидания времени через управляемый scheduler/clock.

3. Concurrency correctness без performance-метрик.
   - Статус: выполнено в рамках CR001-T016.
   - Два dispatcher instance с одной PostgreSQL БД не обрабатывают одну задачу дважды.
   - Две callback delivery worker группы не доставляют один callback дважды.
   - Конкурентные async submit с одинаковым idempotency key дают одну запись и согласованный ответ.
   - Конкурентные sync и async lease не нарушают reserve для sync.

4. Configuration binding tests.
   - Проверить defaults всех `ExternalGateway*Properties`.
   - Проверить parsing duration values в `.properties`.
   - Проверить conditional beans для memory/postgres и enabled/disabled scheduler flags.
   - Проверить отсутствие `DataSource` и Liquibase в memory mode уже есть, но стоит вынести в отдельный configuration test.

5. Static UI smoke.
   - Проверить, что `/dashboard/index.html` грузится и содержит ожидаемые root элементы.
   - Минимальный browser/e2e smoke: открыть dashboard, дождаться `/dashboard/api/snapshot`, изменить simulation settings, старт/стоп функциональной нагрузки.
   - Не проверять FPS, latency under load или throughput, так как performance-тесты исключены.

## Definition of Done

- `mvn test` остается быстрым и не требует Docker.
- Docker-зависимый набор запускается отдельной командой и проходит на чистой машине с Docker.
- Memory и PostgreSQL реализации проходят общий repository contract suite.
- Есть хотя бы один полный e2e сценарий sync, async polling и async callback в PostgreSQL mode.
- Dashboard backend исключен из Definition of Done для CR001 по решению от 2026-06-12.
- OpenAPI и HTTP error contract зафиксированы тестами.
- В тестовом выводе нет ожидаемых WARN из фоновых потоков, которые маскируют реальные проблемы.
