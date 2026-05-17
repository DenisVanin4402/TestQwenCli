---
name: sdd-spec-review
description: Проверять запрос или diff против SDD-спецификации, требований, acceptance criteria и gates.
---

# SDD Spec Review

## Inputs

- Запрос пользователя, issue или diff.
- Релевантный каталог `docs/specs/<id>-<slug>/`.
- `docs/sdd/gates.md`.

## Procedure

1. Найди релевантные requirement IDs.
2. Проверь, что критические требования имеют acceptance criteria.
3. Найди незакрытые уточнения, gaps в traceability и расхождения с планом.
4. Определи, какие тесты или контракты должны существовать.
5. Не изменяй файлы, если запрос не требует правки.

## Output

- Relevant requirements.
- Missing or ambiguous requirements.
- Required tests/contracts.
- Risks and next gate.

