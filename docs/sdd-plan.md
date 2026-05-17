# План внедрения SDD в проект

Дата: 2026-05-17  
Основание: `docs/agentic-sdd-research.md`

## Цель

Внедрить в проект практический Specification-Driven Development workflow для агентской разработки, где спецификация, план, задачи, тесты, проверки и handoff являются версионируемыми артефактами репозитория, а не только историей чата.

Минимальная целевая цепочка:

```text
specification -> plan -> tasks -> tests/contracts -> implementation -> verification -> spec/memory update
```

После внедрения каждая существенная задача должна иметь source-of-truth в `docs/specs/<id>-<slug>/` и проходить через понятные gates перед реализацией и приемкой.

## Принципы rollout

- Сначала сделать переносимую файловую SDD-структуру, затем добавлять Qwen CLI wrappers.
- Использовать SDD для внедрения самого SDD, начиная со спеки `0001-sdd-bootstrap`.
- Не внедрять сразу тяжелую платформу, RAG, CI-gates и полноценную multi-agent автоматизацию.
- Держать `AGENTS.md`, `QWEN.md`, ADR, specs, commands, skills и work logs разными слоями.
- Любая реализация должна ссылаться на requirement IDs и иметь проверяемые acceptance criteria.

## Phase 0: Bootstrap

Цель: создать базовый набор документов и правил, достаточный для первого SDD-пилота.

### Шаги

1. Создать первую SDD-спеку для самого внедрения:
   - `docs/specs/0001-sdd-bootstrap/spec.md`
   - `docs/specs/0001-sdd-bootstrap/plan.md`
   - `docs/specs/0001-sdd-bootstrap/tasks.md`
   - `docs/specs/0001-sdd-bootstrap/test-plan.md`

2. Добавить базовые проектные правила:
   - `AGENTS.md` как переносимые инструкции для coding agents.
   - `QWEN.md` как Qwen-specific project memory.
   - `CONVENTIONS.md` для структуры, именования и локальных команд.

3. Добавить SDD governance docs:
   - `docs/sdd/constitution.md`
   - `docs/sdd/workflow.md`
   - `docs/sdd/gates.md`
   - `docs/sdd/context-management.md`

### Gate

- Есть source-of-truth для SDD bootstrap.
- Критические требования не содержат `[NEEDS CLARIFICATION]`.
- Определены Definition of Ready и Definition of Done.

## Phase 1: Artifact Templates

Цель: дать агентам и людям единый формат артефактов для новых задач.

### Шаги

1. Создать шаблон feature spec:
   - `docs/specs/_template/spec.md`
   - `docs/specs/_template/requirements.md`
   - `docs/specs/_template/plan.md`
   - `docs/specs/_template/research.md`
   - `docs/specs/_template/data-model.md`
   - `docs/specs/_template/test-plan.md`
   - `docs/specs/_template/tasks.md`
   - `docs/specs/_template/quickstart.md`
   - `docs/specs/_template/task-state.md`
   - `docs/specs/_template/work-log.md`
   - `docs/specs/_template/handoff.md`
   - `docs/specs/_template/contracts/README.md`

2. Включить в шаблоны обязательные секции:
   - problem, users, goals, non-goals;
   - requirements with IDs;
   - acceptance criteria;
   - edge cases;
   - NFR: security, privacy, performance, availability, observability;
   - open questions;
   - verification commands;
   - traceability matrix.

3. Добавить ADR-слой:
   - `docs/adr/0001-record-architecture-decisions.md`
   - `docs/adr/_template.md`

### Gate

- Новую задачу можно начать копированием `docs/specs/_template/`.
- Есть явное правило, когда требуется ADR.
- Traceability matrix присутствует в шаблоне.

## Phase 2: Qwen CLI Entry Points

Цель: сделать SDD workflow удобным для ежедневной работы через Qwen CLI.

### Шаги

