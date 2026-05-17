# План внедрения diff-driven specification synthesis

Дата: 2026-05-17

## Контекст

Текущий SDD-фреймворк поддерживает базовый `spec-first` сценарий:

```text
business request -> /sdd:specify -> spec.md -> plan -> tasks -> implement
```

Нужно добавить второй сценарий: системный аналитик получает бизнес-требования, меняет проектную документацию, OpenAPI и DBML в своем merge request, после чего команда разработки должна синтезировать первую SDD-спецификацию не из пустого запроса, а из diff и связанного контекста.

Новый сценарий:

```text
business request + analyst MR diff -> diff intake -> impact map -> synthesized spec -> plan -> tasks -> implement
```

## 1. Идея сценария

Если системный аналитик уже изменил проектную документацию, OpenAPI и DBML в своем MR, то первая SDD-спецификация должна быть не пересказом всей документации, а **сводной change-spec**, собранной из:

- бизнес-требований;
- diff аналитика;
- измененных Markdown-разделов;
- измененных OpenAPI endpoints/schemas;
- измененных DBML tables/relations;
- прямого и косвенного контекста, который нужен для понимания изменения;
- открытых вопросов и конфликтов.

Важно: synthesized spec не должна копировать всю документацию проекта. Она должна содержать только изменения и минимальный контекст, который прямо или косвенно нужен для реализации.

## 2. Источники истины

| Источник | Роль |
|---|---|
| Бизнес-требования | Объясняют зачем изменение нужно |
| MR аналитика | Показывает фактически внесенные изменения |
| Markdown-документация | Описывает бизнес-процессы, правила, сценарии |
| `openapi.yaml` | Описывает API-контракты |
| DBML | Описывает структуру данных и связи |
| Существующие ADR/specs | Дают исторические решения и ограничения |
| Diff относительно release branch | Показывает точный scope изменений |

## 3. Новые артефакты

Добавить в шаблон SDD-задачи:

```text
docs/specs/<id>-<slug>/
  intake.md
  diff-map.md
  impact-map.md
  source-context.md
  spec.md
  requirements.md
  plan.md
  test-plan.md
  tasks.md
  work-log.md
  handoff.md
  contracts/
    openapi-diff.md
    dbml-diff.md
```

Назначение:

| Файл | Смысл |
|---|---|
| `intake.md` | Входные данные: бизнес-требование, MR, base branch, analyst branch |
| `diff-map.md` | Какие файлы изменены и какие смысловые блоки задеты |
| `impact-map.md` | Прямые и косвенные затронутые области |
| `source-context.md` | Короткие выдержки и ссылки на релевантные куски документации |
| `spec.md` | Синтезированная спецификация изменения |
| `requirements.md` | Нормализованные `REQ-*`, `AC-*`, NFR |
| `contracts/openapi-diff.md` | Сводка изменений API |
| `contracts/dbml-diff.md` | Сводка изменений модели данных |

## 4. Новый pipeline

Добавить отдельный вариант pipeline:

```text
intake-diff
  -> classify changes
  -> extract source context
  -> build impact map
  -> synthesize spec
  -> clarify gaps
  -> plan
  -> tasks
  -> implement
  -> review
```

Qwen-команды:

```text
/sdd:intake-diff <base branch> <analyst branch or MR>
/sdd:impact-map <spec id>
/sdd:synthesize-spec <spec id>
/sdd:clarify <spec id>
/sdd:plan <spec id>
/sdd:tasks <spec id>
/sdd:implement <task id>
/sdd:review <spec id>
```

## 5. Детальный план реализации

### Phase A. Обновить governance

Изменить:

```text
docs/sdd/workflow.md
docs/sdd/gates.md
docs/sdd/context-management.md
docs/sdd/instruction.md
CONVENTIONS.md
AGENTS.md
QWEN.md
```

Добавить понятие двух режимов:

```text
spec-first mode
diff-driven mode
```

Добавить правило:

> Если входом является MR аналитика с изменениями в документации, OpenAPI или DBML, агент сначала строит `diff-map.md`, `impact-map.md` и `source-context.md`, и только потом синтезирует `spec.md`.

