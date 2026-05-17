# Управление контекстом

Цель контекстного управления - давать агенту ровно тот набор фактов, который нужен для текущей фазы SDD, и не превращать историю чата в источник истины.

## Слои контекста

| Слой | Где хранится | Когда читать |
|---|---|---|
| Source of truth | `docs/specs/**` | Всегда для текущей задачи |
| Governance | `docs/sdd/**` | При запуске SDD-фазы и review |
| Project memory | `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md` | В начале сессии |
| Decisions | `docs/adr/**` | Если затронуты архитектурные границы |
| Runtime state | `task-state.md`, `work-log.md`, `handoff.md` | При resume и handoff |
| Tool wrappers | `.qwen/commands/**`, `.qwen/skills/**`, `.qwen/agents/**` | При вызове конкретной процедуры |
| Diff-driven intake | `intake.md`, `diff-map.md`, `impact-map.md`, `source-context.md` | Когда входом является analyst MR или документационный diff |

## Правила отбора

- Начинать с текущей спеки и задачи, а не с чтения всего репозитория.
- Искать релевантные файлы через targeted search.
- В context packet включать только требования, AC, ограничения, affected files и команды проверки.
- После compaction сохранять инварианты: requirements, решения, done/not done, modified files, commands run, blockers.
- Не записывать секреты и локальные токены в memory, specs, logs или handoff.
- В `diff-driven` режиме сначала читать diff map и source references, а не всю документацию проекта.
- В `source-context.md` включать summary и точные ссылки, а не полные копии больших документов.

## Promotion model

Факт остается в `work-log.md`, пока он относится только к текущей задаче. Его можно поднять выше, если он повторяем:

- стабильное правило проекта -> `AGENTS.md`, `QWEN.md` или `CONVENTIONS.md`;
- архитектурный выбор -> `docs/adr/`;
- повторяемая процедура -> `.qwen/skills/` или `.qwen/commands/`;
- шаблон артефакта -> `docs/specs/_template/`.

## Diff-driven context policy

Для analyst MR контекст собирается слоями:

1. Business requirement или ссылка на него.
2. `intake.md` с base branch и analyst branch/MR.
3. `diff-map.md` с changed files и semantic blocks.
4. `contracts/openapi-diff.md` и `contracts/dbml-diff.md` для контрактов.
5. `impact-map.md` с direct и indirect impacts.
6. `source-context.md` с минимальным контекстом.

Запрещено строить synthesized spec через полный dump всей документации, если достаточно source references из impact map.
