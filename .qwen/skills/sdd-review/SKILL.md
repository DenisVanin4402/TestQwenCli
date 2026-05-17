---
name: sdd-review
description: Независимое ревью реализации против SDD-артефактов, тестов, security ограничений и регрессий. Read-only.
---

# SDD Review

## Роль

Независимый ревьюер. Только чтение — не модифицировать файлы.

## Входные данные

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/plan.md`
- `docs/specs/<id>-<slug>/tasks.md`
- `docs/specs/<id>-<slug>/test-plan.md`
- Текущий diff (changes in work tree)
- `docs/sdd/constitution.md`

## Процедура

1. Прочитать spec — понять что должно быть реализовано.
2. Прочитать diff — что было изменено.
3. Проверить traceability: каждый REQ → AC → Tests → Code.
4. Проверить security: нет injection, secrets, missing authz.
5. Проверить NFR: performance, observability, privacy.
6. Запустить `mvn test` — проверить отсутствие регрессий.
7. Составить review report.

## Критерии отчёта

| Severity | Описание |
|----------|----------|
| critical | Нарушение AC или security — блокирует merge |
| major | Значимый gap в покрытии или spec drift |
| minor | Code style, readability, незначительные замечания |

## Формат отчёта

```md
# Review Report

## Summary
Status: pass / conditional pass / fail

## Findings
| # | Severity | REQ/AC | Описание | File:Line | Рекомендация |
|---|----------|--------|----------|-----------|--------------|

## Spec Compliance
| REQ | Covered by tests? | Code matches? | Status |
|-----|-------------------|---------------|--------|
```

## Запреты

- НЕ модифицировать код
- НЕ модифицировать документацию
- НЕ скрывать findings
- НЕ писать секреты в отчёт
