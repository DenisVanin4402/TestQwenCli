# ТЗ и план реализации PostgreSQL-репозиториев на Spring Data JPA

## Цель

Проверить возможность добавить альтернативную PostgreSQL-реализацию репозиториев external gateway не через `NamedParameterJdbcTemplate`, а через Spring Data JPA Repository, не меняя текущие доменные интерфейсы:

- `SlotRepository`;
- `AsyncTaskRepository`;
- `CallbackDeliveryRepository`.

Реализация должна быть добавлена параллельно текущей JDBC-версии и включаться отдельным режимом конфигурации. Текущую реализацию `gateway.repository.postgres` считать эталонной по поведению и производительности до завершения интеграционных и нагрузочных проверок.

## Имя пакета и режим конфигурации

Запрашиваемое имя `repository/postges-jpa` нужно нормализовать перед реализацией:

- `postges` выглядит как опечатка, рабочее имя должно быть `postgres`;
- Java package не может содержать дефис, поэтому для кода использовать пакет `com.example.testqwencli.gateway.repository.postgresjpa` или вложенный пакет `com.example.testqwencli.gateway.repository.postgres.jpa`;
- для внешнего значения свойства допустимо использовать дефис: `external-gateway.repository.type=postgres-jpa`.

Рекомендуемый вариант: пакет `com.example.testqwencli.gateway.repository.postgresjpa`, потому что он является отдельной sibling-реализацией рядом с текущими `memory` и `postgres`.

## Вывод по реализуемости

Сделать аналог на Spring Data JPA возможно, но это не будет чистый CRUD/JPA-порт. Текущий PostgreSQL-слой активно использует PostgreSQL-specific операции, которые плохо выражаются через JPQL и стандартные методы `JpaRepository`:

- `WITH candidate ... FOR UPDATE SKIP LOCKED ... UPDATE ... RETURNING` для атомарного claim слотов, async-задач и callback-доставок;
- `INSERT ... ON CONFLICT ... DO NOTHING/DO UPDATE ... RETURNING` для идемпотентного submit и upsert callback-доставки;
- условные `UPDATE ... RETURNING`, где результат должен быть ровно состоянием, измененным базой;
- `jsonb`, `jsonb_set` и приведение `CAST(:payload AS jsonb)`;
- динамическая schema из `external-gateway.postgres.schema`;
- отдельные транзакционные границы для slot lease и async processing transaction;
- PostgreSQL `LISTEN/NOTIFY` для ускорения ожидания sync-слота.

Практичный вариант JPA-реализации: использовать Spring Data JPA для entity mapping, простых чтений, projections и общей структуры репозиториев, а критичные команды оставить native query через `EntityManager` или custom repository fragments. Если переписать эти места на обычный `find -> mutate entity -> save`, изменятся атомарность, количество round trip и профиль блокировок.

## Требования к поведению

JPA-реализация должна сохранить поведение текущей JDBC-версии:

- sync slot acquire выбирает свободный или истекший slot, не превышая `external-gateway.slots.total`;
- release и heartbeat меняют слот только при совпадении пары `slotId + leaseId`;
- async slot acquire сохраняет dynamic sync reserve: `asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)`;
- async slot acquire блокируется, пока есть живые sync waiters;
- при освобождении или cleanup слота публикуется slot-release notification;
- async submit сохраняет идемпотентность по `clientService + externalId` только для `CALLBACK` и `POLLING`;
- повторный submit с тем же payload, priority и deliveryMode возвращает существующую задачу;
- повторный submit с отличающимися полями возвращает `IDEMPOTENCY_CONFLICT`;
- async claim выбирает задачу по `priority_weight DESC, available_at ASC, id ASC` и использует `FOR UPDATE SKIP LOCKED`;
- async processing transaction удерживает row-lock до финального обновления задачи;
- падение JVM до commit async processing transaction не должно оставлять committed `IN_PROGRESS` задачу;
- callback claim выбирает delivery по `available_at ASC, created_at ASC, delivery_id ASC` и использует `FOR UPDATE SKIP LOCKED`;
- callback retry/dead, recovery зависших `DELIVERING` и статистика должны совпадать с текущими статусными правилами;
- Liquibase остается источником схемы, JPA не должен создавать или менять таблицы автоматически.

## Архитектурное ТЗ

1. Добавить зависимость `spring-boot-starter-data-jpa` в модуль `test-qwen-cli-app`.

2. Создать пакет `com.example.testqwencli.gateway.repository.postgresjpa` со структурой:

```text
postgresjpa/
  config/
    PostgresJpaRepositoryConfiguration.java
  entity/
    SlotEntity.java
    SyncWaiterEntity.java
    RequestQueueEntity.java
    CallbackDeliveryEntity.java
  data/
    SlotJpaRepository.java
    SyncWaiterJpaRepository.java
    RequestQueueJpaRepository.java
    CallbackDeliveryJpaRepository.java
  command/
    SlotJpaCommands.java
    AsyncTaskJpaCommands.java
    CallbackDeliveryJpaCommands.java
  mapper/
    PostgresJpaRepositoryMapper.java
  PostgresJpaSlotRepository.java
  PostgresJpaAsyncTaskRepository.java
  PostgresJpaCallbackDeliveryRepository.java
```

