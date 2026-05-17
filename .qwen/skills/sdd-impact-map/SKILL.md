---
name: sdd-impact-map
description: Строить карту прямых и косвенных impacts по diff-driven artifacts.
---

# SDD Impact Map

## Inputs

- `intake.md`
- `diff-map.md`
- Business requirements.
- Changed documentation snippets.

## Procedure

1. Сопоставь changed semantic blocks с direct impacts.
2. Найди минимальный indirect context.
3. Укажи source reference для каждого impact.
4. Раздели business, API, data, security и observability impacts.
5. Создай или обнови `impact-map.md` и `source-context.md`.

## Output

- `impact-map.md`.
- `source-context.md`.
- Impact Gate status.
- Source Context Gate status.

