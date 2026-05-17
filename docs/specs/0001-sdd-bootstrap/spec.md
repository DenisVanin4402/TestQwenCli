# Спецификация: SDD Bootstrap

## Проблема

Проект не имеет структурированного процесса Specification-Driven Development. Без версионируемых артефактов (спецификации, планы, задачи, тесты, handoff) AI-агенты и разработчики работают через историю чата, что приводит к потере контекста, дрейфу требований и невозможности независимой верификации.

## Цель

Создать базовую инфраструктуру SDD в проекте, чтобы все будущие задачи проходили через формализованный процесс: спецификация → план → задачи → тесты → реализация → верификация.

## Пользователи

- Разработчики, использующие Qwen CLI для работы над проектом
- AI coding-агенты, выполняющие задачи
- Ревьюеры, проверяющие соответствие кода спецификации

## Scope

**Включает:**
- Создание структуры репозитория для SDD (`docs/specs/`, `docs/sdd/`, `docs/adr/`)
- Создание governance-документов (constitution, workflow, gates, context-management)
- Создание AGENTS.md, CONVENTIONS.md
- Создание Qwen CLI команд для SDD (`.qwen/commands/sdd/`)
- Создание Qwen CLI навыков (`.qwen/skills/`)
- Создание шаблонов артефактов (`docs/specs/_template/`)
- Создание скрипта spec lint
- Создание определений ролей агентов (`.qwen/agents/`)

**Не включает (non-goals):**
- RAG-платформу
- CI-интеграцию
- Multi-agent автоматизацию с orchestration
- Генерацию Gherkin для всех тестов
- Полноценную security-платформу

## User Stories

- US-1: Как разработчик, я хочу начать новую задачу копированием шаблона спецификации, чтобы не пропустить важные разделы.
- US-2: Как AI агент, я хочу получить context packet с релевантными файлами, а не весь репозиторий, чтобы не превышать контекстное окно.
- US-3: Как ревьюер, я хочу проверить соответствие кода спецификации через матрицу трассировки, чтобы убедиться в полноте реализации.
- US-4: Как пользователь, я хочу использовать `/sdd:plan` для генерации плана из спецификации через Qwen CLI.

## Требования

### REQ-1: Структура репозитория для SDD

Реализация: Создать директорию `docs/specs/` для feature-спецификаций, `docs/sdd/` для governance-документов, `docs/adr/` для архитектурных решений.

### REQ-2: Governance-документы

Реализация: Создать constitution.md, workflow.md, gates.md, context-management.md в `docs/sdd/`.

### REQ-3: Project rules

Реализация: Создать AGENTS.md и CONVENTIONS.md в корне проекта. Обновить QWEN.md ссылками на SDD-документы.

### REQ-4: Qwen CLI команды SDD

Реализация: Создать `.qwen/commands/sdd/` с командами: specify, clarify, plan, tasks, implement, review.

### REQ-5: Qwen CLI навыки SDD

Реализация: Создать `.qwen/skills/` с навыками: sdd-spec-review, sdd-plan, sdd-task-slice, sdd-test-gap, sdd-review.

### REQ-6: Роли агентов

Реализация: Создать `.qwen/agents/` с определениями ролей: planner, implementer, reviewer, security-reviewer.

### REQ-7: Шаблоны артефактов

Реализация: Создать `docs/specs/_template/` с шаблонами всех артефактов спецификации.

### REQ-8: Spec lint скрипт

Реализация: Создать `scripts/sdd-lint.sh` для проверки обязательных секций спецификаций.

## Acceptance Criteria

- AC-1: Все директории созданы согласно `docs/sdd-plan.md` Phase 0-3
- AC-2: Все governance-документы содержат конкретное и применимое содержимое, а не заглушки
- AC-3: AGENTS.md и CONVENTIONS.md содержат ссылки на SDD-документы
- AC-4: Все 6 Qwen CLI команд SDD имеют Markdown definitions в `.qwen/commands/sdd/`
- AC-5: Все 5 Qwen skills SDD имеют SKILL.md в `.qwen/skills/`
- AC-6: Все 4 роли агентов имеют определение в `.qwen/agents/`
- AC-7: `docs/specs/_template/` содержит все 11+ шаблонов
- AC-8: `scripts/sdd-lint.sh` проверяет обязательные секции и requirement IDs
- AC-9: `docs/adr/0001-record-architecture-decisions.md` создана как первая ADR
- AC-10: `mvn test` проходит без регрессий

## Требования к безопасности (NFR-Security)

- Секреты, токены и пароли не записываются ни в один SDD-артефакт.
- AGENTS.md запрещает агентам писать секреты в код или логи.

## Требования к наблюдаемости (NFR-Observability)

- Все изменения логируются в `work-log.md` в соответствующей спецификации.

## Performance NFR

- Spec lint скрипт должен выполняться менее чем за 5 секунд на текущем объёме файлов.

## Нерешённые вопросы

- [RESOLVED] Язык документов — русский (согласно QWEN.md).

## Матрица трассировки

| REQ | Acceptance criteria | Проверка | Статус |
|-----|---------------------|----------|--------|
| REQ-1 | AC-1 | Директории существуют | pending |
| REQ-2 | AC-2 | Governance docs заполнены | pending |
| REQ-3 | AC-3 | AGENTS.md, CONVENTIONS.md, QWEN.md обновлены | pending |
| REQ-4 | AC-4 | `.qwen/commands/sdd/` содержит 6 файлов | pending |
| REQ-5 | AC-5 | `.qwen/skills/` содержит 5 SKILL.md | pending |
| REQ-6 | AC-6 | `.qwen/agents/` содержит 4 файла | pending |
| REQ-7 | AC-7 | `docs/specs/_template/` содержит все шаблоны | pending |
| REQ-8 | AC-8 | `scripts/sdd-lint.sh` работает | pending |
| - | AC-9 | ADR создана | pending |
| - | AC-10 | `mvn test` проходит | pending |
