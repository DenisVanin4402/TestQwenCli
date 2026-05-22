# Architecture Decisions

## ADR-001. Выделяем отдельный gateway-сервис

Решение: интеграция с внешним сервисом выносится в `external-service-gateway`.

Причины:

- внешний лимит `5 concurrent calls` общий для нескольких доменных сервисов;
- `invest-pay` и `user-expertise` не имеют общей схемы данных;
- общая таблица-лимитер в одной из доменных БД создала бы скрытую связанность;
- gateway централизует retry, audit, metrics и priority policy.

Последствия:

- появляется дополнительный внутренний сетевой hop;
- gateway становится критическим техническим сервисом;
- зато доменные сервисы не знают о таблицах лимитирования и очередях.

## ADR-002. PostgreSQL используется как координатор v1

Решение: для v1 используем PostgreSQL gateway-сервиса для lease-слотов и async-очереди.

Причины:

- ожидаемая нагрузка небольшая: около 1500 запросов/день, пики 10-20/мин;
- нужны транзакции, идемпотентность и persistent queue;
- `FOR UPDATE SKIP LOCKED` подходит для конкурентного claim;
- `LISTEN/NOTIFY` в режиме ожидания sync-слота уменьшает latency после освобождения слота без отдельного broker.

Последствия:

- нужно следить за autovacuum и очисткой старых задач;
- PostgreSQL не является message broker, поэтому `NOTIFY external_gateway_slot_released` используется только как wake-up для повторной проверки `ext_slots`;
- при росте нагрузки можно заменить queue layer на RabbitMQ/Kafka/Temporal, сохранив gateway API.

## ADR-003. Lease-запись вместо долгого DB lock

Решение: слот удерживается через поля `lease_id`, `owner`, `expires_at`, а не через открытую транзакцию с row lock.

Причины:

- HTTP-вызов не должен держать DB-транзакцию;
- долгие транзакции ухудшают работу PostgreSQL;
- rollback/commit автоматически отпускает lock, но нам нужно состояние "слот занят" вне транзакции.

Обязательное правило:

```text
release и heartbeat выполняются только по slot_id + lease_id
```

Это защищает от ситуации, где старый поток освобождает уже переиспользованный слот.

## ADR-004. Приоритет sync над async реализуется политикой старта

Решение:

```text
totalSlots = 5
targetFreeSyncSlots = 1
sync limit = 5
asyncAllowed = max(0, totalSlots - syncBusy - targetFreeSyncSlots)
async не стартует новые задачи при наличии живых sync waiters
```

Это скользящий sync reserve: если sync уже занял один слот, gateway старается оставить свободным следующий слот под еще один sync. Если active sync становится больше, допустимое количество новых async-вызовов уменьшается.

Причины:

- уже начатый async-вызов нельзя безопасно вытеснить;
- постоянно поддерживаемый свободный слот снижает latency следующего sync-запроса;
- активная sync-нагрузка постепенно вытесняет старт новых async-вызовов;
- sync waiters gate не дает async забрать следующий освободившийся слот.

Последствия:

- при отсутствии sync один слот может простаивать;
- при росте sync-нагрузки async throughput снижается сильнее, чем при статическом лимите `async <= 4`;
- уже запущенные async-вызовы не отменяются, поэтому резерв восстанавливается только по мере их завершения;
- это осознанная цена за приоритет sync;
- политику можно усложнить позже: time-based borrowing или временное разрешение async занять последний слот после долгого отсутствия sync.

## ADR-005. Async результат доставляется callback'ом, polling остается fallback

Решение: для async-задач основной способ доставки результата - HTTP callback из gateway в сервис-клиент. Polling через gateway API остается fallback/recovery-механизмом и отдельным режимом `deliveryMode=POLLING` для сервисов, которые не реализуют callback endpoint.

Причины:

- у нас нет Kafka/RabbitMQ и нет общей схемы БД между сервисами;
- сервис-клиент не должен регулярно опрашивать gateway, если результат можно доставить push-моделью;
- при ошибке callback результат не теряется, потому что остается в БД gateway;
- polling нужен для ручного восстановления, диагностики, повторного чтения результата и клиентов без callback endpoint.