### Phase B. Добавить шаблоны

Добавить в `docs/specs/_template/`:

```text
intake.md
diff-map.md
impact-map.md
source-context.md
contracts/openapi-diff.md
contracts/dbml-diff.md
```

Пример `diff-map.md`:

```md
# Diff Map

## Git range

- Base: `release/2026.05`
- Analyst branch: `feature/sa-payment-limits`
- Diff command: `git diff release/2026.05...feature/sa-payment-limits`

## Changed files

| File | Type | Change kind | Direct impact |
|---|---|---|---|
| `docs/payments/limits.md` | markdown | modified | Payment limits rules |
| `docs/api/openapi.yaml` | openapi | modified | `POST /payments` request schema |
| `docs/db/payment.dbml` | dbml | modified | `payment_limits` table |

## Changed semantic blocks

| File | Block | Summary | Source |
|---|---|---|---|
| `docs/payments/limits.md` | `## Daily limits` | Daily limit rule changed | diff hunk |
| `openapi.yaml` | `POST /payments` | Added `limitProfileId` | OpenAPI diff |
| `payment.dbml` | `payment_limits` | Added table | DBML diff |
```

Пример `impact-map.md`:

```md
# Impact Map

## Direct impacts

| Area | Source change | Requirement candidate |
|---|---|---|
| Payment validation | `docs/payments/limits.md` | REQ-1 |
| Payment API | `openapi.yaml#/paths/~1payments/post` | REQ-2 |
| Payment data model | `payment.dbml:payment_limits` | REQ-3 |

## Indirect impacts

| Area | Why affected | Required context |
|---|---|---|
| Audit logging | Payment limit decision must be auditable | `docs/audit/events.md` |
| Error model | Limit violation needs API error | `docs/api/errors.md` |

## Open questions