3. Оставить доменные модели текущими records/classes из `gateway.model.*`. JPA entities не должны протекать в сервисный слой.

4. Настроить отдельный JPA infrastructure для gateway datasource:

- использовать существующий `externalGatewayDataSource`;
- добавить `LocalContainerEntityManagerFactoryBean` с packagesToScan только для `postgresjpa.entity`;
- добавить `JpaTransactionManager` с именем вроде `externalGatewayJpaTransactionManager`;
- включить `@EnableJpaRepositories` только для `postgresjpa.data`;
- выставить Hibernate `hbm2ddl.auto=validate` или `none`, без генерации схемы;
- настроить schema через `hibernate.default_schema` из `ExternalGatewayPostgresProperties.schema()`.

5. Развести режимы:

- `memory` остается дефолтом;
- `postgres` оставляет текущую JDBC-реализацию;
- `postgres-jpa` включает JPA-адаптеры;
- общая PostgreSQL-инфраструктура DataSource/Liquibase/LISTEN/NOTIFY должна включаться и для `postgres`, и для `postgres-jpa`.

6. Для простых операций использовать Spring Data JPA Repository:

- lookup по `taskId`, `externalId`, `taskId` callback;
- базовые `count` и агрегирующие projections, если JPQL не ухудшает запрос;
- insert sync waiter и delete waiter;
- entity mapping enum/string, timestamps и UUID.

7. Для критичных операций использовать custom fragments/native query:

- slot acquire с CTE, `FOR UPDATE SKIP LOCKED`, `UPDATE ... RETURNING`;
- lock all slot rows для async reserve;
- async submit с `ON CONFLICT ... DO NOTHING ... RETURNING`;
- async claim с CTE и `UPDATE ... RETURNING`;
- complete/fail/return/callback status conditional updates с `RETURNING`;
- callback upsert с `ON CONFLICT (task_id) DO UPDATE`;
- callback claim с `jsonb_set` нового `eventId`;
- callback recovery bulk update с `RETURNING`.

8. JSONB-маппинг:

- предпочтительно проверить Hibernate 6 JSON mapping через `@JdbcTypeCode(SqlTypes.JSON)`;
- если mapping `Map<String, Object>` и DTO нестабилен, использовать `AttributeConverter` или хранить JSON как `String` внутри entity/commands, как сейчас делает `PostgresJsonMapper`;
- не менять внешний тип payload/result/error в доменных моделях.

9. Транзакции:

- public adapter methods пометить `@Transactional(transactionManager = "externalGatewayJpaTransactionManager")` или выполнять через отдельный `TransactionTemplate`;
- для slot операций сохранить `REQUIRES_NEW`, чтобы committed lease был виден dashboard во время долгого upstream-вызова;
- для async processing transaction сохранить текущую семантику: claim и финальный status update в одной транзакции;
- избегать удержания лишних managed entities в persistence context при долгих upstream-вызовах.

10. Совместимость:

- не менять Liquibase changelog, если JPA mapping ложится на существующую схему;
- не менять HTTP API и сервисные классы;
- не удалять JDBC-реализацию;
- не переиспользовать JPA entities как DTO ответов.

## Плюсы JPA-варианта

- Меньше ручного row mapping для простых чтений и CRUD-операций.
- Явная entity-модель таблиц `ext_slots`, `ext_sync_waiters`, `ext_request_queue`, `ext_callback_delivery`.
- Можно вынести часть find/count методов в типизированные Spring Data interfaces и projections.
- Единый подход к транзакциям через `@Transactional` и Spring Data custom fragments.
- Проще расширять простые read-сценарии, фильтры и dashboard-запросы.
- Можно получить schema validation от Hibernate на старте, если включить `hbm2ddl.auto=validate`.

## Минусы и риски

- Самые важные операции очереди и слотов все равно останутся native SQL, иначе потеряется атомарность и `SKIP LOCKED`.
- `UPDATE ... RETURNING` и `INSERT ... ON CONFLICT ... RETURNING` не являются переносимым JPA-поведением; реализация будет зависеть от Hibernate/PostgreSQL.
- У JPA есть persistence context, dirty checking и auto flush. Для очередей это не дает пользы, но добавляет overhead и риск неожиданного flush перед query.
- Обычный `save` может дать больше SQL-команд, чем текущий single-statement JDBC-подход.
- Динамическая schema сложнее, чем в текущем `PostgresTableNames`.
- JSONB mapping может оказаться менее предсказуемым, чем текущая явная сериализация через `ObjectMapper`.
- Долгая async processing transaction с EntityManager требует осторожности, чтобы не держать лишние managed objects и не получить устаревшее состояние.
- LISTEN/NOTIFY все равно требует JDBC/DataSource-level доступа или отдельной native-команды.
- Повышается сложность конфигурации: появятся отдельные `EntityManagerFactory`, `JpaTransactionManager`, repository scan и настройки Hibernate.

## Оценка производительности

