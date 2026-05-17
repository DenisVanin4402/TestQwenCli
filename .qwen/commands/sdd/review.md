---
description: Провести ревью реализации против спецификации, тестов и требований (Review phase).
---

# /sdd:review

Проведи независимое ревью реализации по чек-листу SDD.

## Входные данные

Путь к спецификации или `{{args}}`

## Что читать

1. `docs/specs/000N-slug/spec.md` — спецификация (requirements + AC)
2. `docs/specs/000N-slug/plan.md` — технический план
3. `docs/specs/000N-slug/tasks.md` — что должно быть сделано
4. `docs/specs/000N-slug/test-plan.md` — что должно быть протестировано
5. Текущий diff / изменённые файлы
6. `docs/sdd/constitution.md` — конституционные правила
7. `docs/sdd/gates.md` — Drift Check Gate, Security Check Gate

## Что делать

1. **Traceability check:** для каждого REQ проверить, что AC выполнены и тесты написаны
2. **Spec-code alignment:** код соответствует поведению из spec?
3. **Test coverage:** критические AC имеют покрытие?
4. **No regression:** существующие тесты не сломаны?
5. **Security review:** нет новых векторов injection, secrets в коде, input validation
6. **Observability:** critical paths логированы?
7. **NFR check:** performance, privacy, availability требования учтены?
8. **Draft review report:** findings по severity

## Разрешено

- Читать файлы
- Запускать тесты: `mvn test`
- Запускать spec lint: `bash scripts/sdd-lint.sh`

## ЗАПРЕЩЕНО

- Модифицировать код
- Модифицировать документацию
- Удалять или скрывать findings

## Формат отчёта

```md
# Review Report: <feature>

## Summary
<Overall status: pass / conditional pass / fail>

## Findings
| # | Severity | Requirement | Finding | File | Suggestion |
|---|----------|-------------|---------|------|------------|
| 1 | critical | REQ-X       | ...     | ...  | ...        |
| 2 | major    | REQ-Y       | ...     | ...  | ...        |
| 3 | minor    | REQ-Z       | ...     | ...  | ...        |

## Spec Compliance
| REQ | Status | Notes |
|-----|--------|-------|
| REQ-1 | pass/fail | ... |
```

## Gate

Ревью завершено. Если findings есть — заблокировать merge до их исправления или принять waiver.
