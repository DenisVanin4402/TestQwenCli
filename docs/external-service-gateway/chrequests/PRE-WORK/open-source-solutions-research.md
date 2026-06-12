# Исследование open-source решений для external-service-gateway

Дата исследования: 2026-06-12.

## Контекст системы

`external-service-gateway` нужен как внутренний Spring Boot gateway к внешнему HTTP-сервису с жестким глобальным лимитом:

```text
не более 5 одновременно выполняющихся upstream-вызовов во всем production-кластере
```

По текущей архитектурной документации gateway централизует:

- sync API с ожиданием слота и возвратом результата в исходном HTTP-запросе;
- async submit с durable state, polling fallback, cancel и manual retry;
- общий лимит слотов между sync и async;
- приоритет sync над стартом новых async-задач;
- retry/backoff upstream-вызовов для async;
- HTTP callback в сервисы-клиенты после финального async-статуса;
- отдельную очередь callback-доставки с собственным retry/dead lifecycle;
- идемпотентность async submit по `clientService + externalId`;
- диагностику через dashboard и будущие метрики/алерты.

Ключевая особенность задачи: это не обычный rate limit "N запросов в секунду", а строгий кластерный concurrency limit на in-flight HTTP-вызовы плюс durable workflow вокруг async-результата.

## Критерии нашего решения

Оценка ниже использует критерии, выведенные из архитектурных документов и ADR.

| Код | Критерий | Почему важен |
| --- | --- | --- |
| C1 | Строгий глобальный concurrency limit `5` между всеми replicas | Нарушение лимита ломает контракт с внешним сервисом. |
| C2 | Единый лимит для sync и async с приоритетом sync перед стартом async | Sync latency важнее async throughput; уже начатый async не вытесняется. |
| C3 | Sync path без durable queue, но с bounded wait и `429 Retry-After` при нехватке слота | Sync клиенту нужен быстрый ответ или явный backpressure. |
| C4 | Durable async queue с priority, retry/backoff, cancel, manual retry и idempotency | Async-задачи не должны теряться при рестартах и дублях submit. |
| C5 | Хранение результата и финального error contract для polling и callback | Клиент может восстановиться через GET даже при проблемах callback. |
| C6 | Отдельная callback delivery queue, retry/dead отдельно от upstream-статуса | Ошибка доставки результата не должна менять результат upstream-задачи. |
| C7 | PostgreSQL-only v1 без обязательного Kafka/RabbitMQ/Temporal/Redis/ZooKeeper | Текущая нагрузка малая, ADR-002 выбирает PostgreSQL как coordinator v1. |
| C8 | Spring Boot / Java 21 / REST/OpenAPI-friendly интеграция | Текущий код и сервисы-клиенты находятся в Spring/HTTP модели. |
| C9 | Production observability и понятные operations | Нужны health, backlog, slot metrics, dead task diagnostics и rollback-путь. |
| C10 | Заменяемость внутренней очереди без изменения gateway API | ADR-010 требует, чтобы клиенты не зависели от внутренних таблиц. |

Шкалы:

- `Распространенность`: ранг по GitHub stars как приближенный публичный proxy. Это не равно enterprise adoption, но дает сравнимую метрику.
- `Сложность`: 1 - библиотека на несколько классов, 5 - отдельная платформа/кластер и новая операционная модель.
- `Поддержка`: `высокая`, `средняя`, `низкая` с учетом фонда/компании, активности репозитория, документации и коммерческого/LTS контура.
- `Соответствие`: 0-5, где 5 означает закрытие почти всех критериев без существенного custom layer.

## Главный вывод

Готового open-source решения, которое один-в-один закрывает `REST sync + durable async + callback + строгий общий in-flight limit 5 + sync priority + PostgreSQL-only v1`, не найдено.

Ближайшие классы решений:

