---
description: Разбить SDD-план на проверяемые задачи с ownership boundaries.
---

# /sdd:tasks

Сформируй задачи для:

```text
{{args}}
```

## Вход

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/plan.md`
- `docs/specs/<id>-<slug>/test-plan.md`
- `docs/specs/_template/tasks.md`

## Читать

- Текущую spec, plan и test plan.
- `docs/sdd/gates.md`
- Релевантные ADR.

## Можно изменять

- `docs/specs/<id>-<slug>/tasks.md`
- `docs/specs/<id>-<slug>/task-state.md`
- `docs/specs/<id>-<slug>/handoff.md`

## Процедура

1. Для каждого requirement ID создай одну или несколько задач.
2. Укажи ownership scope: файлы, пакеты или документы.
3. Укажи зависимости между задачами.
4. Привяжи каждую задачу к проверке.
5. Отдели test-first задачи от implementation задач.

## Результат

- `tasks.md` с task graph.
- `task-state.md` с текущей фазой.
- Начальный `handoff.md`, если работа будет передаваться другому агенту.
- Следующий gate: `/sdd:implement`.

## Stop gate

Остановись, если задачи не имеют requirement IDs, ownership scope или проверки.

