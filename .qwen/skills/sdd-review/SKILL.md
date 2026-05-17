---
name: sdd-review
description: Проводить независимый read-only review реализации против SDD-артефактов.
---

# SDD Review

## Inputs

- Diff или список измененных файлов.
- `spec.md`, `plan.md`, `tasks.md`, `test-plan.md`.
- Релевантные ADR.

## Procedure

1. Сначала проверь поведенческие дефекты и regressions.
2. Сопоставь каждый измененный behavior с requirement IDs.
3. Проверь тесты, контракты, security/privacy и observability.
4. Проверь, что `work-log.md` содержит verification evidence.
5. Не изменяй файлы.

## Output

- Findings first, ordered by severity.
- Open questions.
- Test gaps and residual risks.
- Краткий итог, только после findings.