- Какой error code возвращать при превышении лимита?
- Нужна ли миграция исторических платежей?
```

### Phase C. Добавить Qwen commands

Добавить:

```text
.qwen/commands/sdd/intake-diff.md
.qwen/commands/sdd/impact-map.md
.qwen/commands/sdd/synthesize-spec.md
```

`/sdd:intake-diff` должен:

- принять base branch и analyst branch/MR;
- получить список измененных файлов;
- классифицировать файлы: Markdown, OpenAPI, DBML, other;
- создать `intake.md`;
- создать `diff-map.md`;
- остановиться на Diff Intake gate.

`/sdd:impact-map` должен:

- прочитать `diff-map.md`;
- найти прямые изменения;
- найти косвенно затронутые документы;
- создать `impact-map.md`;
- создать `source-context.md`.

`/sdd:synthesize-spec` должен:

- взять business requirements;
- взять `diff-map.md`;
- взять `impact-map.md`;
- взять `source-context.md`;
- создать `spec.md` и `requirements.md`;
- не включать всю документацию, только change-context.

### Phase D. Добавить skills

Добавить:

```text
.qwen/skills/sdd-diff-intake/SKILL.md
.qwen/skills/sdd-impact-map/SKILL.md
.qwen/skills/sdd-openapi-diff/SKILL.md
.qwen/skills/sdd-dbml-diff/SKILL.md
.qwen/skills/sdd-spec-synthesis/SKILL.md
```

Роли skills:

| Skill | Назначение |
|---|---|
| `sdd-diff-intake` | Построить карту изменений по git diff |
| `sdd-impact-map` | Найти прямой и косвенный impact |
| `sdd-openapi-diff` | Выделить изменения endpoints/schemas/security/responses |
| `sdd-dbml-diff` | Выделить изменения tables/columns/refs/enums |
| `sdd-spec-synthesis` | Собрать change-spec из diff и контекста |

### Phase E. Добавить agent roles

Добавить роли:

```text
.qwen/agents/doc-diff-analyst.md
.qwen/agents/contract-analyst.md
.qwen/agents/data-model-analyst.md
.qwen/agents/spec-synthesizer.md
```

Назначение:

| Agent | Что делает |
|---|---|
| `doc-diff-analyst` | Анализирует Markdown diff |
| `contract-analyst` | Анализирует OpenAPI changes |
| `data-model-analyst` | Анализирует DBML changes |
| `spec-synthesizer` | Собирает итоговую change-spec |

### Phase F. Добавить gates

Новые gates:

| Gate | Проверяет |
|---|---|
| Diff Intake Gate | Base branch известна, diff воспроизводим, файлы классифицированы |
| Source Context Gate | Для каждого изменения есть источник и минимальный контекст |
| Impact Gate | Прямые и косвенные impacts перечислены |
| Contract/Data Gate | OpenAPI и DBML изменения выделены отдельно |
| Synthesis Gate | Каждый `REQ-*` связан с source diff или business requirement |
| Drift Gate | Synthesized spec не содержит поведения, которого нет в diff/requirements |

### Phase G. Добавить проверки

Позже расширить `SddArtifactTests` или добавить `SddLintTests`.

Проверки:

- `intake.md` содержит base branch и analyst branch/MR;
- `diff-map.md` содержит changed files;
- `impact-map.md` содержит direct impacts;
- `spec.md` содержит `REQ-*`;
- каждый `REQ-*` имеет source reference;
- `requirements.md` содержит `AC-*`;
- OpenAPI changes отражены в `contracts/openapi-diff.md`;
- DBML changes отражены в `contracts/dbml-diff.md`;
- `tasks.md` ссылается на synthesized `REQ-*`.

## 6. Главный принцип synthesized spec

Формула:

```text
business requirement
+ analyst diff
+ direct changed docs
+ changed OpenAPI/DBML contracts
+ minimal indirect context
= synthesized change specification
```

В `spec.md` нельзя переносить всю документацию. Нужно писать только:

- что изменилось;
- почему изменилось;
- где источник изменения;
- какие требования следуют из изменения;
- какие критерии приемки проверяют изменение;
- какие документы/API/таблицы задеты;
- какие вопросы остались.

## 7. Пример synthesized requirement

```md
| ID | Requirement | Source |
|---|---|---|
| REQ-1 | При создании платежа система должна проверять дневной лимит клиента. | `docs/payments/limits.md`, hunk 2 |
| REQ-2 | `POST /payments` должен принимать `limitProfileId`. | `openapi.yaml#/paths/~1payments/post/requestBody` |
| REQ-3 | Лимитные профили должны храниться в таблице `payment_limits`. | `payment.dbml:payment_limits` |
```

Пример AC:

```md
| ID | Requirement | Criteria |
|---|---|---|
| AC-1 | REQ-1 | Платеж сверх дневного лимита отклоняется. |
| AC-2 | REQ-2 | OpenAPI содержит поле `limitProfileId` в request schema. |
| AC-3 | REQ-3 | DBML содержит таблицу `payment_limits` и связь с клиентом. |
```

## 8. План внедрения по задачам

| Task | Scope | Результат |
|---|---|---|
| TASK-1 | `docs/sdd/*`, `CONVENTIONS.md` | Описать diff-driven mode |
| TASK-2 | `docs/specs/_template/*` | Добавить intake/diff/impact/source templates |
| TASK-3 | `.qwen/commands/sdd/*` | Добавить intake-diff, impact-map, synthesize-spec |
| TASK-4 | `.qwen/skills/*` | Добавить diff/openapi/dbml/spec synthesis skills |
| TASK-5 | `.qwen/agents/*` | Добавить роли diff/contract/data/spec synthesis |
| TASK-6 | `src/test/.../SddArtifactTests.java` | Проверить наличие новых артефактов |
| TASK-7 | Новый `SddLintTests` | Начать проверку Phase 3 |
| TASK-8 | `docs/specs/0002-*` | Провести пилот на искусственном или реальном analyst diff |

## 9. Что реализовать первым

Первым проходом без тяжелой автоматизации:

1. Обновить документацию фреймворка под `diff-driven mode`.
2. Добавить новые шаблоны.
3. Добавить новые Qwen commands.
4. Добавить новые skills и agents.
5. Расширить структурный тест.
6. Записать это как новую SDD-задачу `0002-diff-driven-spec-synthesis`.

Автоматический парсинг OpenAPI/DBML лучше делать вторым проходом, после того как формат артефактов стабилизируется.

## 10. Статус реализации

Дата обновления: 2026-05-17

### Реализовано в первом проходе

| Пункт плана | Статус | Где реализовано |
|---|---:|---|
| Описать `diff-driven mode` в governance | done | `docs/sdd/workflow.md`, `docs/sdd/gates.md`, `docs/sdd/context-management.md`, `docs/sdd/instruction.md` |
| Зафиксировать `diff-driven mode` в корневых инструкциях | done | `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md`, `README.md` |
| Создать SDD-задачу для внедрения режима | done | `docs/specs/0002-diff-driven-spec-synthesis/` |
| Добавить `intake.md` | done | `docs/specs/_template/intake.md` |
| Добавить `diff-map.md` | done | `docs/specs/_template/diff-map.md` |
| Добавить `impact-map.md` | done | `docs/specs/_template/impact-map.md` |
| Добавить `source-context.md` | done | `docs/specs/_template/source-context.md` |
| Добавить OpenAPI diff template | done | `docs/specs/_template/contracts/openapi-diff.md` |
| Добавить DBML diff template | done | `docs/specs/_template/contracts/dbml-diff.md` |
| Добавить `/sdd:intake-diff` | done | `.qwen/commands/sdd/intake-diff.md` |
| Добавить `/sdd:impact-map` | done | `.qwen/commands/sdd/impact-map.md` |
| Добавить `/sdd:synthesize-spec` | done | `.qwen/commands/sdd/synthesize-spec.md` |
| Добавить diff-driven skills | done | `.qwen/skills/sdd-diff-intake/`, `sdd-impact-map/`, `sdd-openapi-diff/`, `sdd-dbml-diff/`, `sdd-spec-synthesis/` |
| Добавить diff-driven agent roles | done | `.qwen/agents/doc-diff-analyst.md`, `contract-analyst.md`, `data-model-analyst.md`, `spec-synthesizer.md` |
| Расширить структурную проверку | done | `src/test/java/com/example/testqwencli/SddArtifactTests.java` |
| Запустить проверку | done | `mvn test` прошел: 7 tests, 0 failures, 0 errors |

### Еще не реализовано

| Пункт | Статус | Почему не закрыто в первом проходе |
|---|---:|---|
| Автоматический spec lint | todo | Требует отдельного `SddLintTests` или скрипта, который парсит Markdown и проверяет `REQ-*`, `AC-*`, обязательные секции и `[NEEDS CLARIFICATION]` |
| Автоматический traceability checker | todo | Нужно валидировать связи `REQ -> AC -> tests/gap -> tasks -> evidence` |
| Автоматический OpenAPI diff parser | todo | Первый проход фиксирует формат `openapi-diff.md`, но не парсит YAML автоматически |
| Автоматический DBML diff parser | todo | Первый проход фиксирует формат `dbml-diff.md`, но не парсит DBML автоматически |
| Генератор context packet | todo | Сейчас context packet описан документно, но не генерируется командой или тестом |
| Реальный pilot на analyst MR | todo | Нужен реальный или искусственный MR/diff с Markdown, OpenAPI и DBML изменениями |
| CI gates | todo | Пока проверки запускаются локально через `mvn test` |
| Техническое enforcement read-only ролей | todo | Read-only закреплен в agent definitions, но не enforced инструментально |

### Рекомендованный следующий шаг

Создать отдельную SDD-задачу для Phase 3 verification:

```text
docs/specs/0003-sdd-lint-and-traceability/
```

Цель:

- добавить `SddLintTests`;
- проверять обязательные секции;
- проверять отсутствие `[NEEDS CLARIFICATION]`;
- проверять наличие `REQ-*` и `AC-*`;
- проверять source references для diff-driven requirements;
- проверять traceability matrix;
- проверять, что `work-log.md` содержит verification evidence.
