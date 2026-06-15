# CR003-T001: план упрощения request-level idempotency через `@Idempotent`

## Цель этапа

Зафиксировать первый шаг CR003: удалить ручную прикладную обвязку request-level idempotency из async submit и заменить ее в будущем на внутреннюю библиотеку `@Idempotent`, сохранив DB-level unique guards в `ext_request_queue`.

Библиотека идемпотентности недоступна в этом репозитории, поэтому в рамках текущего шага зависимость не добавляется. До подключения библиотеки проект находится в fail-closed dev-phase состоянии: duplicate async submit по `clientService + externalId` не replay-ится, а отклоняется через DB/memory guard. Production-запуск без библиотеки идемпотентности запрещен.

## Выбранный подход

Для async submit:

- будущая аннотация ставится на `ExternalAsyncServiceImpl.submit`;
- ключ: `request.clientService + request.externalId`;
- hash fields: `request.payload`, `request.priority`, `request.deliveryMode`;
- `requestId` не входит в hash;
- unique index в `ext_request_queue` остается нижним предохранителем от дублей;
- ручная repository-логика `find existing + compare fields + alreadyExisted/replay/conflict` удаляется в этом этапе;
- если DB/memory guard обнаружил duplicate key до подключения библиотеки, submit возвращает `409`, не вычисляя hash conflict fields;
- `AsyncSubmitResponse.alreadyExisted` сохраняется в OpenAPI/DTO для будущего replay через библиотеку, но текущий код после удаления ручного алгоритма возвращает `false` для новых задач и не возвращает успешный replay.

Для sync:

- будущая аннотация ставится на `ExternalSyncServiceImpl.sync`;
- ключ: `request.clientService + headers.idempotencyKey`;
- hash fields: `request.externalId`, `request.payload`;
- `headers.requestId` не входит в hash;
- повторный sync-запрос с тем же ключом должен отсекаться через `ext_request_queue`, чтобы не занимать второй слот и не вызывать upstream повторно;
- целевое поведение для отсутствующего `headers.idempotencyKey` - отклонять sync-запрос как неидемпотентный до получения слота и upstream-вызова;
- схема sync-idempotency зависит от `SYNC_REQUEST` lifecycle из CR004.

## Затронутые модули и границы

Затрагиваются:

- `ExternalAsyncServiceImpl.submit`;
- `ExternalSyncServiceImpl.sync`;
- порт `AsyncTaskRepository` и реализации `MemoryAsyncTaskRepository`, `PostgresAsyncTaskRepository`;
- внутренние модели submit result/duplicate guard;
- unit/contract/controller/concurrency tests, проверяющие старый ручной replay/conflict.

Границы компонентов не меняются. Контроллеры, slot manager, dispatcher и callback delivery не меняются на этом шаге.

## Публичные контракты

HTTP/OpenAPI shape не меняется: endpoint, request body, response body и поле `alreadyExisted` остаются. Поведение duplicate submit временно меняется до подключения библиотеки: вместо успешного replay `202 alreadyExisted=true` текущий код возвращает `409` по занятости `clientService + externalId`.

Текущий sync OpenAPI помечает `Idempotency-Key` как optional, поэтому в рамках CR003-T001 это остается только целевым решением, а не изменением поведения. Перевод `Idempotency-Key` в обязательный заголовок для sync требует отдельной реализации после CR004 или вместе с ней: обновить OpenAPI, generated interface, контроллерную валидацию, ошибку `400` для отсутствующего ключа и тесты. До этого sync-запрос без ключа продолжает идти текущим путем без request-level replay, но будущий идемпотентный режим не должен использовать fallback key из `requestId` или `externalId`.

Поле `AsyncSubmitResponse.alreadyExisted` сохраняется как часть будущего контракта с `@Idempotent`. В текущем fail-closed режиме оно всегда `false`, потому что успешного replay без библиотеки больше нет. Удаление или deprecate поля не выполняется в CR003-T001, чтобы не менять JSON-shape и generated OpenAPI модели.

## Data/state/deployment/operations

Схема БД не меняется на текущем шаге.

Обязательные будущие условия:

- async unique guard в `ext_request_queue` сохраняется;
- sync unique guard должен появиться в `ext_request_queue` после CR004 вместе с `SYNC_REQUEST` lifecycle;
- idempotency TTL внешней библиотеки не должен быть короче retention соответствующих request records;
- idempotency proxy не должен держать долгую БД-транзакцию вокруг ожидания слота или upstream-вызова.
- production readiness gate: gateway нельзя считать готовым к production, пока `@Idempotent` не возвращает replay/hash-conflict semantics для async submit.

## Тестовая стратегия

На текущем шаге меняется duplicate submit behavior, поэтому нужны unit/contract/controller tests.

Проверки текущего этапа:

- первый async submit создает `PENDING` задачу и возвращает `alreadyExisted=false`;
- повторный async submit с той же парой `clientService + externalId` отклоняется `409`, а не replay-ится;
- повторный async submit с другой `payload/priority/deliveryMode` тоже отклоняется `409`, но без ручного списка `conflictingFields`;
- repository contract tests подтверждают, что DB/memory guard не создает вторую задачу;
- PostgreSQL concurrency test подтверждает, что из нескольких concurrent submit создается одна задача, остальные получают duplicate rejection;
- существующие dispatcher/callback tests продолжают использовать только успешный первый submit.

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
- Отклонение sync-запросов без `Idempotency-Key` изменит публичный контракт, поэтому его нельзя включать в текущий документальный шаг без отдельного OpenAPI/code изменения.
- До подключения библиотеки async duplicate submit временно не replay-ится. Это осознанный non-prod gap, который должен быть виден в tests/docs и production readiness.
- Dashboard или ручные нагрузочные сценарии с повторным `externalId` начнут получать duplicate rejection вместо уже существующей задачи; генераторы тестовых запросов должны использовать новые `externalId`.

## Критерии отката

Откат выполняется возвратом старой ручной repository-логики `find existing + compare fields + replay/conflict` и старых тестовых ожиданий.

Если будущая реализация с библиотекой окажется несовместима, нельзя автоматически возвращать ручной async алгоритм как production fallback без отдельного решения: это снова замаскирует отсутствие обязательной библиотеки. DB unique guard при любом варианте не удаляется.
