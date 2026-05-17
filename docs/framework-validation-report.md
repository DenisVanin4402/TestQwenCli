# Отчет о соответствии SDD-фреймворка требованиям

Дата проверки: 2026-05-17  
Проверяемые источники:

- `docs/sdd-plan.md`
- `docs/agentic-sdd-research.md`
- реализованные артефакты SDD-фреймворка в репозитории

## Короткий вывод

Фреймворк реализован как файловый SDD-baseline: спеки, шаблоны, governance, Qwen-команды, skills, agent roles и инструкция есть.

Полностью закрыты в основном Phase 0-2 из `docs/sdd-plan.md`.

Phase 3 закрыта частично: есть структурный smoke-test и описанные правила, но нет настоящего `spec lint` и `traceability checker`.

Phase 4 не проводилась.

Phase 5 реализована только как набор role definitions, без реальной orchestration/platform.

## Соответствие `docs/sdd-plan.md`

| Требование | Статус | Что есть | Что не хватает |
|---|---:|---|---|
| Source-of-truth в `docs/specs/<id>-<slug>/` | Частично | Есть `docs/specs/0001-sdd-bootstrap/` и шаблон `_template` | Нет пилотной реальной задачи через полный цикл |
| Минимальная цепочка `spec -> plan -> tasks -> tests -> implementation -> verification` | Частично | Описана в `docs/sdd/workflow.md`, `AGENTS.md`, `QWEN.md`, `instruction.md` | Не автоматизирована как единый runner |
| Phase 0: bootstrap spec | Да | `spec.md`, `plan.md`, `tasks.md`, `test-plan.md`, `task-state.md`, `work-log.md`, `handoff.md` | Нет |
| Phase 0: `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md` | Да | Все три файла есть и описывают SDD-процесс | Нет |
| Phase 0: governance docs | Да | `constitution.md`, `workflow.md`, `gates.md`, `context-management.md`, дополнительно `instruction.md` | Нет |
| Phase 0 gate: DoR/DoD | Да | Есть в `CONVENTIONS.md` | Нет |
| Phase 1: шаблон feature spec | Да | Есть полный `docs/specs/_template/` | Нет |
| Phase 1: обязательные секции в шаблонах | Да | Есть problem/goals/non-goals/REQ/AC/NFR/open questions/traceability | Автопроверки секций нет |
| Phase 1: ADR-слой | Да | Есть `docs/adr/_template.md` и `0001-record-architecture-decisions.md` | Нет |
| Phase 2: Qwen commands | Да | Есть `.qwen/commands/sdd/specify|clarify|plan|tasks|implement|review.md` | Нет |
| Phase 2: команды определяют вход, чтение, запись, результат, gate | Да | В командах эти секции прописаны | Нет runtime-проверки соблюдения |
| Phase 2: Qwen skills | Да | Есть 5 skills: spec-review, plan, task-slice, test-gap, review | Нет |
| Phase 3: простой spec lint | Нет | Есть только структурный `SddArtifactTests` на наличие файлов | Нет проверки секций, `[NEEDS CLARIFICATION]`, `REQ-*`, ссылок tasks на requirements |
| Phase 3: traceability check | Нет | Есть traceability matrix в шаблоне и bootstrap `test-plan.md` | Нет автоматической проверки `REQ -> AC -> tests/gap -> tasks -> evidence` |
| Phase 3: команды проверки в `AGENTS/QWEN/CONVENTIONS` | Частично | `mvn test`, workflow и work-log правила описаны | Нет отдельной команды `spec lint` / `traceability check` |
| Phase 4: Pilot | Нет | Подготовлена инфраструктура | Не проведена реальная задача через `/sdd:*` |
| Phase 5: reviewer/security/test roles | Частично | Есть `.qwen/agents/reviewer.md`, `security-reviewer.md`, `test-engineer.md` | Нет реальной multi-agent orchestration |
| Phase 5: strict handoff contract и ownership | Частично | Есть шаблон `handoff.md`, agent roles, commands с ownership | Нет автоматического enforcement |

