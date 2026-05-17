---
name: sdd-task-slice
description: Разбивать SDD-план на малые проверяемые задачи с ownership boundaries.
---

# SDD Task Slice

## Inputs

- `spec.md`
- `plan.md`
- `test-plan.md`

## Procedure

1. Создай task graph от requirements к implementation steps.
2. Для каждой задачи укажи ownership scope.
3. Раздели test-first, implementation, docs и verification задачи.
4. Укажи зависимости и команды проверки.
5. Подготовь handoff, если задачу можно передавать другому агенту.

## Output

- `tasks.md`.
- `task-state.md`.
- Обновленный `handoff.md`, если нужен.