1. `PostgreSQL custom coordinator` - лучше всего соответствует текущим требованиям и уже выбран ADR-002. Он требует собственной реализации, но минимизирует инфраструктуру и точно выражает sync/async policy.
2. `db-scheduler` или `JobRunr` - хорошие кандидаты, если нужно заменить часть собственного async scheduler/job execution, но они не закрывают sync priority, публичный gateway API и callback state без custom layer.
3. `Temporal` или `Conductor` - сильные workflow engines для durable execution, retry и visibility, но они вводят отдельную платформу и не дают из коробки нужный sync path и строгий общий upstream concurrency с sync reserve.
4. `RabbitMQ` - сильный durable broker, но при текущей постановке добавляет инфраструктуру и оставляет custom реализацию slot policy, callback state, idempotency и REST facade.
5. `Kong`, `APISIX`, `Envoy`, `Spring Cloud Gateway` - полезны как внешний API/proxy слой, но не заменяют application gateway с durable async/result/callback semantics.
6. `Resilience4j`, `Bucket4j`, `Redisson`, `Curator`, `ShedLock` - решают отдельные примитивы: local bulkhead, token bucket, distributed semaphore/lock. Они не являются end-to-end решением.

## Сравнительная таблица

| Решение | Класс | GitHub stars / forks на 2026-06-12 | Ранг распространенности | Сложность | Поддержка | Соответствие | Что закрывает | Главные разрывы относительно наших требований |
| --- | --- | ---: | ---: | --- | --- | ---: | --- | --- |
| Текущая Spring Boot + PostgreSQL модель gateway | Custom application gateway | N/A | N/A | 3 | внутренняя | 5.0 | C1-C10 проектируются явно: lease slots, sync waiters, async queue, callback queue, REST/OpenAPI. | Нужно самим закрыть P0 production gaps: real upstream HTTP client, service identity, observability, retention, deployment. |
| `Kong/kong` | API gateway | 43 575 / 5 151 | 1 | 4 | высокая | 2.2 | Routing, plugins, rate limiting, auth, ops proxy layer. | Rate limit не равен durable async orchestration; нет sync priority, result storage, callback delivery state. |
| `apache/kafka` | Event streaming platform | 32 791 / 15 273 | 2 | 5 | высокая | 2.6 | Durable event log, consumer groups, replay, high throughput. | Не request queue для sync path; strict global in-flight 5, callback result API и cancel/retry надо строить отдельно. |
| `conductor-oss/conductor` | Workflow engine | 31 933 / 929 | 3 | 5 | средняя-высокая | 3.2 | Durable workflow/task model, retries, timeouts, HTTP/system tasks, status listener. | Отдельная платформа; REST facade, sync bounded wait, sync reserve и exact upstream slots требуют custom design. |
| `envoyproxy/envoy` | Proxy / service mesh data plane | 28 378 / 5 426 | 4 | 4 | высокая | 2.2 | Global rate limit filter, adaptive concurrency, circuit breaking, observability. | Proxy не хранит durable async state/result/callback; adaptive concurrency не является фиксированным lease pool 5 с sync priority. |
| `redisson/redisson` | Redis/Valkey Java primitives | 24 356 / 5 485 | 5 | 3 | средняя-высокая | 2.2 | Distributed semaphore/locks, permit lease, queues, Redis-backed coordination. | Требует Redis/Valkey; решает примитив слота, но не API, async queue semantics, callback delivery и Postgres-only v1. |
| `temporalio/temporal` | Durable execution workflow engine | 20 931 / 1 654 | 6 | 5 | высокая | 3.3 | Durable workflows, activities, retries, event history, worker slots, visibility. | Отдельный Temporal cluster; exact global upstream concurrency и sync priority не являются готовым бизнес-contract. |
| `apache/apisix` | API gateway | 16 715 / 2 884 | 7 | 4 | высокая | 2.3 | `limit-conn`, `limit-count`, Redis-backed policies, routing/plugins. | Нет durable async/result/callback state; concurrency keying не выражает нашу mixed sync/async policy. |
| `rabbitmq/rabbitmq-server` | Message broker | 13 705 / 4 008 | 8 | 4 | высокая | 3.4 | Durable queues, acknowledgements, prefetch, priority queues, DLX, retry patterns. | Дополнительный broker; sync path и общий slot reserve между sync/async остаются custom; result storage/callback state нужны в gateway. |
| `resilience4j/resilience4j` | Java resilience library | 10 684 / 1 467 | 9 | 1 | средняя-высокая | 2.0 | Local bulkhead, rate limiter, circuit breaker, retry, time limiter, Micrometer. | Local JVM state, не кластерный coordinator; нет durable queue/callback/idempotency. Хорош как компонент real upstream client. |
| `quartz-scheduler/quartz` | Java scheduler | 6 727 / 1 982 | 10 | 3 | средняя | 2.6 | JDBC clustered scheduling, failover, job recovery. | Ориентирован на scheduled jobs, а не request queue/result contract; cluster-wide lock может деградировать; sync path/callback custom. |
| `apache/camel` | Integration framework | 6 230 / 5 130 | 11 | 4 | высокая | 3.0 | Throttle EIP, concurrent request throttle, retry/dead letter, HTTP/JMS/SQL components, Spring Boot. | Фреймворк маршрутов, не готовый gateway; strict Postgres slot lease, sync reserve, API models и callback state надо проектировать. |
| `spring-cloud/spring-cloud-gateway` | Spring API gateway | 4 878 / 3 456 | 12 | 3 | высокая | 2.4 | Spring Boot routing, filters, Redis/Bucket4j rate limiter, circuit breaker filter. | Rate limit per request/time, не durable async orchestration; нет result/callback state и mixed sync/async slot policy. |
| `lukas-krecan/ShedLock` | Distributed lock for scheduled tasks | 4 170 / 569 | 13 | 1 | средняя | 1.5 | Prevents same scheduled task running concurrently across nodes via external store. | Автор прямо указывает, что это не distributed scheduler; skips вместо queue; нет request-level slots, async state, callback. |
| `apache/curator` | ZooKeeper coordination recipes | 3 174 / 1 246 | 14 | 4 | высокая | 2.0 | Inter-process semaphore across JVMs, leases auto-released on session loss, distributed queues. | Требует ZooKeeper; решает coordination primitive, но не Postgres-only queue/result/callback/API. |
| `jobrunr/jobrunr` | JVM background jobs | 3 000 / 319 | 15 | 2-3 | средняя | 3.6 | Persistent jobs in existing DB, distributed execution, automatic retries, dashboard, Spring Boot integration. | Часть нужных возможностей вроде priority queues, rate limiters, mutexes и job result вынесена в Pro; sync path and slot reserve custom. |
| `bucket4j/bucket4j` | Java token-bucket rate limiter | 2 759 / 321 | 16 | 2 | средняя | 1.8 | Precise local/distributed token bucket, JDBC/Redis/Hazelcast integrations. | Token bucket rate limiting не равно in-flight concurrency lease; нет durable workflow/callback. |
| `spring-projects/spring-integration` | Spring EIP framework | 1 626 / 1 149 | 17 | 3 | высокая | 3.1 | Message channels, JDBC message store, retry advice, circuit breaker advice, rate limiter advice, HTTP adapters. | JDBC queue docs сами рекомендуют broker для очередей при возможности; gateway API, slot lease и callback status надо строить custom. |
| `kagkarlsson/db-scheduler` | DB-backed Java scheduler | 1 570 / 246 | 18 | 2 | средняя | 3.8 | Cluster-friendly persistent tasks in one DB table, single execution, retries/backoff examples, Java/Spring Boot. | Scheduler, а не full request gateway; нет sync path, result API, callback delivery queue, idempotency conflict model. |

