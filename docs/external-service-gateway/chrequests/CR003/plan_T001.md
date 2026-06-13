# CR003-T001: план упрощения request-level idempotency через `@Idempotent`

## Цель этапа

Зафиксировать первый шаг CR003: заменить в будущем ручную прикладную обвязку request-level idempotency на внутреннюю библиотеку `@Idempotent`, сохранив DB-level unique guards в `ext_request_queue`.

Библиотека идемпотентности недоступна в этом репозитории, поэтому в рамках текущего шага зависимость не добавляется и production-поведение не меняется. Места будущего применения фиксируются комментариями у методов service boundary.

## Выбранный подход

Для async submit:

- будущая аннотация ставится на `ExternalAsyncServiceImpl.submit`;
- ключ: `request.clientService + request.externalId`;
- hash fields: `request.payload`, `request.priority`, `request.deliveryMode`;
- `requestId` не входит в hash;
- unique index в `ext_request_queue` остается нижним предохранителем от дублей;
- ручная repository-логика `find existing + compare fields + alreadyExisted/conflict` удаляется только после подключения библиотеки или согласованного local stub.

Для sync:

- будущая аннотация ставится на `ExternalSyncServiceImpl.sync`;
- ключ: `request.clientService + headers.idempotencyKey`;
- hash fields: `request.externalId`, `request.payload`;
- `headers.requestId` не входит в hash;
- повторный sync-запрос с тем же ключом должен отсекаться через `ext_request_queue`, чтобы не занимать второй слот и не вызывать upstream повторно;
- схема sync-idempotency зависит от `SYNC_REQUEST` lifecycle из CR004.

## Затронутые модули и границы

Затронуты только комментарии в service boundary:

- `ExternalAsyncServiceImpl.submit`;
- `ExternalSyncServiceImpl.sync`.

Границы компонентов не меняются. Контроллеры, репозитории, slot manager, dispatcher и callback delivery не меняются на этом шаге.

## Публичные контракты

HTTP/OpenAPI контракт не меняется.

Отдельно нужно решить в будущей реализации судьбу `AsyncSubmitResponse.alreadyExisted`:

- сохранить поле и изменить replay так, чтобы оно могло становиться `true`;
- оставить replay первого ответа как есть, где `alreadyExisted=false`;
- deprecated/remove поле в отдельном CR/OpenAPI-решении.

В рамках текущего шага поведение `alreadyExisted` не меняется.

## Data/state/deployment/operations

Схема БД не меняется на текущем шаге.

Обязательные будущие условия:

- async unique guard в `ext_request_queue` сохраняется;
- sync unique guard должен появиться в `ext_request_queue` после CR004 вместе с `SYNC_REQUEST` lifecycle;
- idempotency TTL внешней библиотеки не должен быть короче retention соответствующих request records;
- idempotency proxy не должен держать долгую БД-транзакцию вокруг ожидания слота или upstream-вызова.

## Тестовая стратегия

На текущем шаге production-код не меняет поведение, поэтому тесты не обязательны.

При будущей реализации с библиотекой или stub нужны проверки:

- concurrent async submit с одинаковым ключом не создает вторую задачу;
- async submit с тем же ключом и другим hash дает conflict;
- повторный async submit replay-ит согласованный ответ;
- repeated sync с тем же ключом не занимает второй слот;
- repeated sync с тем же ключом и другим hash дает conflict;
- DB unique guard остается последней защитой при обходе service proxy.

## Риски

- AOP-аннотация может быть обойдена self-invocation или прямым вызовом repository; DB unique guard должен остаться.
- Sync idempotency нельзя завершить без CR004, потому что текущий sync trace создается после выполнения запроса.
- Если `headers.idempotencyKey` отсутствует, нужно отдельное решение: reject, no-idempotency path или fallback key.
- Replay async submit может изменить семантику `alreadyExisted`.

## Критерии отката

Так как текущий шаг добавляет только документацию и комментарии, откат выполняется удалением этих правок.

Если будущая реализация с библиотекой окажется несовместима, ручная async submit idempotency остается текущим fallback, а DB unique guard не удаляется.
