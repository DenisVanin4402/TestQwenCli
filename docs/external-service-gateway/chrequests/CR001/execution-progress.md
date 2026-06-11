# CR001: план и журнал выполнения

## Назначение

Файл фиксирует рабочий статус CR001: какие этапы выполнены, какие проверки запускались и какой результат получен. Основной список задач остается в `work-items.md`, а этот документ хранит историю выполнения и текущие блокеры.

## Чеклист этапов

- [x] Изучить `work-items.md` и `test-coverage-plan.md`.
- [x] Проверить текущую Maven-структуру, зависимости и тестовый набор.
- [x] Добавить Maven-профиль `integration-tests`.
- [x] Настроить Maven Failsafe для запуска `*IT`.
- [x] Оставить `mvn test` быстрым и без Docker-зависимых тестов.
- [x] Добавить зависимости `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`.
- [x] Добавить test support для PostgreSQL Testcontainers и динамических Spring properties.
- [x] Добавить очистку PostgreSQL-таблиц между integration/e2e тестами.
- [x] Добавить фабрики sync/async/callback тестовых запросов.
- [x] Добавить управляемые async wait helpers и mutable test clock.
- [x] Добавить PostgreSQL smoke integration-тест для postgres mode и Liquibase.
- [x] Проверить, что smoke-тест проверяет schema, таблицы, constraints, indexes и начальные строки `ext_slots`.
- [x] Задокументировать команду запуска Docker-зависимых integration-тестов в `README.md`.
- [x] Добавить общий behavior suite для `SlotRepository`.
- [x] Подключить `MemorySlotRepository` к общему контракту.
- [x] Добавить Docker-зависимый `PostgresSlotRepositoryIT` на том же контракте.
- [x] Зафиксировать отсутствие повторной выдачи одного sync-слота при конкурентном захвате.
- [x] Исправить кеширование Spring context между PostgreSQL Testcontainers IT.
- [x] Добавить детальный план недостающих тестов с учетом текущей функциональности.
- [x] Добавить общий behavior suite для `AsyncTaskRepository`.
- [x] Подключить `MemoryAsyncTaskRepository` к общему контракту.
- [x] Добавить Docker-зависимый `PostgresAsyncTaskRepositoryIT` на том же контракте.
- [x] Закрепить submit, idempotency, claim, retry/cancel, sync trace и stats для memory/PostgreSQL реализаций.
- [x] Добавить общий behavior suite для `CallbackDeliveryRepository`.
- [x] Подключить `MemoryCallbackDeliveryRepository` к общему контракту.
- [x] Добавить Docker-зависимый `PostgresCallbackDeliveryRepositoryIT` на том же контракте.
- [x] Закрепить create pending/dead, upsert, claim, delivered/retry/dead, timeout recovery и stats для memory/PostgreSQL реализаций.
- [x] Добавить PostgreSQL e2e happy path для sync API на реальном HTTP-порту.
- [x] Добавить PostgreSQL e2e happy path для async polling API с реальным scheduler/dispatcher.
- [x] Проверить, что e2e happy path сверяет HTTP-ответы и persisted state в PostgreSQL.
- [x] Добавить PostgreSQL e2e happy path для async callback через реальный тестовый HTTP endpoint.
- [x] Проверить callback body, `X-Callback-Attempt`, `X-Request-Id` и отсутствие повторной доставки.
- [x] Проверить финальные статусы `DONE`/`DELIVERED` в PostgreSQL после HTTP callback.
- [x] Добавить PostgreSQL e2e негативные API-сценарии для sync и async API.
- [x] Проверить `NO_SLOT_AVAILABLE`, malformed JSON, validation error, async idempotency conflict, cancel и retry state conflict.
- [x] Проверить persisted state для негативных сценариев, где состояние должно изменяться или сохраняться.
- [x] Добавить PostgreSQL LISTEN/NOTIFY integration-тест для sync wait strategy.
- [x] Проверить пробуждение ожидающего sync acquire через реальный PostgreSQL `NOTIFY`.
- [x] Проверить fallback при потерянной PostgreSQL notification без длинного ожидания polling interval.
- [x] Проверить, что при 10 ожидающих sync acquire один освобожденный slot получает ровно один waiter.
- [x] Проверить, что при 10 ожидающих sync acquire `n` освобожденных slots получают ровно `n` waiters без дублей.
- [x] Добавить контрактный тест `HttpCallbackClient` против реального loopback HTTP server.
- [x] Проверить `POST`, target URI, JSON body, `X-Callback-Attempt` и `X-Request-Id`.
- [x] Проверить отсутствие пустого `X-Request-Id`.
- [x] Проверить `2xx`, `4xx/5xx`, read timeout и connection failure без внешней сети.
- [x] Проверить retry/dead последствия non-2xx callback response в dispatcher flow.
- [x] Расширить controller/API тесты в memory mode.
- [x] Проверить async not found, чужой `X-Client-Service`, malformed JSON и отсутствие optional headers.
- [x] Проверить polling response после `DONE` и manual retry для `DEAD` retryable задачи.
- [x] Проверить sync upstream timeout/error и повторные SYNC trace с одинаковым `externalId`.
- [x] Проверить `X-Request-Id` в новых error responses и стабильные JSON-поля публичных ответов.
- [x] Добавить OpenAPI metadata и явные response/parameter annotations для gateway API.
- [x] Сверить generated `/v3/api-docs` с `docs/openapi` по стабильным paths, параметрам, status codes и schema fields.
- [x] Сверить callback OpenAPI-документ с сериализуемым `CallbackPayload`.
- [x] Добавить functional-тесты scheduler-слоя без ожидания реального расписания.
- [x] Проверить enabled/disabled flags для async и callback schedulers.
- [x] Проверить запись scheduler-метрик только при положительном count.
- [x] Запустить `mvn test`.
- [x] Добиться успешного `mvn verify -Pintegration-tests` в текущем окружении.

