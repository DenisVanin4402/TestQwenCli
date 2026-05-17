---
name: sdd-plan
description: Строить технический план реализации из SDD-спецификации с учетом affected files, ADR, тестов и рисков.
---

# SDD Plan

## Inputs

- `spec.md`
- `requirements.md`
- `docs/sdd/workflow.md`
- `docs/sdd/gates.md`
- Релевантные файлы кода.

## Procedure

1. Сопоставь requirements и acceptance criteria с текущим кодом.
2. Определи affected files, public contracts и data model changes.
3. Зафиксируй альтернативы и trade-offs.
4. Реши, нужен ли ADR.
5. Запиши test strategy и verification commands.

## Output

- `plan.md`.
- `research.md` при необходимости.
- `data-model.md` при необходимости.
- ADR decision: required/not required.