## Соответствие `docs/agentic-sdd-research.md`

| Идея / рекомендация | Статус | Что сделано | Gap |
|---|---:|---|---|
| Версионируемые Markdown-артефакты вместо истории чата | Да | `docs/specs/**`, `docs/sdd/**`, `docs/adr/**`, `.qwen/**` | Нет |
| Разделение layers: project memory, specs, ADR, commands, skills, work logs | Да | Слои разделены по разным файлам и каталогам | Нет |
| Context packet вместо чтения всего репозитория | Частично | Описан в `workflow.md`, `instruction.md`, Qwen commands | Нет генератора context packet |
| Spec artifacts: `spec.md`, requirements, AC, edge cases, NFR, open questions | Да | Есть в шаблонах и bootstrap spec | Нет автоматического lint |
| EARS/Gherkin как optional формат требований/AC | Частично | В шаблоне есть Given/When/Then и структурные требования | Нет отдельного стандарта EARS |
| ADR для significant decisions | Да | ADR template и ADR-0001 есть | Нет enforcement |
| Multi-agent roles | Да как definitions | Добавлены orchestrator, spec-writer, planner, builder, test-engineer, reviewer, security-reviewer | Нет runtime orchestration |
| Reviewer/security reviewer read-only | Да как правило | В agent definitions указано `write_access: read-only` | Нет технической блокировки записи |
| Handoff contract | Да | Есть `handoff.md` в bootstrap и template | Нет автопроверки полноты |
| Skills, agents, commands, rules | Да | `.qwen/skills`, `.qwen/agents`, `.qwen/commands`, `AGENTS.md` | Нет версии/публикации skills |
| Verification gates | Частично | Описаны в `docs/sdd/gates.md` | Нет автоматических gate checks |
| Traceability matrix | Частично | Есть в `test-plan.md` шаблона и bootstrap | Нет валидатора связей |
| Security/governance constitution | Да | `docs/sdd/constitution.md` | Нет автоматических security checks |
| Anti-patterns учтены | Частично | Описаны в `instruction.md`; структура избегает одного большого memory-файла | Нет проверки соблюдения |
| Qwen adaptation | Да | `.qwen/commands`, `.qwen/skills`, `.qwen/agents`, `QWEN.md` | Не проверено в реальном Qwen CLI запуске |

## Отдельно про `SddArtifactTests`

`SddArtifactTests.java` сейчас является только структурным smoke-test: проверяет, что обязательные файлы существуют.

Это полезный guard, но он не закрывает Phase 3 как `spec lint`.

Фактический gap:

- нет парсинга Markdown;
- нет проверки обязательных секций;
- нет поиска `[NEEDS CLARIFICATION]`;
- нет проверки `REQ-*` / `AC-*`;
- нет проверки, что `tasks.md` ссылается на requirements;
- нет проверки traceability до evidence в `work-log.md`.

## Итог

Сделано:

- файловый SDD-framework baseline;
- bootstrap spec;
- шаблоны задач;
- governance docs;
- ADR-слой;
- Qwen commands;
- Qwen skills;
- Qwen agent roles;
- подробная инструкция;
- структурный smoke-test.

Не сделано:

- настоящий `spec lint`;
- автоматический traceability checker;
- context packet generator;
- реальный pilot через весь pipeline;
- техническое enforcement read-only ролей;
- CI gates;
- RAG/context platform.

## Рекомендуемый следующий шаг

Добавить `SddLintTests`, который проверяет Phase 3:

- обязательные секции;
- отсутствие `[NEEDS CLARIFICATION]`;
- наличие `REQ-*`;
- наличие `AC-*`;
- связь `AC -> REQ`;
- связь `TASK -> REQ`;
- наличие traceability matrix;
- наличие verification evidence в `work-log.md`.

После этого провести пилотную задачу `0002-*` через полный цикл:

```text
/sdd:specify -> /sdd:clarify -> /sdd:plan -> /sdd:tasks -> /sdd:implement -> /sdd:review
```