## Статус задач CR001

| Задача | Статус | Результат |
| --- | --- | --- |
| CR001-T001: инфраструктура integration-тестов | Выполнена | Профиль `integration-tests` и Failsafe добавлены, `mvn test` не запускает `*IT`, `mvn verify -Pintegration-tests` запускает Docker-зависимые `*IT` и проходит в текущем окружении. Для совместимости Testcontainers 1.20.5 с Docker Engine 29 в Failsafe передается `api.version=1.44`. |
| CR001-T002: PostgreSQL smoke и Liquibase | Выполнена | Добавлен `PostgresLiquibaseSmokeIT` с `PostgreSQLContainer` и `@DynamicPropertySource`. Тест поднимает `postgres:16-alpine`, стартует postgres mode, применяет Liquibase и проверяет schema, таблицы, constraints, indexes и начальные строки `ext_slots`. |
| CR001-T003: test support для PostgreSQL/e2e | Выполнена | `PostgresIntegrationTestSupport` расширен общими `JdbcTemplate`/`NamedParameterJdbcTemplate`, очисткой runtime-таблиц, `AsyncTestAwaiter`, `MutableTestClock` и фабриками `GatewayTestRequests`. `PostgresLiquibaseSmokeIT` переиспользует support и очищает runtime state после теста. |
| CR001-T004: контракт `SlotRepository` | Выполнена | Добавлен общий `SlotRepositoryContract`, который проходит для `MemorySlotRepository` в быстром Surefire-контуре и для `PostgresSlotRepository` в Docker-зависимом `PostgresSlotRepositoryIT`. Контракт покрывает sync acquire до лимита, async reserve, live/expired sync waiters, `release`, `heartbeat`, защиту `leaseId`, `reapExpiredLeases` и конкурентный sync acquire без выдачи одного slot двум владельцам. |
| CR001-T005: контракт `AsyncTaskRepository` | Выполнена | Добавлен общий `AsyncTaskRepositoryContract`, который проходит для `MemoryAsyncTaskRepository` в быстром Surefire-контуре и для `PostgresAsyncTaskRepository` в Docker-зависимом `PostgresAsyncTaskRepositoryIT`. Контракт покрывает submit `PENDING` задачи, idempotency reuse/conflict, JSON payload claim, priority/available_at claim, `complete`, `failTransient`, `returnClaimToPending`, `cancel`, `retry`, aggregate callback status, несколько SYNC trace на один `externalId` и `stats` без смешивания sync trace с async queue. |
| CR001-T006: контракт `CallbackDeliveryRepository` | Выполнена | Добавлен общий `CallbackDeliveryRepositoryContract`, который проходит для `MemoryCallbackDeliveryRepository` в быстром Surefire-контуре и для `PostgresCallbackDeliveryRepository` в Docker-зависимом `PostgresCallbackDeliveryRepositoryIT`. Контракт покрывает создание pending/dead доставки, upsert по `taskId`, claim с обновлением `eventId`, порядок claim по `availableAt`, `markDelivered`, `markRetryOrDead`, `markDead`, timeout recovery в `RETRY`/`DEAD` и `stats`. |
| CR001-T007: e2e happy path sync и async polling | Выполнена | Добавлен `PostgresExternalGatewayHappyPathIT` с `webEnvironment = RANDOM_PORT`, `TestRestTemplate` и `PostgresIntegrationTestSupport`. Тест проверяет `POST /v1/external/sync` через реальный HTTP-порт и persisted SYNC trace в PostgreSQL, а также `POST /v1/external/async` в polling mode, работу scheduled async dispatcher и `GET /v1/external/async/{taskId}` до статуса `DONE`. |
| CR001-T008: e2e async callback | Выполнена | Добавлен `PostgresExternalGatewayCallbackIT` с реальным HTTP endpoint на loopback, динамическим allow-list callback URL и включенными async/callback schedulers. Тест отправляет async-запрос в callback mode, проверяет HTTP callback body и заголовки `X-Callback-Attempt`/`X-Request-Id`, а затем сверяет `DONE` задачу и `DELIVERED` callback delivery в PostgreSQL без повторной доставки. |
| CR001-T009: e2e негативные API-сценарии | Выполнена | Добавлен `PostgresExternalGatewayNegativeApiIT` с `webEnvironment = RANDOM_PORT`, `TestRestTemplate`, PostgreSQL backend и отключенными async/callback schedulers. Тест покрывает занятые sync-слоты с `429`/`Retry-After`/persisted `FAILED` trace, malformed JSON `INVALID_REQUEST`, validation error `VALIDATION_ERROR`, async idempotency conflict `409`, cancel pending task с повторным cancel и retry pending task с `TASK_STATE_CONFLICT`. |
| CR001-T010: LISTEN/NOTIFY integration | Выполнена | Добавлен `PostgresSlotListenNotifyIT` с postgres mode и `sync-acquire-wait-mode=listen_notify`. Тест подтверждает, что ожидающий sync acquire просыпается после освобождения слота и реального PostgreSQL `NOTIFY`, что потерянная notification компенсируется fallback timeout, а также что при 10 ожидающих contenders ровно число освобожденных slots получает lease без дублей. |
| CR001-T011: контракт `HttpCallbackClient` | Выполнена | Добавлен `HttpCallbackClientTest` с JDK `HttpServer` на loopback. Тест проверяет `POST`, target URI, JSON body, `X-Callback-Attempt`, заданный/пустой `X-Request-Id`, `2xx`, `4xx/5xx`, read timeout и connection failure без внешней сети. `CallbackDeliveryFlowTest` дополнительно закрепляет non-2xx response как retry/dead путь dispatcher. |
| CR001-T012: расширение controller/API тестов в memory mode | Выполнена | `ExternalAsyncControllerTest` расширен сценариями malformed JSON, not found, отсутствующих optional headers, чужого `X-Client-Service`, polling response после `DONE` и manual retry для `DEAD` retryable задачи. `ExternalSyncControllerTest` проверяет upstream timeout, simulated upstream failure и повторные SYNC trace с одинаковым `externalId`. |
| CR001-T013: тесты `dashboard-backend` | Не выполняется | Исключена из CR001 по решению от 2026-06-12. |
| CR001-T014: OpenAPI и error contract | Выполнена | Добавлен `ExternalGatewayOpenApiContractTest`, который сверяет generated `/v3/api-docs` с `docs/openapi` по gateway paths, operationId, параметрам, status codes, response schema refs, публичным schema fields и наличию dashboard API paths; callback YAML сверяется с сериализуемым `CallbackPayload`. |
| CR001-T015: functional-тесты scheduler-слоя | Выполнена | Schedulers обновлены записью dashboard-метрик для положительных результатов. Добавлен `GatewaySchedulerTest`, который напрямую вызывает tick-методы async dispatcher, callback dispatcher/recovery и slot lease reaper, а также проверяет conditional creation async/callback scheduler beans по enabled/disabled flags. |
| CR001-T016 - CR001-T018 | Не начаты | Следующие задачи остаются в очереди из `work-items.md`. |

