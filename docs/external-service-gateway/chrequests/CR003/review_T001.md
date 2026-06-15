# CR003-T001: senior architect review

## Итог

CR003-T001 переоткрыт и фактически выполнен как реализационный этап, а не как document/code-marker-only шаг.

Реализация в целом соответствует обновленному плану: ручная async submit idempotency-обвязка удалена из service/repository layer, успешный submit больше не делает replay существующей задачи, а duplicate `clientService + externalId` до подключения внутренней библиотеки `@Idempotent` fail-closed отклоняется `409 IDEMPOTENCY_CONFLICT`.

Проверенный фактический diff:

- `ExternalAsyncServiceImpl.submit` теперь обрабатывает `DUPLICATE_REJECTED` и возвращает `alreadyExisted=false` только для новых задач.
- `AsyncSubmitResult` и `AsyncSubmitResultType` упрощены до `SUBMITTED` и `DUPLICATE_REJECTED`; удалены `existingTaskId`, `conflictingFields`, `alreadyExisted`.
- `MemoryAsyncTaskRepository.submit` больше не сравнивает `payload`, `priority`, `deliveryMode` и не возвращает существующую задачу.
- `PostgresAsyncTaskRepository.submit` оставляет атомарный `INSERT ... ON CONFLICT DO NOTHING`, но больше не делает `SELECT existing` и hash/diff compare.
- OpenAPI async YAML в рабочем входе Maven и документационном зеркале описывает `202` как создание задачи, а `409` как duplicate guard до `@Idempotent`.
- Repository/controller/PostgreSQL concurrency/integration tests обновлены под fail-closed duplicate behavior.
- Архитектурные документы обновлены в части C4 component view, async sequence, production readiness и data/state описания duplicate guard.
- Подготовительные PRE-WORK материалы обновлены как справочный mirror: async replay/hash conflict больше не описан как текущая реализация, а перенесен в будущий `@Idempotent`.

Повторно выполненные проверки reviewer-а:

- `git diff --check` - без whitespace-ошибок; остались только предупреждения Git о будущей нормализации LF/CRLF в Windows working tree.
- `mvn -pl test-qwen-cli-app -am test` - успешно, 120 tests.
- `mvn -pl test-qwen-cli-app -am -Pintegration-tests verify` - первая попытка была остановлена timeout 180s без test failure; повтор с увеличенным timeout прошел успешно, 48 integration tests.

## Соответствие плану

Обновленный `plan_T001.md` согласован с текущей реализацией:

- библиотека идемпотентности не подключается в этом репозитории;
- production-запуск без `@Idempotent` запрещен и зафиксирован как gate;
- ручная repository/service логика `find existing + compare fields + reuse/conflict` удалена;
- поле `AsyncSubmitResponse.alreadyExisted` сохранено в JSON-shape для будущего replay, но текущий успешный submit возвращает `false`;
- sync idempotency остается target state через будущую `SYNC_REQUEST` lifecycle-запись CR004; текущий sync OpenAPI не изменяет optional `Idempotency-Key`.

Work-items по CR003-T001 в основном выполнены:

- code markers для будущей `@Idempotent` есть у `ExternalAsyncServiceImpl.submit` и `ExternalSyncServiceImpl.sync`;
- async DB unique guard не удален: PostgreSQL path продолжает использовать `ON CONFLICT (client_service, external_id) DO NOTHING`, а Liquibase сохраняет partial unique index для async delivery modes;
- duplicate async submit по занятому ключу возвращает `409`, а не successful replay;
- fail-closed behavior отражен в controller/repository/concurrency/integration tests;
- production readiness gate на `@Idempotent` отражен в `plan_T001.md`, `execution-progress.md` и `09-production-readiness.md`.

Публичный HTTP/OpenAPI shape сохранен: endpoint, request body, response schema и поле `alreadyExisted` остаются. При этом runtime-поведение duplicate async submit намеренно изменено до подключения библиотеки: одинаковый повтор больше не возвращает `202 alreadyExisted=true`, а получает `409`.

Соответствие ADR:

- ADR-002: PostgreSQL остается координатором v1 для queue/slots/callback state; DB duplicate guard остается нижним слоем корректности.
- ADR-003: lease-модель слотов не затронута; submit не удерживает слот и не добавляет долгих DB locks.
- ADR-004: sync priority и sync waiters gate не меняются.
- ADR-005: async callback/polling lifecycle не меняется; изменен только submit duplicate path до создания второй задачи.
- ADR-010: клиенты по-прежнему работают через HTTP API и не получают прямой доступ к таблицам gateway.

## Производительность

