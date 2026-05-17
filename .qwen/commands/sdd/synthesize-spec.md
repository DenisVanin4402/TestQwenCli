---
description: Синтезировать SDD-спецификацию из business requirements, analyst diff и source context.
---

# /sdd:synthesize-spec

Синтезируй change-spec для:

```text
{{args}}
```

## Вход

- `docs/specs/<id>-<slug>/intake.md`
- `docs/specs/<id>-<slug>/diff-map.md`
- `docs/specs/<id>-<slug>/impact-map.md`
- `docs/specs/<id>-<slug>/source-context.md`
- `contracts/openapi-diff.md`
- `contracts/dbml-diff.md`

## Читать

- Входные diff-driven artifacts.
- `docs/specs/_template/spec.md`
- `docs/specs/_template/requirements.md`
- `docs/sdd/gates.md`
- Релевантные ADR, если diff затрагивает архитектурные решения.

## Можно изменять

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/requirements.md`
- `docs/specs/<id>-<slug>/work-log.md`

## Процедура

1. Сформулируй problem и goals на основе business requirement и diff.
2. Создай `REQ-*`, где каждый requirement связан с business source или source diff.
3. Создай `AC-*`, где каждый criterion связан с requirement.
4. Включи только change-context, не копируй всю проектную документацию.
5. Отдельно запиши OpenAPI, DBML, NFR и open questions.
6. Проверь Drift Gate: spec не должна содержать behavior, которого нет в diff или требованиях.

## Результат

- `spec.md`.
- `requirements.md`.
- Список source references для `REQ-*`.
- Open questions.
- Следующий gate: `/sdd:clarify` или `/sdd:plan`.

## Stop gate

Остановись, если requirement не связан с источником или synthesized spec расширяет scope beyond analyst diff/business requirements.

