# CR002-T010: план проверки архитектурной документации

## Цель этапа

Проверить, требует ли фактический результат CR002 дополнительных правок в `docs/external-service-gateway/architecture`.

Этап является документационно-проверочным: production-код, OpenAPI YAML, Maven-настройки, тесты, БД и runtime configuration не меняются.

## Архитектурный подход

Сверить результат CR002 с архитектурными документами и ADR:

- рабочий вход OpenAPI находится в `test-qwen-cli-app/src/main/resources/openapi`;
- документационное зеркало находится в `docs/external-service-gateway/openapi`;
- генерация OpenAPI-кода подключена к Maven-сборке;
- generated API interfaces и generated `*DTO` используются только на границе контроллеров;
- доменные `gateway.model.*` модели остаются контрактом service/repository/persistence-слоев;
- стандартный `/v3/api-docs` остается diagnostic output, а не canonical contract CR002.

Если существующие ADR и architecture README уже фиксируют эти договоренности, не расширять C4, sequence, data/state или operations документы низкоуровневыми деталями generated-кода. Точечно исправить только устаревшие формулировки, если они создают неверное впечатление о текущем состоянии.

## Затронутые модули и границы

Планируемые файлы:

- `docs/external-service-gateway/architecture/README.md` - проверить раздел источников истины и при необходимости уточнить wording;
- `docs/external-service-gateway/architecture/decisions.md` - проверить, достаточно ли ADR-012;
- `docs/external-service-gateway/chrequests/CR002/execution-progress.md` - зафиксировать итог проверки;
- `docs/external-service-gateway/chrequests/CR002/review_T010.md` - создать после проверки.

Не планируется менять:

- C4 context/container/component diagrams, если границы компонентов не изменились;
- sequence diagrams, если runtime flow sync/async/callback не изменился;
- data/state docs, если persistence и state machine не изменились;
- deployment/operations docs, если deployment model и production-инварианты не изменились.

## Публичные контракты

Этап не меняет публичные HTTP-контракты, OpenAPI YAML, status codes, headers, JSON body или callback contract.

## Data/state/deployment/operations

Data/state не меняются: таблицы, очереди, callback delivery state, slot leases и repository contracts не затрагиваются.

Deployment не меняется: generated sources создаются во время Maven-сборки в `target/generated-sources/openapi`.

Operations не меняются: новых runtime endpoints, scheduler-ов, внешних соединений, секретов или production runbook-инвариантов этап не добавляет.

## Тестовая стратегия

Maven-тесты для T010 не запускаются отдельно, потому что этап проверяет документацию и не меняет runtime-код или тесты. Итоговый быстрый контур будет подтвержден на CR002-T011 через `mvn test`.

Документная проверка:

- прочитать `architecture/README.md`, `architecture/decisions.md`, релевантные C4/data/operations документы;
- убедиться, что ADR-012 покрывает выбранную роль generated OpenAPI-кода;
- зафиксировать в `execution-progress.md`, какие архитектурные файлы изменены или почему изменения не требуются.

## Риски

- Слишком подробное описание generated DTO в C4/sequence docs может превратить архитектурную документацию в пересказ реализации.
- Недостаточная фиксация источника истины OpenAPI может оставить двусмысленность между resources YAML, docs mirror и runtime `/v3/api-docs`.

## Критерии отката

Откат этапа:

- вернуть точечные документные правки в `architecture/README.md`, если они окажутся неудачными;
- удалить `plan_T010.md` и `review_T010.md`, если этап полностью отменяется до закрытия;
- удалить или скорректировать записи T010 в `execution-progress.md`.

Откат не требует изменений production-кода, тестов, данных или Maven-настроек.
