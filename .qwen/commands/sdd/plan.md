---
description: Построить технический SDD-план по готовой спецификации.
---

# /sdd:plan

Создай план реализации для:

```text
{{args}}
```

## Вход

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/requirements.md`
- `docs/specs/_template/plan.md`

## Читать

- `AGENTS.md`
- `QWEN.md`
- `CONVENTIONS.md`
- `docs/sdd/workflow.md`
- `docs/sdd/gates.md`
- `docs/adr/**`, если затронута архитектура.
- Только релевантные файлы кода через targeted search.

## Можно изменять

- `docs/specs/<id>-<slug>/plan.md`
- `docs/specs/<id>-<slug>/research.md`
- `docs/specs/<id>-<slug>/data-model.md`
- `docs/specs/<id>-<slug>/test-plan.md`
- `docs/adr/*.md`, если ADR действительно нужен.

## Процедура

1. Сопоставь requirements и AC с текущей структурой проекта.
2. Перечисли affected files и public contracts.
3. Опиши технический подход и альтернативы.
4. Определи стратегию тестирования.
5. Зафиксируй риски, rollback и необходимость ADR.
6. Не меняй production-код.

## Результат

- `plan.md` с affected files, contracts, tests и risks.
- Ссылки на ADR или явное "ADR не требуется".
- Verification commands.
- Следующий gate: `/sdd:tasks`.

## Stop gate

Остановись, если plan требует изменения публичного контракта без обновления contract/test strategy или ADR.