Последствия:

- каждый сервис-клиент с `deliveryMode=CALLBACK` должен реализовать внутренний callback endpoint;
- для `deliveryMode=POLLING` callback-доставка не создается, а `callbackDeliveryStatus` равен `NOT_REQUIRED`;
- callback endpoint должен быть идемпотентным;
- gateway должен иметь отдельный retry/backoff для доставки callback;
- запись callback delivery создается атомарно с финальным статусом async-задачи, чтобы callback не потерялся при рестарте gateway;
- ошибка доставки callback не меняет статус upstream-задачи.

## ADR-006. Callback URL берется из allow-list конфигурации

Решение: gateway не принимает произвольный `callbackUrl` в async-запросе. Callback endpoint выбирается по `clientService`.

Пример:

```yaml
external-gateway:
  clients:
    user-expertise:
      callback-url: http://user-expertise/internal/external-gateway/callbacks
    invest-pay:
      callback-url: http://invest-pay/internal/external-gateway/callbacks
```

Причины:

- произвольный URL в payload создает SSRF-риск;
- gateway должен вызывать только доверенные внутренние сервисы;
- сетевые политики и mTLS проще настраивать на фиксированные сервисные маршруты.

Последствия:

- для нового сервиса-клиента с `deliveryMode=CALLBACK` нужна конфигурация в gateway;
- в запросе достаточно передать `clientService` и `deliveryMode`, но `clientService` обязательно сверяется с аутентифицированной service-to-service identity.

## ADR-007. Результат внешнего сервиса нормализуется в Map<String, String>

Решение: sync response, async callback и fallback GET возвращают успешный результат как `Map<String, String>`. Для финальных неуспешных async-статусов `result` равен `null`, а причина передается в структурированном поле `error`. Строковое `lastError` может использоваться только как диагностическое краткое описание.

Причины:

- контракт простой для Java/Spring сервисов;
- результат можно безопасно хранить как JSONB;
- сервисы-клиенты не зависят от полной структуры upstream-ответа;
- в callback можно передавать результат без дополнительного запроса к gateway.

Последствия:

- gateway должен иметь mapping layer из upstream response в `Map<String, String>`;
- если upstream вернет вложенную структуру, ее нужно либо flatten'ить, либо явно отбросить лишние поля;
- ключи результата должны быть стабильными и документированными для конкретной операции.

## ADR-008. OpenAPI разделяется на sync, async и callback

Решение: поддерживаем отдельные OpenAPI-файлы для входящих gateway API и исходящего callback-контракта.

Причины:

- sync и async имеют разные SLA и жизненный цикл;
- async имеет статусы, fallback-чтение результата, cancel/retry;
- callback реализуют сервисы-клиенты, а вызывает его gateway;
- потребители могут подключать только нужный контракт.

Файлы:

- `../openapi/external-gateway-sync.yaml`
- `../openapi/external-gateway-async.yaml`
- `../openapi/external-gateway-callback.yaml`

## ADR-009. Gateway API стабилен, внутренняя очередь заменяема

Решение: потребители интегрируются только через HTTP API gateway. Они не имеют доступа к таблицам gateway.

Причины:

- можно заменить PostgreSQL queue на RabbitMQ/Temporal без изменения доменных сервисов;
- можно менять retry и priority policy внутри gateway;
- ownership остается четким.

## ADR-010. REST/OpenAPI - единственный протокол v1

Решение: v1 проектируется как REST/OpenAPI API с HTTP callback для доставки async-результата. Другие внутренние протоколы не входят в документацию и контракт v1.

Причины:

- проще подключить существующие Spring Boot сервисы;
- проще отлаживать через curl/Postman/logs;
- OpenAPI лучше подходит для callback endpoint, который должны реализовать разные сервисы;
- текущая нагрузка не требует высокой пропускной способности протокола.

Детали REST-контракта описаны в `protocol-options.md`.
