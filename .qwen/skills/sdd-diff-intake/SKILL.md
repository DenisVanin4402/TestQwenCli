---
name: sdd-diff-intake
description: Строить intake и diff map по business requirements и MR системного аналитика.
---

# SDD Diff Intake

## Inputs

- Business requirements.
- Base branch.
- Analyst branch, MR URL или git range.
- Измененные файлы документации, OpenAPI и DBML.

## Procedure

1. Зафиксируй source branches и команду получения diff.
2. Получи changed files.
3. Классифицируй файлы: markdown, openapi, dbml, other.
4. Выдели changed semantic blocks.
5. Отдели несвязанные изменения.
6. Создай или обнови `intake.md` и `diff-map.md`.

## Output

- `intake.md`.
- `diff-map.md`.
- Diff Intake Gate status.
- Open questions.