## История выполнения

### 2026-06-08

1. Прочитаны `docs/external-service-gateway/chrequests/CR001/work-items.md` и `test-coverage-plan.md`.
   - Результат: подтвержден порядок запуска работ, первая цель - CR001-T001, затем PostgreSQL smoke из CR001-T002.

2. Проверена текущая структура проекта.
   - Результат: проект является multi-module Maven reactor с модулями `dashboard-backend`, `dashboard-ui`, `test-qwen-cli-app`; Failsafe и Testcontainers до изменений отсутствовали.

3. Добавлен профиль `integration-tests` в корневой `pom.xml`.
   - Результат: Maven Failsafe запускает `**/*IT.java`, а при отсутствии integration-тестов модуль не ломает reactor.

4. Добавлены Testcontainers test dependencies в `test-qwen-cli-app/pom.xml`.
   - Результат: модуль получил зависимости для Spring Boot Testcontainers, JUnit Jupiter extension и PostgreSQLContainer без явных версий, через Spring Boot BOM.

5. Добавлен `PostgresIntegrationTestSupport`.
   - Результат: общий support поднимает `postgres:16-alpine` и передает `external-gateway.postgres.jdbc-url`, username, password и schema через `@DynamicPropertySource`.

6. Добавлен `PostgresLiquibaseSmokeIT`.
   - Результат: тест запускает Spring context в `external-gateway.repository.type=postgres`, отключает фоновые async/callback schedulers и проверяет:
     - schema `external_gateway_it`;
     - таблицы `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`;
     - ключевые check/unique/foreign key constraints;
     - индексы для slots, waiters, request queue и callback delivery;
     - 5 начальных строк в `ext_slots`.

7. Обновлен `README.md`.
   - Результат: добавлена команда `mvn verify -Pintegration-tests` и уточнение, что обычный `mvn test` не поднимает контейнеры.

8. Запущен `mvn test`.
   - Результат: успешно, 55 тестов, 0 failures, 0 errors, 0 skipped. Новый `PostgresLiquibaseSmokeIT` не попал в обычный Surefire-прогон.

9. Запущен `mvn verify -Pintegration-tests`.
   - Результат: первая попытка в sandbox упала на доступе Maven к сети: `Permission denied: getsockopt`.
   - Повтор с повышенными правами дошел до `PostgresLiquibaseSmokeIT`, но Testcontainers не смог найти валидный Docker environment.

10. Проверен Docker.
    - Результат: `docker ps` видит работающий контейнер `testqwen-postgis`; `docker info` с повышенными правами видит Docker Desktop context `desktop-linux`.
    - Testcontainers при этом получает невалидный ответ Docker API через pipe `docker_cli`: пустые server-поля и статус `400`.

11. Проверены обходные варианты для Testcontainers.
    - Результат: запуск с `-Ddocker.host=npipe:////./pipe/dockerDesktopLinuxEngine` и с переменной окружения `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` не изменил результат. Integration-прогон остается заблокированным окружением, а не ошибкой компиляции или Spring/Liquibase кода.

12. Диагностирована несовместимость Testcontainers 1.20.5 с Docker Engine 29.
    - Результат: Docker CLI успешно подключается к Docker Desktop engine `29.2.1`, но docker-java внутри Testcontainers при дефолтной версии Docker API получает `400` и пустые server-поля. Проверочный запуск `mvn verify -Pintegration-tests '-Dapi.version=1.44'` прошел успешно.

