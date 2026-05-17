---
name: sdd-dbml-diff
description: Анализировать DBML changes и выделять изменения таблиц, колонок, связей и enum.
---

# SDD DBML Diff

## Inputs

- Base DBML source.
- Changed DBML source.
- Git diff или MR diff.

## Procedure

1. Выдели changed tables.
2. Выдели changed columns.
3. Выдели changed refs.
4. Выдели changed enums.
5. Отметь потенциальные миграции и compatibility risks.
6. Свяжи каждое изменение с requirement candidate.

## Output

- `contracts/dbml-diff.md`.
- Data questions.
- Requirement candidates.

