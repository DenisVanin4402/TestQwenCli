# План разработки тестового дашборда

## Цель

Сделать отдельный дашборд для управляемого нагрузочного тестирования `external-service-gateway` и наблюдения за его техническим состоянием в реальном времени. Первый шаг - утвердить кликабельный UI-прототип без серверной реализации.

## Предлагаемая структура Maven-модулей

После утверждения прототипа проект будет переведен в multi-module Maven-структуру:

- `test-qwen-cli-app` - текущее Spring Boot приложение gateway.
- `dashboard-backend` - REST/SSE API дашборда, генератор нагрузки, сбор диагностических снимков и управление параметрами симуляции.
- `dashboard-ui` - статическая SPA-страница, CSS и JavaScript, которые вызывают API `dashboard-backend`.

При миграции нужно сохранить существующее поведение приложения и тестов. Перенос текущего кода в `test-qwen-cli-app` будет отдельным этапом, чтобы не смешивать модульную перестройку с логикой дашборда.

## Метрики нагрузки

В UI должны быть видны:

- целевые sync RPS и фактические sync RPS;
- целевые async submit RPS и фактические async submit RPS;
- количество активных sync и async upstream-вызовов;
- количество успешно обработанных sync-ответов;
- количество sync-ответов, упавших по timeout или из-за отсутствия слота;
- количество async-задач без финального результата;
- количество async-задач, завершенных успешно;
- количество async-задач в retry/backoff;
- количество callback-доставок без ответа;
- p50, p95 и p99 latency;
- доля ошибок внешнего сервиса;
- насыщение слотов и оценка headroom.

## Параметры сценария

Дашборд должен управлять параметрами, которые влияют на приложение и симуляцию внешнего сервиса:

- sync requests per second;
- async submit requests per second;
- распределение клиентских сервисов, например `invest-pay` и `user-expertise`;
- доля `HIGH` и `LOW` async priority;
- базовая latency внешнего сервиса;
- jitter latency;
- процент ошибок внешнего сервиса;
- размер ответа внешнего сервиса;
- timeout sync-запроса;
- общее количество слотов;
- резерв свободных слотов под sync;
- задержка callback endpoint и процент ошибок callback;
- длительность тестового сценария и режим остановки.

## Метрики здоровья проекта

В UI должны быть видны:

- размер async-очереди по статусам `PENDING`, `IN_PROGRESS`, `DONE`, `DEAD`, `CANCELLED`;
- объем неотправленных async-ответов через callback delivery;
- количество callback-доставок в retry и `DEAD`;
- объем ожидания sync-очереди;
- количество sync-запросов, отвалившихся по timeout или `NO_SLOT_AVAILABLE`;
- количество async-задач на retry;
- свободный пул слотов в разрезе `SYNC`, `ASYNC`, reserve и free;
- возраст самой старой async-задачи;
- возраст самой старой callback-доставки;
- количество истекших lease;
- состояние async dispatcher и callback dispatcher;
- текущий repository mode: `memory` или `postgres`.

## Предлагаемые endpoint-контракты

Черновой контракт для следующего этапа:

```http
GET  /dashboard/api/snapshot
GET  /dashboard/api/events
POST /dashboard/api/load/start
POST /dashboard/api/load/stop
PUT  /dashboard/api/load/profile
GET  /dashboard/api/load/profile
PUT  /dashboard/api/upstream-simulation
GET  /dashboard/api/upstream-simulation
GET  /dashboard/api/health
```

Для realtime-обновления предпочтителен SSE:

```http
GET /dashboard/api/stream
```

Если SSE будет избыточен для первого релиза, UI может стартовать с polling `GET /dashboard/api/snapshot` раз в 500-1000 мс.

## Этапы работ

### Этап 0. UI-прототип

Статус: `in_progress`.

Сделать кликабельную SPA-страницу с локальной симуляцией данных:

- панель запуска и остановки нагрузки;
- пресеты сценариев;
- слайдеры параметров внешнего сервиса;
- realtime-график throughput/latency/errors;
- блок здоровья очередей и слотов;
- вкладка будущих API-контрактов.

Артефакт прототипа: `docs/external-service-gateway/dashboard-prototype/index.html`.

Критерий приемки: пользователь утверждает композицию, состав метрик и взаимодействия.

### Этап 1. Модульная структура

Статус: `todo`.

Перевести проект в Maven multi-module без изменения runtime-поведения gateway:

- выделить текущее приложение в отдельный модуль;
- добавить пустые модули `dashboard-backend` и `dashboard-ui`;
- сохранить запуск тестов через `mvn test`;
- обновить документацию запуска.

### Этап 2. Backend API дашборда

Статус: `todo`.

Реализовать `dashboard-backend`:

- DTO для snapshot, load profile, upstream simulation и health;
- REST API управления нагрузкой;
- SSE или polling endpoint для realtime-снимков;
- сервис сбора состояния из `SlotRepository`, `AsyncTaskRepository` и `CallbackDeliveryRepository`;
- безопасные дефолты, чтобы дашборд не стартовал нагрузку сам при запуске приложения.

### Этап 3. Генератор нагрузки

Статус: `todo`.

Реализовать управляемый генератор:

- sync load runner с ограничением RPS;
- async submit runner с ограничением RPS;
- учет успешных, timeout, rejected и failed ответов;
- управление профилем нагрузки во время работы;
- graceful stop и очистка фоновых задач.

### Этап 4. Управляемая симуляция upstream/callback

Статус: `todo`.

Расширить симулированный внешний сервис:

- latency, jitter, response size и error rate;
- timeout/error сценарии;
- отдельные параметры callback endpoint;
- тесты на применение параметров без рестарта приложения.

### Этап 5. Интеграция SPA

Статус: `todo`.

Перенести утвержденный UI в `dashboard-ui`:

- заменить локальную симуляцию на вызовы API приложения;
- добавить fallback-состояния при недоступном API;
- подключить realtime через SSE или polling;
- обеспечить responsive-верстку.

### Этап 6. Тестирование

Статус: `todo`.

Покрыть изменения тестами:

- unit-тесты генератора нагрузки;
- MockMvc-тесты dashboard API;
- тесты сбора health snapshot;
- smoke-тест статической страницы;
- ручная проверка локального запуска.

### Этап 7. Документация и runbook

Статус: `todo`.

Описать:

- как запускать дашборд локально;
- какие сценарии нагрузки использовать;
- как читать метрики очередей и слотов;
- какие пороги считать тревожными;
- как безопасно остановить нагрузку.

## Что не делаем до утверждения UI

- Не добавляем Java-код dashboard backend.
- Не меняем `pom.xml`.
- Не переносим текущее приложение в новый Maven-модуль.
- Не меняем существующие endpoint-контракты gateway.