## Ранжирование по распространенности

Ранг рассчитан по `stargazers_count` из GitHub API на 2026-06-12. Для инфраструктурных продуктов GitHub stars не отражают полностью production adoption: RabbitMQ, Kafka, Envoy, Spring и Apache Camel могут быть существенно распространеннее, чем видно по одному репозиторию.

| Ранг | Репозиторий | Stars | Forks | Последний push на момент проверки |
| ---: | --- | ---: | ---: | --- |
| 1 | `Kong/kong` | 43 575 | 5 151 | 2026-06-10 |
| 2 | `apache/kafka` | 32 791 | 15 273 | 2026-06-12 |
| 3 | `conductor-oss/conductor` | 31 933 | 929 | 2026-06-12 |
| 4 | `envoyproxy/envoy` | 28 378 | 5 426 | 2026-06-12 |
| 5 | `redisson/redisson` | 24 356 | 5 485 | 2026-06-12 |
| 6 | `temporalio/temporal` | 20 931 | 1 654 | 2026-06-12 |
| 7 | `apache/apisix` | 16 715 | 2 884 | 2026-06-12 |
| 8 | `rabbitmq/rabbitmq-server` | 13 705 | 4 008 | 2026-06-12 |
| 9 | `resilience4j/resilience4j` | 10 684 | 1 467 | 2026-05-22 |
| 10 | `quartz-scheduler/quartz` | 6 727 | 1 982 | 2026-05-13 |
| 11 | `apache/camel` | 6 230 | 5 130 | 2026-06-12 |
| 12 | `spring-cloud/spring-cloud-gateway` | 4 878 | 3 456 | 2026-06-12 |
| 13 | `lukas-krecan/ShedLock` | 4 170 | 569 | 2026-06-11 |
| 14 | `apache/curator` | 3 174 | 1 246 | 2026-03-16 |
| 15 | `jobrunr/jobrunr` | 3 000 | 319 | 2026-06-11 |
| 16 | `bucket4j/bucket4j` | 2 759 | 321 | 2026-05-20 |
| 17 | `spring-projects/spring-integration` | 1 626 | 1 149 | 2026-06-11 |
| 18 | `kagkarlsson/db-scheduler` | 1 570 | 246 | 2026-06-08 |

