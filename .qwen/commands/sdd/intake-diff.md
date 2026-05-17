---
description: Построить intake и diff map по MR системного аналитика.
---

# /sdd:intake-diff

Создай diff-driven intake для:

```text
{{args}}
```

## Вход

- Бизнес-требование или ссылка на него.
- Base branch релизной ветки.
- Analyst branch, MR URL или git range.
- При наличии: список измененных Markdown, OpenAPI и DBML файлов.

## Читать

- `AGENTS.md`
- `QWEN.md`
- `CONVENTIONS.md`
- `docs/sdd/workflow.md`
- `docs/sdd/gates.md`
- `docs/specs/_template/intake.md`
- `docs/specs/_template/diff-map.md`
- Git diff только по указанному range.

## Можно изменять

- `docs/specs/<id>-<slug>/intake.md`
- `docs/specs/<id>-<slug>/diff-map.md`
- `docs/specs/<id>-<slug>/work-log.md`

## Процедура

1. Зафиксируй base branch, analyst branch/MR и команду получения diff.
2. Получи список измененных файлов.
3. Классифицируй файлы: markdown, openapi, dbml, other.
4. Раздели прямые изменения и несвязанные изменения.
5. Для каждого файла запиши change kind и direct impact.
6. Остановись на Diff Intake Gate.

## Результат

- `intake.md` с входными данными.
- `diff-map.md` с changed files и semantic blocks.
- Open questions по воспроизводимости diff или scope.
- Следующий gate: `/sdd:impact-map`.

## Stop gate

Остановись, если base branch неизвестна, diff невоспроизводим или MR содержит несвязанные изменения, которые нельзя отделить.