13. Обновлен Failsafe-профиль `integration-tests`.
    - Результат: в `pom.xml` добавлено `systemPropertyVariables` со значением `api.version=1.44`, поэтому отдельный ручной параметр при запуске integration-тестов больше не нужен.

14. Уточнена конфигурация `PostgresLiquibaseSmokeIT`.
    - Результат: для smoke-теста задан `external-gateway.slots.lease-reap-interval-ms=600000`, чтобы slot reaper не шумел ошибками после остановки Testcontainers PostgreSQL.

15. Повторно запущен `mvn verify -Pintegration-tests`.
    - Результат: успешно. Surefire-прогон выполнил 55 тестов без failures/errors/skipped, Failsafe поднял `testcontainers/ryuk:0.11.0` и `postgres:16-alpine`, Liquibase применил 5 changesets, `PostgresLiquibaseSmokeIT` прошел.

16. Расширен test support для PostgreSQL/e2e.
    - Результат: добавлены `PostgresGatewayDatabaseCleaner`, `GatewayTestRequests`, `AsyncTestAwaiter` и `MutableTestClock`. Базовый `PostgresIntegrationTestSupport` теперь предоставляет общий JDBC-доступ, очистку таблиц, управляемые ожидания и переиспользуемую Testcontainers-настройку PostgreSQL.

17. Обновлен `PostgresLiquibaseSmokeIT`.
    - Результат: smoke-тест использует `jdbcTemplate()` из support-класса и очищает runtime data через `cleanGatewayTables()` после выполнения.

18. Добавлен быстрый unit-набор `GatewayTestSupportTest`.
    - Результат: проверены фабрики sync/async/callback данных, mutable clock и диагностическое поведение async wait helper без Docker.

19. Повторно запущены проверки после CR001-T003.
    - Результат: `mvn test` успешно выполнил 60 тестов без failures/errors/skipped. `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор и Failsafe `PostgresLiquibaseSmokeIT` на `postgres:16-alpine`.

20. Добавлен общий контрактный набор `SlotRepositoryContract`.
    - Результат: сценарии из прежнего `MemorySlotRepositoryTest` вынесены в общий JUnit interface и расширены проверками истекшего sync waiter, успешного `heartbeat` и конкурентного sync acquire.

21. `MemorySlotRepositoryTest` переведен на общий контракт.
    - Результат: быстрый `mvn test` продолжает проверять memory-реализацию без Docker и выполняет 9 сценариев контракта `SlotRepository`.

22. Добавлен `PostgresSlotRepositoryIT`.
    - Результат: PostgreSQL-реализация запускается в postgres mode на `postgres:16-alpine`, переиспользует `PostgresIntegrationTestSupport`, очищает runtime state перед/после каждого теста и выполняет тот же набор из 9 сценариев `SlotRepository`.

23. Диагностировано кеширование Spring context между PostgreSQL Testcontainers IT.
    - Результат: при совместном запуске `PostgresLiquibaseSmokeIT` и `PostgresSlotRepositoryIT` Spring cache переиспользовал `DataSource` к уже остановленному контейнеру. В `PostgresIntegrationTestSupport` добавлен `@DirtiesContext(classMode = AFTER_CLASS)`, чтобы каждый PostgreSQL IT получал свежий context и актуальный Testcontainers JDBC URL.

24. Повторно запущены проверки после CR001-T004.
    - Результат: `mvn test` успешно выполнил 63 теста без failures/errors/skipped. `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор из 63 тестов и Failsafe-набор из 10 integration-тестов: `PostgresLiquibaseSmokeIT` и 9 сценариев `PostgresSlotRepositoryIT`.

25. Добавлен детальный план недостающих тестов `missing-tests-plan.md`.
    - Результат: план фиксирует текущие пробелы покрытия по разработанной функциональности: PostgreSQL `SlotManager` sync wait на занятых слотах, контракты `AsyncTaskRepository` и `CallbackDeliveryRepository`, e2e sync/async/callback flows, негативные API-сценарии, LISTEN/NOTIFY, `HttpCallbackClient`, controller/API memory mode, schedulers, configuration binding, OpenAPI, dashboard backend/UI, concurrency correctness и test support self-tests.

26. Добавлен общий контрактный набор `AsyncTaskRepositoryContract`.
    - Результат: контракт закрепляет ключевое поведение очереди async-задач и sync trace: создание `PENDING` задачи, поиск по `taskId`/`externalId`, idempotency reuse, idempotency conflict по payload/priority/deliveryMode, claim с JSON payload, порядок claim по priority и `available_at`, запрет повторного claim для `IN_PROGRESS`, `complete`, `failTransient`, `returnClaimToPending`, `cancel`, `retry`, обновление aggregate callback status, несколько SYNC trace на один `externalId` и `stats` без учета SYNC trace.

27. `MemoryAsyncTaskRepositoryTest` переведен на общий контракт, добавлен `PostgresAsyncTaskRepositoryIT`.
    - Результат: memory-реализация проверяется в быстром Surefire-контуре, PostgreSQL-реализация проверяется тем же behavior suite через Testcontainers PostgreSQL и переиспользует `PostgresIntegrationTestSupport` с очисткой runtime-таблиц перед/после каждого сценария.