Оценка предварительная, без benchmark на этом репозитории.

Если JPA-реализация сохранит текущие native SQL для горячих операций, разница должна быть умеренной:

- latency отдельных repository-вызовов может вырасти примерно на 3-10% из-за Hibernate session, mapping и flush checks;
- throughput горячих DB-only сценариев может снизиться примерно на 5-15%;
- на реальном end-to-end пути с внешним upstream latency эта разница может быть почти незаметна, потому что сетевой вызов будет доминировать.

Если переписать горячие операции на идиоматичный JPA `select entity -> mutate -> save/flush`, ожидаемая деградация заметнее:

- claim/acquire может потребовать 2-4 SQL round trip вместо одного `UPDATE ... RETURNING`;
- под конкуренцией p95 latency может вырасти в 1.5-2 раза из-за более долгих блокировок и дополнительных запросов;
- throughput dispatcher/slot acquire может снизиться на 20-50% в DB-bound нагрузке;
- вероятность race condition и state conflict выше, если не использовать явные locks/native SQL.

Для простых lookup/statistics-запросов разница между JDBC и JPA обычно небольшая, если использовать projections и не загружать лишние entities. Для очередей и lease-слотов кэш JPA почти бесполезен: строки часто меняются, а корректность важнее повторного чтения из persistence context.

Предварительный вывод: JPA-адаптер можно добавить как экспериментальную альтернативу и улучшение структуры mapping, но текущая JDBC-реализация должна оставаться предпочтительной для production, пока JPA-версия не пройдет parity и нагрузочные тесты.

## План работ

1. Подготовить конфигурацию:
   - добавить `spring-boot-starter-data-jpa`;
   - расширить условие PostgreSQL infrastructure на `postgres` и `postgres-jpa`;
   - добавить JPA EntityManagerFactory и transaction manager только для `postgres-jpa`;
   - добавить property-пример в `application.properties` и `application-postgres.properties` без переключения дефолта.

2. Описать entities:
   - замапить существующие таблицы и колонки без изменения Liquibase;
   - enum хранить как `String`;
   - timestamps хранить как `Instant`;
   - JSONB сначала реализовать самым предсказуемым способом и покрыть тестами сериализации.

3. Реализовать Spring Data repository interfaces:
   - CRUD/find для slots, waiters, request queue, callback delivery;
   - projections для stats;
   - custom fragments для native SQL.

4. Реализовать adapter-классы:
   - `PostgresJpaSlotRepository implements SlotRepository`;
   - `PostgresJpaAsyncTaskRepository implements AsyncTaskRepository`;
   - `PostgresJpaCallbackDeliveryRepository implements CallbackDeliveryRepository`;
   - вынести mapping entity/native rows в отдельный mapper.

5. Сохранить PostgreSQL-specific команды:
   - перенести текущие SQL-сценарии без изменения алгоритма;
   - проверить, что native `RETURNING` корректно маппится через Hibernate;
   - если Spring Data `@Query` плохо работает с `RETURNING`, использовать `EntityManager.createNativeQuery(...)`.

6. Добавить тесты:
   - общий repository contract suite для memory, postgres JDBC и postgres JPA;
   - PostgreSQL integration tests на реальной БД или Testcontainers;
   - concurrency tests для `claimNextPending`, slot acquire/release, callback claim;
   - тест старта Spring context с `external-gateway.repository.type=postgres-jpa`;
   - тест schema validation, чтобы JPA mapping совпадал с Liquibase.

7. Добавить performance-проверку:
   - сравнить JDBC и JPA на одинаковой БД, pool size и настройках;
   - сценарии: slot acquire/release, async submit duplicate/new, async claim/complete, callback claim/mark;
   - метрики: throughput, p50/p95/p99 latency, количество SQL round trip, lock wait, CPU приложения;
   - зафиксировать результат в отдельном отчете перед решением о production-переключении.

8. Обновить документацию:
   - описать режим `postgres-jpa`;
   - описать ограничения JPA-варианта и native SQL exceptions;
   - добавить rollback: переключить `external-gateway.repository.type` обратно на `postgres`.

## Критерии приемки

- `mvn test` проходит.
- В режиме `postgres-jpa` поднимается Spring context.
- JPA-адаптеры проходят те же behavioral tests, что memory и JDBC PostgreSQL.
- PostgreSQL integration tests подтверждают `FOR UPDATE SKIP LOCKED`, idempotency conflict, retry/dead transitions и callback recovery.
- Нагрузочная проверка показывает деградацию не выше согласованного порога. Предлагаемый первичный порог: не больше 15% throughput loss для hot-path native JPA-варианта относительно JDBC.
- JDBC-режим `postgres` остается рабочим и не меняет поведение.

## Рекомендация

Добавлять JPA-реализацию стоит как параллельный экспериментальный режим `postgres-jpa`, а не как замену текущего `postgres`. Основная польза будет в типизированном mapping и более удобных read/CRUD-операциях. Для критичных queue/lease-команд нужно сохранить native SQL, потому что именно он обеспечивает текущую атомарность, низкое число round trip и корректную работу под конкурентной нагрузкой.
