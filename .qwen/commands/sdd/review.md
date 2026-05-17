---
description: Провести read-only review реализации против SDD-артефактов.
---

# /sdd:review

Проверь реализацию:

```text
{{args}}
```

## Вход

- Diff или список измененных файлов.
- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/plan.md`
- `docs/specs/<id>-<slug>/test-plan.md`
- `docs/specs/<id>-<slug>/tasks.md`

## Читать

- SDD-артефакты текущей задачи.
- Измененные файлы.
- Релевантные тесты и контракты.
- `docs/sdd/gates.md`
- Релевантные ADR.

## Можно изменять

- Ничего. Review read-only.

## Процедура

1. Сопоставь diff с requirement IDs и AC.
2. Проверь, что тесты покрывают критические AC или gaps явно приняты.
3. Проверь публичные контракты и обратную совместимость.
4. Проверь security/privacy/observability из NFR.
5. Выдай findings по severity.

## Результат

- Findings first.
- Для каждого finding: requirement/AC, файл и строка, почему это дефект, какая проверка поймала бы его.
- Если дефектов нет, явно указать residual risks и test gaps.

## Stop gate

Остановись на review gate, если spec, code и tests расходятся или traceability matrix имеет критический gap.