28. Повторно запущены проверки после CR001-T005.
    - Результат: `mvn test` успешно выполнил 72 теста без failures/errors/skipped. `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор из 72 тестов и Failsafe-набор из 21 integration-теста: `PostgresLiquibaseSmokeIT`, 11 сценариев `PostgresAsyncTaskRepositoryIT` и 9 сценариев `PostgresSlotRepositoryIT`.

29. Добавлен общий контрактный набор `CallbackDeliveryRepositoryContract`.
    - Результат: контракт закрепляет lifecycle callback-доставки на уровне репозитория: `createPending`, `createDead`, upsert по `taskId`, claim доступной записи с обновлением `eventId`, порядок claim по `availableAt`, `markDelivered`, `markRetryOrDead`, `markDead`, восстановление зависших `DELIVERING` доставок в `RETRY` или `DEAD`, а также счетчики `stats` и `oldestBacklogCreatedAt`.

30. `MemoryCallbackDeliveryRepositoryTest` переведен на общий контракт, добавлен `PostgresCallbackDeliveryRepositoryIT`.
    - Результат: memory-реализация проверяется в быстром Surefire-контуре, PostgreSQL-реализация проверяется тем же behavior suite через Testcontainers PostgreSQL. Контракт создает финальные async-задачи через `AsyncTaskRepository`, поэтому PostgreSQL-сценарии проверяют не только callback-таблицу, но и FK-связь с `ext_request_queue`.

31. Повторно запущены проверки после CR001-T006.
    - Результат: `mvn test` успешно выполнил 82 теста без failures/errors/skipped. `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор из 82 тестов и Failsafe-набор из 31 integration-теста: `PostgresLiquibaseSmokeIT`, 11 сценариев `PostgresAsyncTaskRepositoryIT`, 10 сценариев `PostgresCallbackDeliveryRepositoryIT` и 9 сценариев `PostgresSlotRepositoryIT`.

32. Добавлен `PostgresExternalGatewayHappyPathIT` для CR001-T007.
    - Результат: integration-тест запускает приложение на случайном HTTP-порту в postgres mode, использует `TestRestTemplate`, очищает PostgreSQL runtime-таблицы перед/после сценариев и проверяет два happy path: sync API с persisted SYNC trace и async polling API с реальным scheduled dispatcher до `DONE`.

33. Повторно запущены проверки после CR001-T007.
    - Результат: `mvn test` успешно выполнил 82 теста без failures/errors/skipped и не запустил новый `*IT` в Surefire. `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор из 82 тестов и Failsafe-набор из 33 integration-тестов: `PostgresExternalGatewayHappyPathIT` на 2 e2e-сценария, `PostgresLiquibaseSmokeIT`, 11 сценариев `PostgresAsyncTaskRepositoryIT`, 10 сценариев `PostgresCallbackDeliveryRepositoryIT` и 9 сценариев `PostgresSlotRepositoryIT`.

34. Добавлен `PostgresExternalGatewayCallbackIT` для CR001-T008.
    - Результат: integration-тест запускает приложение на случайном HTTP-порту в postgres mode, поднимает тестовый `HttpServer` на loopback и через `@DynamicPropertySource` подставляет его URL в allow-list `external-gateway.clients[invest-pay].callback-url`. Сценарий отправляет `POST /v1/external/async` в callback mode, дожидается реального HTTP callback, проверяет `POST /callbacks`, тело события, `X-Callback-Attempt`, `X-Request-Id`, финальный `DONE` task, `DELIVERED` callback delivery и отсутствие повторной доставки.
    - Дополнительно: `DashboardSimulatedCallbackClient` переведен под свойство `external-gateway.callback.simulated-client-enabled` с default-on поведением. Callback e2e задает `false`, чтобы использовать production `HttpCallbackClient`, а dashboard-симуляция по умолчанию сохраняет прежнее поведение.

35. Повторно запущены проверки после CR001-T008.
    - Результат: `mvn test` успешно выполнил 82 теста без failures/errors/skipped. `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор из 82 тестов и Failsafe-набор из 34 integration-тестов: `PostgresExternalGatewayCallbackIT`, `PostgresExternalGatewayHappyPathIT` на 2 e2e-сценария, `PostgresLiquibaseSmokeIT`, 11 сценариев `PostgresAsyncTaskRepositoryIT`, 10 сценариев `PostgresCallbackDeliveryRepositoryIT` и 9 сценариев `PostgresSlotRepositoryIT`.

36. Добавлен `PostgresExternalGatewayNegativeApiIT` для CR001-T009.
    - Результат: integration-тест запускает приложение на случайном HTTP-порту в postgres mode, использует `TestRestTemplate`, очищает PostgreSQL runtime-таблицы перед/после сценариев и проверяет негативные API-контракты: занятые sync-слоты с `NO_SLOT_AVAILABLE`, malformed JSON, validation error, async idempotency conflict, cancel pending task и retry state conflict.
    - Дополнительно: проверяется persisted state в PostgreSQL для сценариев, где состояние должно быть записано или сохранено: `FAILED` SYNC trace при отсутствии slot, исходная async-задача после idempotency conflict, `CANCELLED` после cancel и неизменный `PENDING` после невалидного retry.

37. Повторно запущены проверки после CR001-T009.
    - Результат: `mvn test` успешно выполнил 82 теста без failures/errors/skipped и не запустил новый `*IT` в Surefire.
    - Первый запуск `mvn verify -Pintegration-tests` в песочнице не получил доступ к Docker, поэтому Docker-зависимая проверка была повторена с повышенными правами.
    - Повторный Docker-запуск дошел до `PostgresExternalGatewayNegativeApiIT` и выявил нестабильное ожидание сообщения validation error для пустого `clientService`: запрос нарушал одновременно `@NotBlank` и `@Size`.