## Ранжирование по соответствию требованиям

| Ранг | Решение | Соответствие | Комментарий |
| ---: | --- | ---: | --- |
| 1 | Текущая Spring Boot + PostgreSQL модель gateway | 5.0 | Единственное решение, явно выражающее все проектные инварианты. |
| 2 | `db-scheduler` | 3.8 | Лучший OSS-кандидат для замены части async scheduling при сохранении PostgreSQL-only v1. |
| 3 | `JobRunr` | 3.6 | Хорош для durable jobs, но часть нужных enterprise primitives находится вне OSS-core. |
| 4 | `RabbitMQ` | 3.4 | Хороший broker, если разрешена новая инфраструктура и custom gateway остается владельцем slot policy. |
| 5 | `Temporal` | 3.3 | Силен для durable workflows, но слишком тяжел для текущей v1-постановки и не заменяет sync API. |
| 6 | `Conductor` | 3.2 | Похож на Temporal по классу, но JSON/workflow-platform модель далека от текущего компактного gateway. |
| 7 | `Spring Integration` | 3.1 | Хорошо ложится на Spring, но это toolkit, а не готовый gateway. |
| 8 | `Apache Camel` | 3.0 | Богатый integration framework, но добавляет DSL/runtime complexity. |
| 9 | `Quartz` | 2.6 | Кластерный JDBC scheduler, но не request/result/callback gateway. |
| 10 | `Kafka` | 2.6 | Сильная event platform, но не естественный выбор для малой request/reply нагрузки и sync path. |
| 11 | `Spring Cloud Gateway` | 2.4 | Может быть front proxy, но не core workflow/queue layer. |
| 12 | `APISIX` | 2.3 | Сильный proxy-level concurrency/rate limiter, не durable gateway. |
| 13 | `Kong` | 2.2 | Аналогично APISIX: proxy/plugin layer, не workflow/result state. |
| 14 | `Envoy` | 2.2 | Силен как data plane, но не application state machine. |
| 15 | `Redisson` | 2.2 | Можно построить distributed semaphore, но это новая Redis-зависимость и много custom logic. |
| 16 | `Resilience4j` | 2.0 | Нужен как компонент upstream client, не как coordinator. |
| 17 | `Curator` | 2.0 | Семофор надежен, но ZooKeeper ради 5 слотов избыточен. |
| 18 | `Bucket4j` | 1.8 | Хороший token bucket, но задача про in-flight lease. |
| 19 | `ShedLock` | 1.5 | Lock for scheduled tasks, не queue/gateway. |

## Ранжирование по сложности работы

