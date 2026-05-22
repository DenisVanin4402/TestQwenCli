# Implementation Roadmap

Документ описывает порядок разработки `external-service-gateway`. На этом этапе код не пишется; файл нужен как чек-лист для будущей имплементации.

## Шаг 0. Зафиксировать контракты

Результат:

- согласованы `../openapi/external-gateway-sync.yaml`, `../openapi/external-gateway-async.yaml` и `../openapi/external-gateway-callback.yaml`;
- определены client service names: `invest-pay`, `user-expertise`, другие будущие клиенты;
- определены callback endpoints для сервисов, которые хотят получать async-результат push-моделью;
- определена схема авторизации между сервисами;
- определена семантика `externalId` и `Idempotency-Key`.

Критерий готовности:

- потребители могут сгенерировать клиентов по OpenAPI;
- `user-expertise` и `invest-pay` понимают контракт callback endpoint;
- понятны статусы и ошибки sync/async API.

## Шаг 1. Создать Spring Boot сервис и проверить запуск (сделано)

Результат:

- текущий Spring Boot скелет проекта создан;
- добавлены health/readiness endpoints;
- добавлена базовая конфигурация профилей.

Критерий готовности:

- сервис стартует локально и отвечает на smoke endpoint;
- после подключения PostgreSQL readiness не проходит без доступной gateway-схемы.

## Шаг 2. Добавить PostgreSQL-схему gateway

Результат:

- Liquibase миграции;
- таблица `ext_slots` с 5 слотами;
- таблица `ext_request_queue`;
- поле или вычисляемое значение `priority_weight` для сортировки `HIGH = 100`, `LOW = 10`;
- таблица `ext_sync_waiters`;
- таблица `ext_callback_delivery`;
- опциональная таблица `ext_call_audit`.

Критерий готовности:

- миграции создают схему с нуля;
- повторный запуск миграций идемпотентен;
- есть constraints на статусы, приоритеты и slot kind.

## Шаг 3. Реализовать Slot Manager

Результат:

- acquire sync slot: максимум 5;
- acquire async slot по динамическому sync reserve;
- release по `slot_id + lease_id`;
- heartbeat по `slot_id + lease_id`;
- reaper устаревших lease.

Критерий готовности:

- конкурентный тест на 10+ потоков никогда не получает больше 5 слотов;
- async acquire соблюдает динамический лимит `asyncAllowed = max(0, 5 - syncBusy - 1)`;
- при росте `syncBusy` gateway не стартует новые async-вызовы, пока скользящий sync reserve не восстановится;
- старый lease не может освободить новый lease.

## Шаг 4. Реализовать sync API

Результат:

- `POST /v1/external/sync`;
- регистрация sync waiter перед ожиданием слота;
- удаление sync waiter после acquire/timeout;
- upstream HTTP-вызов вне DB-транзакции;
- корректные ответы при timeout, upstream error, validation error.

Критерий готовности:

- sync получает приоритет над async при освобождении слота;
- при отсутствии слота дольше SLA возвращается управляемая ошибка;
- все sync-вызовы логируются с correlation/request id.

## Шаг 5. Реализовать async API

Результат:

- `POST /v1/external/async`;
- `GET /v1/external/async/{taskId}`;
- `GET /v1/external/async/by-external-id/{externalId}`;
- `DELETE /v1/external/async/{taskId}`;
- `POST /v1/external/async/{taskId}/retry`.
- поддержка `deliveryMode=CALLBACK | POLLING`;
- сохранение результата upstream-вызова как `Map<String, String>`.

Критерий готовности:

- повторная постановка с тем же `externalId` возвращает существующую задачу;
- статусы задачи отражают реальное состояние обработки;
- завершенная задача содержит результат или ошибку.
- `GET` возвращает тот же `result`, который отправляется в callback: `Map<String, String>` для `DONE` и `null` для неуспешных финальных статусов.

## Шаг 5.1. Реализовать callback delivery

Результат:

- gateway выбирает callback URL по `clientService` из allow-list конфигурации;
- gateway не принимает произвольный `callbackUrl` из пользовательского payload;
- после финального статуса async-задачи gateway отправляет callback в сервис-клиент;
- gateway отправляет обязательный заголовок `X-Callback-Attempt` и опциональные `X-Request-Id`, `X-Gateway-Signature`;
- payload callback содержит `result` как `Map<String, String>` для `DONE` и `null` для неуспешных финальных статусов;
- доставка callback имеет собственные retry/backoff и финальный статус `DEAD`;
- callback endpoint на стороне сервиса-клиента должен быть идемпотентным.

Критерий готовности:

- `user-expertise` получает callback после успешного выполнения async-задачи;
- повторная доставка одного результата не создает дублей в сервисе-клиенте;
- если callback не доставлен, результат остается доступен через `GET /v1/external/async/{taskId}`;
- ошибка доставки callback не переводит успешно выполненную upstream-задачу в failed.

## Шаг 6. Реализовать Dispatcher

Результат:

- выбор задач по `priority_weight DESC, available_at ASC, id ASC`;
- `FOR UPDATE SKIP LOCKED` для claim;
- запрет старта async, если есть живые sync waiters;
- `LISTEN/NOTIFY` как wake-up механизм;
- polling fallback.

Критерий готовности:

- несколько gateway-инстансов не берут одну задачу дважды;
- async backlog не вытесняет sync;
- при `syncBusy=1` одновременно стартует не больше 3 async-вызовов, при `syncBusy=2` - не больше 2;
- потеря NOTIFY не ломает обработку благодаря polling.

## Шаг 7. Реализовать Upstream Client

Результат:

- HTTP client с connect/read timeout;
- circuit breaker;
- retry policy для разрешенных transient ошибок;
- запрет retry для неидемпотентных sync-вызовов без idempotency key.

Критерий готовности:

- таймаут upstream меньше slot lease TTL;
- 429 от внешнего сервиса логируется как нарушение лимита или внешний конкурент;
- ошибки мапятся в понятные gateway-статусы.

## Шаг 8. Добавить recovery

Результат:

- reaper для устаревших slots;
- reaper для зависших `IN_PROGRESS` задач;
- перевод задач в `DEAD` после `maxAttempts`;
- перевод неретраибельных финальных ошибок в `FAILED`;
- ручной retry из `DEAD`/`FAILED`, если финальная ошибка помечена как `retryable`.

Критерий готовности:

- рестарт gateway во время async-вызова не теряет задачу;
- зависшая задача возвращается в обработку после TTL;
- reaper не освобождает чужой новый lease.
- зависшая доставка callback возвращается в retry после TTL.

## Шаг 9. Добавить observability

Результат:

- Micrometer metrics;
- structured logs;
- dashboard queries;
- alerts.

Критерий готовности:

- видно количество занятых слотов;
- видно очередь по приоритетам;
- видны dead tasks;
- видны pending/dead callback delivery;
- виден процент sync rejected.

## Шаг 10. Нагрузочные и интеграционные тесты

Минимальные сценарии:

- 20 конкурентных sync-запросов, одновременно upstream получает не больше 5;
- async backlog из 100 задач без активных sync-вызовов, одновременно upstream получает не больше 4 async;
- если активен 1 sync-вызов, async dispatcher доводит async только до 3 in-flight и держит 1 свободный слот;
- если активны 2 sync-вызова, async dispatcher доводит async только до 2 in-flight и держит 1 свободный слот;
- во время async backlog приходит sync, следующий свободный слот получает sync;
- падение gateway-инстанса во время async-вызова;
- повторный `externalId` для async;
- успешная callback-доставка результата в `user-expertise`;
- повторный callback по той же задаче идемпотентен;
- callback endpoint временно недоступен, gateway делает retry, результат доступен через GET;
- `LISTEN/NOTIFY` отключен, polling продолжает обработку.

Критерий готовности:

- тесты проходят локально и в CI;
- результаты тестов приложены к релизу v1.

## Шаг 11. Развертывание

Результат:

- gateway разворачивается минимум в 2 инстанса;
- все потребители переключены на gateway;
- сервисы-клиенты, использующие async callback, открыли внутренний callback endpoint;
- прямые вызовы внешнего сервиса из `invest-pay` и `user-expertise` удалены или запрещены сетевой политикой.

Критерий готовности:

- внешний сервис видит вызовы только от gateway;
- лимит 5 соблюдается в production;
- rollback-план описан и проверен.