38. Уточнен validation-сценарий CR001-T009.
    - Результат: в `PostgresExternalGatewayNegativeApiIT` поле `clientService` убрано из невалидного тела запроса, чтобы сценарий проверял однозначное нарушение `@NotBlank` и не зависел от порядка обработки нескольких ошибок валидации.

39. Финально запущены проверки после исправления CR001-T009.
    - Результат: точечный `mvn -pl test-qwen-cli-app -am '-Dit.test=PostgresExternalGatewayNegativeApiIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify -Pintegration-tests` успешно выполнил 6 сценариев `PostgresExternalGatewayNegativeApiIT`.
    - Результат: полный `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор из 82 тестов и Failsafe-набор из 40 integration-тестов: `PostgresExternalGatewayNegativeApiIT` на 6 e2e-сценариев, `PostgresExternalGatewayCallbackIT`, `PostgresExternalGatewayHappyPathIT` на 2 e2e-сценария, `PostgresLiquibaseSmokeIT`, 11 сценариев `PostgresAsyncTaskRepositoryIT`, 10 сценариев `PostgresCallbackDeliveryRepositoryIT` и 9 сценариев `PostgresSlotRepositoryIT`.

40. Добавлен `PostgresSlotListenNotifyIT` для CR001-T010.
    - Результат: integration-тест запускает postgres mode с `external-gateway.slots.sync-acquire-wait-mode=listen_notify`, проверяет старт `PostgresSlotReleaseNotificationListener` и подтверждает получение тестового `NOTIFY` через реальный PostgreSQL.
    - Результат: основной сценарий занимает все sync-слоты, дожидается блокировки ожидающего sync acquire, освобождает слот прямым SQL без локального publisher и отправляет `NOTIFY external_gateway_slot_released`. Ожидающий acquire получает освобожденный slot быстрее полного fallback interval, что закрепляет LISTEN/NOTIFY path отдельно от polling.
    - Результат: fallback-сценарий освобождает слот прямым SQL без `NOTIFY`; ожидающий acquire завершается через короткий fallback timeout и очищает sync waiter.

41. Запущены проверки после CR001-T010.
    - Результат: `mvn test` успешно выполнил 82 теста без failures/errors/skipped; новый `PostgresSlotListenNotifyIT` скомпилирован и не запущен в Surefire.
    - Результат: точечный `mvn -pl test-qwen-cli-app -am '-Dit.test=PostgresSlotListenNotifyIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify -Pintegration-tests` успешно выполнил 2 сценария `PostgresSlotListenNotifyIT`.
    - Результат: полный `mvn verify -Pintegration-tests` успешно выполнил Surefire-набор из 82 тестов и Failsafe-набор из 42 integration-тестов: `PostgresSlotListenNotifyIT` на 2 сценария, `PostgresExternalGatewayNegativeApiIT` на 6 e2e-сценариев, `PostgresExternalGatewayCallbackIT`, `PostgresExternalGatewayHappyPathIT` на 2 e2e-сценария, `PostgresLiquibaseSmokeIT`, 11 сценариев `PostgresAsyncTaskRepositoryIT`, 10 сценариев `PostgresCallbackDeliveryRepositoryIT` и 9 сценариев `PostgresSlotRepositoryIT`.

42. Расширен `PostgresSlotListenNotifyIT` для конкурентных LISTEN/NOTIFY сценариев.
    - Результат: добавлены проверки, где 10 sync waiters одновременно просыпаются после PostgreSQL `NOTIFY`, но при одном освобожденном slot lease получает ровно один waiter.
    - Результат: добавлена зеркальная проверка, где при 10 sync waiters и 3 освобожденных slots lease получают ровно 3 waiters; дополнительно проверяется отсутствие дублей по `slotId` и `leaseId`.
    - Результат: новые тесты и вспомогательные методы снабжены русскими комментариями, включая комментарии к переменным класса `PostgresSlotListenNotifyIT`.
    - Результат: `mvn test` успешно выполнил 82 теста без failures/errors/skipped.
    - Результат: точечный `mvn -pl test-qwen-cli-app -am '-Dit.test=PostgresSlotListenNotifyIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify -Pintegration-tests` успешно выполнил 4 сценария `PostgresSlotListenNotifyIT`.
    - Результат: после добавления комментариев выполнен `mvn -pl test-qwen-cli-app -am test-compile`, тестовые классы успешно компилируются.

43. Запущен полный Docker-зависимый контур после расширения `PostgresSlotListenNotifyIT`.
    - Команда: `mvn verify -Pintegration-tests`.
    - Результат: build завершился успешно за 01:36 min.
    - Результат: Surefire-набор успешно выполнил 82 теста без failures/errors/skipped.
    - Результат: Failsafe-набор успешно выполнил 44 integration-теста без failures/errors/skipped, включая `PostgresSlotListenNotifyIT` на 4 сценария.

44. Добавлен контракт `HttpCallbackClient` для CR001-T011.
    - Результат: добавлен быстрый `HttpCallbackClientTest` без Docker и внешней сети. Тест поднимает JDK `HttpServer` на loopback и проверяет фактический `RestClient`-запрос: `POST`, target URI с query string, JSON body callback payload, `Content-Type`, `X-Callback-Attempt` и `X-Request-Id`.
    - Результат: отдельно проверено, что пустой `requestId` не добавляет `X-Request-Id`.
    - Результат: отдельно проверены `2xx` как успешный `CallbackClientResponse`, `400`/`503` как `RestClientResponseException`, read timeout как `ResourceAccessException` и connection failure на закрытом loopback port как `ResourceAccessException`.
    - Результат: `CallbackDeliveryFlowTest` расширен сценарием non-2xx callback response: первый `503` переводит доставку в `RETRY`, повтор после backoff переводит ее в `DEAD`, а async task получает соответствующий aggregate callback status.

