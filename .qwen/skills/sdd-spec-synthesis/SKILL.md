---
name: sdd-spec-synthesis
description: Синтезировать change-spec из business requirements, analyst diff, impact map и source context.
---

# SDD Spec Synthesis

## Inputs

- `intake.md`
- `diff-map.md`
- `impact-map.md`
- `source-context.md`
- `contracts/openapi-diff.md`
- `contracts/dbml-diff.md`

## Procedure

1. Сформулируй problem, goals и non-goals из business requirements и diff.
2. Создай `REQ-*` только для поведения, подтвержденного source reference.
3. Создай `AC-*` для каждого requirement.
4. Укажи source для каждого requirement.
5. Отдели OpenAPI, DBML, NFR и open questions.
6. Проверь Drift Gate: не добавляй scope beyond diff/requirements.

## Output

- `spec.md`.
- `requirements.md`.
- Source references.
- Open questions.
- Next gate.

