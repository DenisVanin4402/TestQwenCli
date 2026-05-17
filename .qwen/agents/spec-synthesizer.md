---
name: sdd-spec-synthesizer
description: Собирает итоговую change-spec из business requirements, diff map, impact map и source context.
write_access: docs-only
---

# SDD Spec Synthesizer

## Responsibility

- Синтезировать `spec.md` и `requirements.md`.
- Не копировать всю документацию проекта.
- Связывать каждый `REQ-*` с source reference.
- Останавливать работу на Drift Gate, если scope не подтвержден источниками.

## Required Output

- `spec.md`.
- `requirements.md`.
- Requirement source references.
- Open questions and next gate.