45. Запущены проверки после CR001-T011.
    - Результат: точечный `mvn -pl test-qwen-cli-app -am "-Dtest=HttpCallbackClientTest,CallbackDeliveryFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнил 15 тестов без failures/errors/skipped.
    - Результат: `mvn test` успешно выполнил 89 тестов без failures/errors/skipped.
    - Результат: `mvn verify -Pintegration-tests` завершился успешно за 01:42 min; Surefire-набор выполнил 89 тестов без failures/errors/skipped, Failsafe-набор выполнил 44 integration-теста без failures/errors/skipped.

46. Расширены controller/API тесты в memory mode для CR001-T012.
    - Результат: `ExternalAsyncControllerTest` дополнен проверками malformed JSON `INVALID_REQUEST`, `TASK_NOT_FOUND` с `X-Request-Id`, `TASK_NOT_FOUND` без optional headers, фильтрации чужого `X-Client-Service`, polling response после `DONE` и успешного manual retry для `DEAD` retryable задачи.
    - Результат: сценарии `DONE` и `DEAD` подготавливают состояние через публичный `AsyncTaskRepository`; методы изолированы через `@DirtiesContext(methodMode = BEFORE_METHOD)`, чтобы накопленные memory-задачи из соседних controller-тестов не влияли на claim.
    - Результат: `ExternalSyncControllerTest` дополнен проверками `UPSTREAM_TIMEOUT`, `UPSTREAM_SIMULATED_FAILURE` и повторных SYNC trace с одинаковым `externalId`.
    - Результат: новые error-сценарии проверяют `X-Request-Id`, `retryable`, `details` и persisted sync trace; polling response после `DONE` дополнительно фиксирует стабильные публичные JSON-поля `AsyncTask`.

47. Запущены проверки после CR001-T012.
    - Результат: точечный `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalAsyncControllerTest,ExternalSyncControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнил 19 controller-тестов без failures/errors/skipped.
    - Результат: `mvn test` успешно выполнил 98 тестов без failures/errors/skipped.
    - Наблюдение: в тестовом выводе по-прежнему присутствуют ожидаемый WARN из `PostgresSlotNotificationConfigurationTest` и предупреждение Mockito о dynamic agent loading; это остается в очереди CR001-T017.

48. CR001-T013 исключена из работ, CR001-T014 возвращена в очередь.
    - Решение: задачу `CR001-T013: тесты dashboard-backend` не выполняем в рамках CR001; `CR001-T014: OpenAPI и error contract` остается рабочей задачей.
    - Результат: `work-items.md`, `test-coverage-plan.md` и этот журнал обновлены; после CR001-T012 очередь переходит к CR001-T014.

49. Добавлен OpenAPI/error contract для CR001-T014.
    - Результат: в `TestQwenCliApplication` добавлена общая OpenAPI metadata для generated `/v3/api-docs`.
    - Результат: `ExternalSyncController` и `ExternalAsyncController` снабжены OpenAPI-аннотациями для публичных operationId, response codes, JSON response schemas и стабильных header/path параметров.
    - Результат: `docs/openapi/external-gateway-sync.yaml` приведен к фактическому HTTP-поведению: upstream timeout документируется как `504`, неиспользуемый `502` удален, а `upstreamStatus` отмечен как обязательное публичное поле sync response.
    - Результат: `docs/openapi/external-gateway-async.yaml` приведен к текущей реализации: неиспользуемый `413` удален, `alreadyExisted` и `callbackDeliveryStatus` отмечены как обязательные публичные поля соответствующих response-схем.
    - Результат: добавлен `ExternalGatewayOpenApiContractTest`, который через MockMvc получает generated `/v3/api-docs`, читает YAML из `docs/openapi` и сверяет gateway paths, operationId, параметры, status codes, response schema refs, schema fields и наличие dashboard API paths.
    - Результат: callback OpenAPI-документ дополнительно сверяется с фактически сериализуемым `CallbackPayload`, так как callback endpoint реализуется сервисами-клиентами и не публикуется в generated `/v3/api-docs` текущего приложения.