Изменение снижает overhead duplicate submit path: PostgreSQL больше не выполняет дополнительный `SELECT existing` и сравнение `payload`, `priority`, `deliveryMode` после `ON CONFLICT DO NOTHING`.

Для successful submit, dispatch, callback delivery, slot acquire/release и polling новых runtime paths, очередей, schedulers, thread pools или DB locks не добавлено. Схема БД не менялась.

Остаточный performance-риск перенесен в будущую интеграцию `@Idempotent`: proxy не должен держать долгую DB-транзакцию вокруг ожидания слота или upstream-вызова. Для текущего async submit это менее рискованно, потому что submit короткий; для будущего sync path это критично для ADR-003.

## Безопасность

Новые входные данные, callback URL, SSRF-поверхность, scope доступа и секреты не добавлены. Callback contracts и allow-list URL не менялись.

Fail-closed duplicate behavior снижает риск частичной ручной идемпотентности: gateway больше не маскирует отсутствие обязательной библиотеки успешным replay, который не обеспечивает внешний cluster-wide lock/hash semantics.

Публичный error code остается `IDEMPOTENCY_CONFLICT`, но `details.existingTaskId` и `details.conflictingFields` больше не возвращаются. Это соответствует плану: до `@Idempotent` gateway не раскрывает детали существующей задачи и не утверждает hash conflict.

Sync без `Idempotency-Key` сейчас не отклоняется. Это корректно для CR003-T001, потому что обязательность sync-заголовка требует отдельного OpenAPI/code/test изменения после CR004 или вместе с ним. Запрет fallback key из `requestId`/`externalId` зафиксирован.

## Архитектурные приемы

Выбранный прием архитектурно оправдан для dev-phase: удалить неполный ручной алгоритм сейчас, оставить DB/memory guard как нижний предохранитель и явно сделать `@Idempotent` production gate. Это лучше отражает реальное состояние системы перед production rollout.

Границы компонентов в целом сохранены:

- controller остается HTTP boundary;
- service boundary готовится к будущей AOP-аннотации;
- repository отвечает только за durable insert/duplicate guard и state transitions;
- DB unique guard остается последней защитой при обходе будущего idempotency proxy.

Решение не смешивает async submit idempotency с sync lifecycle до появления `SYNC_REQUEST` из CR004. Это сохраняет текущую схему и не создает преждевременную связанность sync trace с async queue semantics.

## Замечания

1. severity: low
   ссылка: `docs/external-service-gateway/architecture/04-data-and-state.md:126`
   риск: state machine async task все еще содержит переход `PENDING --> PENDING: idempotent submit same payload`. После CR003-T001 текущий duplicate submit не является переходом состояния и отклоняется `409`; будущий `@Idempotent` replay также должен происходить над service boundary без второго repository submit. Эта строка может ввести в заблуждение при последующих data/state review.
   предлагаемое действие: заменить переход на явную пометку duplicate submit как HTTP reject/no state transition до `@Idempotent`, а target replay через библиотеку описать отдельно вне текущей state machine или как внешний service-boundary guard.
   статус human approval: accepted

2. severity: note
   ссылка: `docs/external-service-gateway/chrequests/CR003/work-items.md:5`
   риск: верхнеуровневое описание CR003 все еще говорит про снижение сложности "без изменения публичного поведения", тогда как CR003-T001 намеренно меняет runtime-поведение duplicate async submit с successful replay `202 alreadyExisted=true` на fail-closed `409`. В самом T001 это отражено корректно, но будущий читатель может увидеть противоречие между общим scope и исключением T001.
   предлагаемое действие: при закрытии этапа или CR уточнить верхнеуровневую формулировку: HTTP/OpenAPI shape в целом не меняется, а duplicate async submit behavior является явным dev-phase исключением CR003-T001 до подключения `@Idempotent`.
   статус human approval: accepted

## Рекомендация

CR003-T001 можно одобрить как реализационный этап. Оба документационных замечания приняты и закрыты.

Замечание `low` закрыто обновлением async task state machine в `04-data-and-state.md`: duplicate submit больше не описывается как переход `PENDING -> PENDING`, а зафиксирован как HTTP reject/no state transition до `@Idempotent`.

Замечание `note` закрыто уточнением верхнеуровневого scope в `work-items.md`: CR003 сохраняет HTTP/OpenAPI shape, но CR003-T001 явно является dev-phase исключением по runtime-поведению duplicate async submit.

Переходить к CR003-T002 можно только после явной команды пользователя и с созданием `plan_T002.md` до реализации.

## Human approval

Статус: accepted.

Решение:

- замечание `low` принято и исправлено в `04-data-and-state.md`;
- замечание `note` принято и исправлено в `work-items.md`;
- дополнительных действий по CR003-T001 перед остановкой не требуется.
