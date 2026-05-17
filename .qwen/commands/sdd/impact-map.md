---
description: Построить карту прямых и косвенных impacts по diff-driven intake.
---

# /sdd:impact-map

Построй impact map для:

```text
{{args}}
```

## Вход

- `docs/specs/<id>-<slug>/intake.md`
- `docs/specs/<id>-<slug>/diff-map.md`
- Business requirements, если они доступны отдельно.

## Читать

- `diff-map.md`
- Измененные Markdown-разделы.
- Измененные OpenAPI/DBML фрагменты.
- Релевантные документы, на которые прямо указывает diff.
- `docs/specs/_template/impact-map.md`
- `docs/specs/_template/source-context.md`
- `docs/specs/_template/contracts/openapi-diff.md`
- `docs/specs/_template/contracts/dbml-diff.md`

## Можно изменять

- `docs/specs/<id>-<slug>/impact-map.md`
- `docs/specs/<id>-<slug>/source-context.md`
- `docs/specs/<id>-<slug>/contracts/openapi-diff.md`
- `docs/specs/<id>-<slug>/contracts/dbml-diff.md`
- `docs/specs/<id>-<slug>/work-log.md`

## Процедура

1. Для каждого semantic block определи direct impact.
2. Найди минимальный indirect context: документы, правила, API errors, audit, data constraints.
3. OpenAPI changes вынеси в `contracts/openapi-diff.md`.
4. DBML changes вынеси в `contracts/dbml-diff.md`.
5. Не копируй всю документацию, записывай summary и точные source references.
6. Остановись на Impact Gate и Source Context Gate.

## Результат

- `impact-map.md`.
- `source-context.md`.
- `contracts/openapi-diff.md`.
- `contracts/dbml-diff.md`.
- Open questions по contract/data/business gaps.
- Следующий gate: `/sdd:synthesize-spec`.

## Stop gate

Остановись, если requirement candidates не имеют source references или indirect context разрастается до чтения всей документации.

