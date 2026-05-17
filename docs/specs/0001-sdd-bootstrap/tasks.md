# Задачи: SDD Bootstrap

## Задачи

### TSK-1: Создать структуру директорий

**REQ:** REQ-1
**Описание:** Создать все директории: `docs/sdd/`, `docs/specs/`, `docs/specs/_template/`, `docs/specs/_template/contracts/`, `docs/adr/`, `.qwen/commands/sdd/`, `.qwen/skills/`, `.qwen/agents/`, `scripts/`
**Вход:** None
**Выход:** Директории созданы
**Зависимости:** None
**Оценка сложности:** S

### TSK-2: Создать governance-документы

**REQ:** REQ-2
**Описание:** Создать constitution.md, workflow.md, gates.md, context-management.md в `docs/sdd/`
**Вход:** `docs/agentic-sdd-research.md`, `docs/sdd-plan.md`
**Выход:** 4 governance файла с конкретным содержанием
**Зависимости:** TSK-1
**Оценка сложности:** M

### TSK-3: Создать project rules

**REQ:** REQ-3
**Описание:** Создать AGENTS.md, CONVENTIONS.md в корне. Обновить QWEN.md.
**Вход:** Existing QWEN.md, research docs
**Выход:** AGENTS.md, CONVENTIONS.md, обновлённый QWEN.md
**Зависимости:** TSK-2
**Оценка сложности:** M

### TSK-4: Создать Qwen CLI команды SDD

**REQ:** REQ-4
**Описание:** Создать 6 команд в `.qwen/commands/sdd/`: specify.md, clarify.md, plan.md, tasks.md, implement.md, review.md
**Вход:** `docs/sdd/workflow.md` для понимания фаз
**Выход:** 6 Markdown-файлов команд
**Зависимости:** TSK-3
**Оценка сложности:** M

### TSK-5: Создать Qwen CLI навыки SDD

**REQ:** REQ-5
**Описание:** Создать 5 навыков в `.qwen/skills/`: sdd-spec-review, sdd-plan, sdd-task-slice, sdd-test-gap, sdd-review
**Вход:** `docs/sdd/workflow.md`, research docs
**Выход:** 5 SKILL.md файлов
**Зависимости:** TSK-3
**Оценка сложности:** M

### TSK-6: Создать роли агентов

**REQ:** REQ-6
**Описание:** Создать 4 роли в `.qwen/agents/`: planner.md, implementer.md, reviewer.md, security-reviewer.md
**Вход:** research doc section 8.1 (multi-agent roles)
**Выход:** 4 Markdown-файла ролей
**Зависимости:** TSK-3
**Оценка сложности:** S

### TSK-7: Создать шаблоны артефактов

**REQ:** REQ-7
**Описание:** Создать все шаблоны в `docs/specs/_template/`: spec.md, requirements.md, plan.md, research.md, data-model.md, test-plan.md, tasks.md, quickstart.md, task-state.md, work-log.md, handoff.md, contracts/README.md
**Вход:** Все governance docs + research
**Выход:** 12 шаблонов
**Зависимости:** TSK-2, TSK-3
**Оценка сложности:** M

### TSK-8: Создать ADR template и первую ADR

**REQ:** REQ-1 (часть)
**Описание:** Создать `docs/adr/_template.md` и `docs/adr/0001-record-architecture-decisions.md`
**Вход:** research section 7.4
**Выход:** 2 ADR файла
**Зависимости:** TSK-1
**Оценка сложности:** S

### TSK-9: Создать spec lint скрипт

**REQ:** REQ-8
**Описание:** Создать `scripts/sdd-lint.sh` для проверки обязательных секций spec.md
**Вход:** `docs/sdd/gates.md` (spec lint gate requirements)
**Выход:** Рабочий shell-скрипт
**Зависимости:** TSK-2, TSK-7
**Оценка сложности:** M

## Граф зависимостей

```
TSK-1 -> TSK-2 -> TSK-3 -> TSK-4
                 ↳ TSK-5
                 ↳ TSK-6
                 ↳ TSK-7
TSK-1 -> TSK-8
TSK-7 + TSK-2 -> TSK-9
```

## Владение и блокировки

| Задача | Ответственный | Статус | Блокеры |
|--------|---------------|--------|---------|
| TSK-1 | Builder | planned | none |
| TSK-2 | Builder | planned | TSK-1 |
| TSK-3 | Builder | planned | TSK-2 |
| TSK-4 | Builder | planned | TSK-3 |
| TSK-5 | Builder | planned | TSK-3 |
| TSK-6 | Builder | planned | TSK-3 |
| TSK-7 | Builder | planned | TSK-2, TSK-3 |
| TSK-8 | Builder | planned | TSK-1 |
| TSK-9 | Builder | planned | TSK-2, TSK-7 |