50. Запущены проверки после CR001-T014.
    - Результат: точечный `mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнил 2 OpenAPI contract-теста без failures/errors/skipped.
    - Результат: `mvn test` успешно выполнил 100 тестов без failures/errors/skipped.
    - Результат: `mvn verify -Pintegration-tests` завершился успешно за 02:03 min; Surefire-набор выполнил 100 тестов без failures/errors/skipped, Failsafe-набор выполнил 44 integration-теста без failures/errors/skipped.
    - Наблюдение: в тестовом выводе по-прежнему присутствуют ожидаемый WARN из `PostgresSlotNotificationConfigurationTest` и предупреждение Mockito о dynamic agent loading; это остается в очереди CR001-T017.

51. Добавлены functional-тесты scheduler-слоя для CR001-T015.
    - Результат: `ExternalAsyncDispatcherScheduler` теперь записывает `DashboardMetricsRegistry.recordAsyncDispatchIterations` после `dispatchBatch`, а `DashboardMetricsRegistry` учитывает только положительный count.
    - Результат: `CallbackDeliveryDispatcherScheduler` теперь записывает `DashboardMetricsRegistry.recordCallbackDispatchIterations` после `dispatchBatch`; recovery tick и startup recovery по-прежнему вызывают `recoverTimedOutDeliveries` без ожидания реального расписания.
    - Результат: `SlotLeaseReaperScheduler` теперь записывает `DashboardMetricsRegistry.recordExpiredLeases` после `reapExpiredLeases`, с тем же правилом учета только положительного count.
    - Результат: добавлен быстрый `GatewaySchedulerTest` на 10 сценариев: async/callback batch size, positive/zero metrics, callback startup/scheduled recovery, slot lease reaper metrics и conditional creation async/callback scheduler beans при enabled/disabled flags.

52. Запущены проверки после CR001-T015.
    - Результат: точечный `mvn -pl test-qwen-cli-app -am "-Dtest=GatewaySchedulerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` успешно выполнил 10 scheduler-тестов без failures/errors/skipped.
    - Результат: `mvn test` успешно выполнил 110 тестов без failures/errors/skipped.
    - Результат: `mvn verify -Pintegration-tests` завершился успешно за 02:02 min; Surefire-набор выполнил 110 тестов без failures/errors/skipped, Failsafe-набор выполнил 44 integration-теста без failures/errors/skipped.
    - Наблюдение: в тестовом выводе по-прежнему присутствуют ожидаемый WARN из `PostgresSlotNotificationConfigurationTest` и предупреждение Mockito о dynamic agent loading; это остается в очереди CR001-T017.

## Текущий результат

- Быстрый тестовый контур `mvn test` сохранен и проходит: 110 тестов без failures/errors/skipped.
- Docker-зависимый контур выделен в отдельную команду `mvn verify -Pintegration-tests` и проходит: 110 Surefire-тестов и 44 Failsafe integration-теста без failures/errors/skipped.
- PostgreSQL smoke-тест для Liquibase добавлен, компилируется, запускается Failsafe и проходит на реальном `postgres:16-alpine`.
- CR001-T001, CR001-T002, CR001-T003, CR001-T004, CR001-T005, CR001-T006, CR001-T007, CR001-T008, CR001-T009, CR001-T010, CR001-T011, CR001-T012, CR001-T014 и CR001-T015 закрыты по приемке; CR001-T013 исключена из работ по решению от 2026-06-12.
- PostgreSQL/e2e support готов для следующих contract/e2e тестов: есть очистка БД, фабрики тестовых запросов, mutable clock и bounded async waits с диагностикой timeout.
- `SlotRepository` закреплен общим контрактом для memory и PostgreSQL реализаций, включая конкурентный sync acquire.
- `AsyncTaskRepository` закреплен общим контрактом для memory и PostgreSQL реализаций, включая idempotency, retry/cancel, JSON payload claim, SYNC trace и stats.
- `CallbackDeliveryRepository` закреплен общим контрактом для memory и PostgreSQL реализаций, включая upsert по `taskId`, claim с обновлением `eventId`, retry/dead lifecycle, timeout recovery и stats.
- Happy path sync и async polling закреплены e2e-тестом на реальном HTTP-порту с PostgreSQL backend и реальным async dispatcher.
- Happy path async callback закреплен e2e-тестом с реальным HTTP callback endpoint, production `HttpCallbackClient`, PostgreSQL backend и проверкой `DONE`/`DELIVERED` persisted state.
- Негативные sync/async API-сценарии закреплены e2e-тестом на реальном HTTP-порту с PostgreSQL backend и проверкой контракта ошибок и persisted state.
- LISTEN/NOTIFY sync wait закреплен integration-тестом на реальном PostgreSQL: проверены notification path, fallback при потерянном `NOTIFY` и конкурентное пробуждение 10 waiters с выдачей lease ровно по числу освобожденных slots.
- Низкоуровневый `HttpCallbackClient` закреплен тестом на реальном loopback HTTP server, включая успешный ответ, HTTP error responses и сетевые ошибки без внешнего доступа.
- Быстрые controller/API тесты в memory mode расширены для async/sync негативных сценариев, polling после `DONE`, manual retry `DEAD` и повторных SYNC trace с одинаковым `externalId`.
- OpenAPI и error contract закреплены быстрым MockMvc-тестом: generated `/v3/api-docs` сверяется с `docs/openapi` по стабильным частям gateway API, а callback YAML сверяется с сериализуемым `CallbackPayload`.
- Scheduler-слой закреплен быстрыми functional-тестами без ожидания реального расписания: проверены batch size, enabled/disabled flags, startup/scheduled callback recovery и запись dashboard-метрик только при положительных результатах.
- PostgreSQL integration-контексты не переиспользуют `DataSource` к остановленному Testcontainers PostgreSQL между IT-классами.
- В тестовом выводе остаются отдельные технические долги вне текущего фикса: ожидаемый WARN из `PostgresSlotNotificationConfigurationTest` и предупреждение Mockito о dynamic agent loading.

## Следующие шаги

- CR001-T013 пропустить по решению от 2026-06-12.
- Перейти к CR001-T016: concurrency correctness.
- Затем расширять покрытие по CR001-T017 и следующим задачам из `work-items.md`.
- Пробел `MT-P0-001` по PostgreSQL `SlotManager` sync wait на занятых слотах закрыт в рамках CR001-T010.
- В рамках CR001-T017 убрать ожидаемый WARN из `PostgresSlotNotificationConfigurationTest` и отдельно решить предупреждение Mockito agent.