| Сложность | Решения | Практический смысл |
| --- | --- | --- |
| 1 | `Resilience4j`, `ShedLock` | Легко подключить, но покрывают только узкие примитивы. |
| 2 | `Bucket4j`, `db-scheduler` | Встраиваемые Java libraries; подходят для частичных задач. |
| 2-3 | `JobRunr` | Встраиваемая job library с dashboard и storage schema; нужно проверить OSS/Pro границы. |
| 3 | Текущая PostgreSQL модель, `Quartz`, `Spring Integration`, `Spring Cloud Gateway` | Умеренная сложность, но различается объем custom logic. |
| 4 | `RabbitMQ`, `Apache Camel`, `Kong`, `APISIX`, `Envoy`, `Redisson`, `Curator` | Требуют отдельной инфраструктуры или существенного runtime/DSL/ops слоя. |
| 5 | `Kafka`, `Temporal`, `Conductor` | Отдельные платформы с новой operational model; оправданы при большем масштабе или сложных workflows. |

## Ранжирование по поддержке

| Уровень | Решения | Обоснование |
| --- | --- | --- |
| Высокая | `Kafka`, `Camel`, `Curator`, `APISIX`, `Envoy`, `Spring Cloud Gateway`, `Spring Integration`, `RabbitMQ`, `Kong`, `Temporal` | Apache/CNCF/Spring/Broadcom/Kong/Temporal ecosystems, активные релизы, документация, большая база пользователей. |
| Средняя-высокая | `Conductor`, `Resilience4j`, `Redisson` | Активные проекты с заметным сообществом, но adoption/support модель уже более проектно-специфична. |
| Средняя | `JobRunr`, `db-scheduler`, `Quartz`, `ShedLock`, `Bucket4j` | Поддерживаемые библиотеки, но меньше core maintainers или более узкая область применения. |
| Внутренняя | Текущая PostgreSQL модель gateway | Поддержка полностью на нашей команде, но зато поведение соответствует доменному контракту. |

## Разбор классов решений

### API gateway и proxy-level rate limiting

`Kong`, `APISIX`, `Envoy` и `Spring Cloud Gateway` решают routing, authentication, filters, rate limiting, request rejection и часть observability. У `APISIX` есть `limit-conn`, у `Envoy` есть global rate limit filter и adaptive concurrency, у `Spring Cloud Gateway` есть `RequestRateLimiter` с Redis/Bucket4j.

Эти решения полезны как фронт gateway или service mesh layer, но они не владеют нашим durable state:

- не создают async task с `clientService + externalId`;
- не хранят result/error для polling;
- не доставляют callback с отдельным retry/dead lifecycle;
- не реализуют priority queue и manual retry/cancel semantics;
- не выражают правило `async не стартует при live sync waiters`.

Итог: не подходят как замена core gateway, но могут быть использованы вокруг него для auth, routing, TLS, coarse rate limiting и стандартной proxy observability.

### Resilience и rate limiter libraries

`Resilience4j` хорошо подходит для real upstream HTTP client:

- circuit breaker;
- time limiter;
- retry;
- local bulkhead;
- Micrometer integration.

`Bucket4j` хорош для token-bucket rate limiting, в том числе распределенного через JDBC/Redis/Hazelcast. Но token bucket контролирует rate/allowance, а не количество удерживаемых in-flight HTTP-вызовов с lease release в `finally`.

Итог: эти библиотеки следует рассматривать как дополнительные компоненты, а не как замену PostgreSQL coordinator. Для P0 "real upstream HTTP client" `Resilience4j` выглядит наиболее полезным.

### Distributed semaphore и lock primitives

`Redisson` и `Apache Curator` умеют distributed semaphores. `Curator InterProcessSemaphoreV2` прямо описан как counting semaphore across JVMs; `Redisson PermitExpirableSemaphore` дает permit id и lease time.

Они похожи на наш `ext_slots`, но требуют Redis/Valkey или ZooKeeper. При этом весь остальной слой остается нашим:

- sync waiters и sync reserve;
- async queue и idempotency;
- callback delivery queue;
- dashboard state;
- retention и support operations.

`ShedLock` еще уже: он предотвращает параллельный запуск scheduled task, но сам проект явно указывает, что это не distributed scheduler.

Итог: можно использовать при переходе на Redis/ZooKeeper coordination, но для текущего ADR-002 это избыточно.

### Brokers: RabbitMQ и Kafka

`RabbitMQ` хорошо решает durable queue, acknowledgements, prefetch, priority и DLX. Для async части он был бы естественнее PostgreSQL при высокой нагрузке или если broker уже является платформенным стандартом.

Но для нашей задачи все равно нужно писать application gateway:

- sync path не должен попадать в очередь;
- sync reserve должен влиять на старт async;
- результат надо хранить для polling;
- callback delivery имеет отдельный state machine;
- cancel/manual retry/idempotency scoped by `clientService + externalId` остаются доменной логикой gateway.

`Kafka` еще менее прямое соответствие: это event log и streaming platform, а не per-request durable work queue с cancel/manual retry/result read. Kafka может быть полезна для событий о завершении задач, аудита или будущего event-driven контракта, но не как простая замена текущей очереди.

Итог: RabbitMQ - возможная evolution path, если нагрузка или platform policy оправдают broker. Kafka - нецелевой вариант для текущей малой request/reply задачи.

### Workflow engines: Temporal и Conductor

`Temporal` и `Conductor` сильны там, где нужен durable execution, долгие workflow, таймеры, retries, visibility, signals/events и восстановление после отказов. Они хорошо покрывают классы задач "вызвать внешний сервис, подождать, повторить, уведомить".

Но в нашем случае они вводят отдельную платформу ради относительно маленькой интеграционной задачи:

- текущая нагрузка около 1500 запросов/день и пики 10-20/мин;
- v1 ADR выбирает PostgreSQL, а не workflow cluster;
- публичный REST/OpenAPI contract уже отделен от внутренней очереди;
- strict global upstream concurrency и sync priority надо выражать отдельно, через worker slots, task queues, custom slot supplier или внешний semaphore;
- callback URL allow-list и scoped polling/cancel/retry остаются gateway-level concerns.

Итог: Workflow engine оправдан, если gateway превращается в набор сложных многошаговых бизнес/integration workflows. Для текущего v1 это слишком тяжелый базовый слой.

### Integration frameworks: Apache Camel и Spring Integration

`Apache Camel` и `Spring Integration` предоставляют Enterprise Integration Patterns: throttling, retry, dead letter, HTTP/JDBC/JMS adapters, message channels, service activators. Они ближе к нашей задаче, чем API gateway products, потому что работают на уровне application integration.

Однако они не дают готовой предметной модели:

- exact slot lease rows;
- sync waiters;
- async idempotency conflict;
- callback delivery state;
- OpenAPI DTO и error contract;
- dashboard-specific health snapshot.

`Spring Integration` проще вписывается в Spring Boot, но JDBC message store docs прямо предупреждают, что database-backed queue не всегда лучший выбор и при возможности лучше рассмотреть JMS/AMQP. Для нашего малого объема PostgreSQL допустим, но уже реализованная schema точнее выражает доменную state machine.

Итог: полезны как inspiration или как toolkit при будущем рефакторинге, но не снижают радикально сложность текущего gateway.

### Java DB-backed schedulers и job libraries

`db-scheduler` и `JobRunr` - наиболее близкий practical OSS-класс к текущему async dispatcher:

- persistent storage в существующей БД;
- cluster-friendly single execution;
- retries/backoff;
- Spring Boot integration;
- dashboard у JobRunr.

Разрывы:

- они не закрывают sync endpoint и sync priority;
- callback delivery с отдельным state все равно нужен;
- idempotency conflict по payload/priority/deliveryMode остается нашим API contract;
- текущие статусы `PENDING`, `IN_PROGRESS`, `DONE`, `DEAD`, `CANCELLED` и polling response надо маппить;
- `JobRunr` имеет важные advanced capabilities в Pro-навигации: priority queues, rate limiters, mutexes, job result.

Итог: если нужно уменьшить собственный scheduler-код, первым кандидатом стоит смотреть `db-scheduler` для async dispatch tick/claim. Но для текущей реализации уже есть доменная PostgreSQL queue, и миграция может не окупиться до появления новых требований.

## Рекомендация

Для v1 оставить текущую архитектуру `Spring Boot + PostgreSQL coordinator`, потому что она:

- единственная прямо покрывает строгий mixed sync/async concurrency policy;
- не добавляет брокер/workflow/Redis/ZooKeeper инфраструктуру;
- согласована с ADR-002, ADR-004, ADR-005 и ADR-010;
- соответствует малой текущей нагрузке;
- сохраняет возможность заменить внутреннюю очередь позже без изменения HTTP contract.

Практические заимствования из OSS:

1. Использовать `Resilience4j` для real upstream HTTP client: circuit breaker, time limiter, retry classification и Micrometer metrics.
2. Держать `db-scheduler` как кандидат на будущую замену части async scheduling, если custom scheduler начнет усложняться.
3. Рассмотреть `RabbitMQ` только если нагрузка/ops policy потребует broker или появятся другие сервисы-потребители событий результата.
4. Рассмотреть `Temporal`/`Conductor` только если gateway станет orchestration platform для многошаговых долгих workflows.
5. Использовать `Kong`/`APISIX`/`Envoy`/`Spring Cloud Gateway` только как внешний proxy/security/routing layer, не как замену core gateway state machine.

## Источники

Внутренняя документация:

- `docs/external-service-gateway/architecture/README.md`
- `docs/external-service-gateway/architecture/01-c4-context.md`
- `docs/external-service-gateway/architecture/02-c4-containers.md`
- `docs/external-service-gateway/architecture/03-c4-components.md`
- `docs/external-service-gateway/architecture/04-data-and-state.md`
- `docs/external-service-gateway/architecture/08-deployment-operations.md`
- `docs/external-service-gateway/architecture/09-production-readiness.md`
- `docs/external-service-gateway/architecture/decisions.md`
- `docs/external-service-gateway/chrequests/PRE-WORK/protocol-options.md`

Внешние источники:

- Envoy rate limit filter: https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/rate_limit_filter
- Envoy adaptive concurrency: https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/adaptive_concurrency_filter
- Kong rate limiting plugin: https://developer.konghq.com/plugins/rate-limiting/
- Apache APISIX `limit-conn`: https://apisix.apache.org/docs/apisix/plugins/limit-conn/
- Spring Cloud Gateway `RequestRateLimiter`: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html
- Resilience4j Bulkhead: https://resilience4j.readme.io/docs/bulkhead
- Resilience4j RateLimiter: https://resilience4j.readme.io/docs/ratelimiter
- Bucket4j reference: https://bucket4j.com/8.14.0/toc.html
- Redisson locks and synchronizers: https://redisson.pro/docs/data-and-services/locks-and-synchronizers/
- Apache Curator shared semaphore: https://curator.apache.org/docs/recipes-shared-semaphore/
- RabbitMQ queues: https://www.rabbitmq.com/docs/queues
- RabbitMQ consumer prefetch: https://www.rabbitmq.com/docs/consumer-prefetch
- RabbitMQ acknowledgements/confirms: https://www.rabbitmq.com/docs/confirms
- RabbitMQ dead letter exchanges: https://www.rabbitmq.com/docs/dlx
- Temporal workflows: https://docs.temporal.io/workflows
- Temporal activities: https://docs.temporal.io/activities
- Temporal worker performance: https://docs.temporal.io/develop/worker-performance
- Conductor workflow definition: https://conductor-oss.github.io/conductor/documentation/configuration/workflowdef/index.html
- Conductor task definition: https://conductor-oss.github.io/conductor/documentation/configuration/taskdef.html
- Apache Camel Throttle EIP: https://camel.apache.org/components/4.10.x/eips/throttle-eip.html
- Apache Camel Dead Letter Channel: https://camel.apache.org/components/4.10.x/eips/dead-letter-channel.html
- Apache Camel Resilience4j EIP: https://camel.apache.org/components/4.10.x/eips/resilience4j-eip.html
- Spring Integration overview: https://docs.spring.io/spring-integration/reference/overview.html
- Spring Integration JDBC message store: https://docs.spring.io/spring-integration/reference/jdbc/message-store.html
- Spring Integration advice classes: https://docs.spring.io/spring-integration/reference/handler-advice/classes.html
- JobRunr documentation: https://www.jobrunr.io/en/documentation/
- db-scheduler repository and docs: https://github.com/kagkarlsson/db-scheduler
- Quartz JDBC clustering: https://www.quartz-scheduler.org/documentation/quartz-2.3.0/configuration/ConfigJDBCJobStoreClustering.html
- ShedLock repository and docs: https://github.com/lukas-krecan/ShedLock
- GitHub API repository metadata for ranking: `https://api.github.com/repos/<owner>/<repo>`, checked 2026-06-12.