1. Добавить Qwen commands:
   - `.qwen/commands/sdd/specify.md`
   - `.qwen/commands/sdd/clarify.md`
   - `.qwen/commands/sdd/plan.md`
   - `.qwen/commands/sdd/tasks.md`
   - `.qwen/commands/sdd/implement.md`
   - `.qwen/commands/sdd/review.md`

2. Для каждой команды определить:
   - входные данные;
   - какие файлы читать;
   - какие файлы можно изменять;
   - обязательный формат результата;
   - gate, на котором команда должна остановиться.

3. Добавить базовые Qwen skills:
   - `.qwen/skills/sdd-spec-review/SKILL.md`
   - `.qwen/skills/sdd-plan/SKILL.md`
   - `.qwen/skills/sdd-task-slice/SKILL.md`
   - `.qwen/skills/sdd-test-gap/SKILL.md`
   - `.qwen/skills/sdd-review/SKILL.md`

### Gate

- `/sdd:specify`, `/sdd:plan`, `/sdd:tasks`, `/sdd:implement`, `/sdd:review` имеют Markdown definitions.
- Команды не требуют чтения всего репозитория.
- Команды работают через context packet, а не через неструктурированный prompt.

## Phase 3: Lightweight Verification

Цель: добавить минимальные автоматические проверки качества SDD-артефактов без тяжелой инфраструктуры.

### Шаги

1. Добавить простой spec lint:
   - проверка обязательных секций;
   - поиск `[NEEDS CLARIFICATION]`;
   - проверка наличия requirement IDs;
   - проверка, что `tasks.md` ссылается на requirement IDs.

2. Добавить проверку traceability:
   - requirements -> acceptance criteria;
   - acceptance criteria -> tests or explicit gap;
   - tasks -> verification evidence.

3. Зафиксировать команды проверки в:
   - `AGENTS.md`
   - `QWEN.md`
   - `CONVENTIONS.md`

### Gate

- Есть команда или documented procedure для проверки SDD-артефактов.
- Результаты проверок записываются в `work-log.md`.
- Исключения фиксируются явно, а не скрываются.

## Phase 4: Pilot

Цель: проверить workflow на первой реальной задаче и обновить правила по результатам.

### Шаги

1. Выбрать следующую реальную фичу или техническую задачу.
2. Провести ее через полный цикл:

```text
/sdd:specify -> /sdd:clarify -> /sdd:plan -> /sdd:tasks -> /sdd:implement -> /sdd:review
```

3. По итогам пилота обновить:
   - `docs/sdd/workflow.md`
   - `docs/sdd/gates.md`
   - Qwen commands;
   - Qwen skills;
   - templates;
   - `AGENTS.md` и `QWEN.md`, если появились стабильные правила.

### Gate

- Пилотная задача завершена с заполненными `work-log.md`, `task-state.md` и traceability matrix.
- Найденные проблемы workflow либо исправлены, либо записаны как follow-up tasks.

## Phase 5: Multi-Agent And Context Platform

Цель: расширять процесс только после того, как файловый SDD workflow доказал полезность.

### Шаги

1. Добавить read-only reviewer role.
2. Добавить security reviewer role.
3. Добавить test engineer role.
4. Ввести строгий handoff contract и ownership boundaries для параллельных задач.
5. Позже рассмотреть:
   - context packet compiler;
   - RAG для больших документов;
   - spec drift checks;
   - memory review/decay;
   - CI gates.

### Gate

- Multi-agent workflow не используется без ownership boundaries.
- Reviewer и security reviewer по умолчанию read-only.
- Все handoff documents содержат done, not done, modified files, commands run, blockers и do-not-change constraints.

## Ближайший практический шаг

Начать с `Phase 0` и создать `docs/specs/0001-sdd-bootstrap/` как первую dogfooding-спеку. После этого уже по ней добавить базовые файлы `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md` и `docs/sdd/*`.

## Не входит в первый заход

- Полноценная RAG-платформа.
- Автоматический drift detection в CI.
- Сложная multi-agent orchestration.
- Обязательная генерация Gherkin для всех тестов.
- ADR на каждое мелкое изменение.
- Большой append-only memory file.
