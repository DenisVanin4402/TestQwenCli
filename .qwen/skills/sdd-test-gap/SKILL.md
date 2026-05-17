---
name: sdd-test-gap
description: Находить gaps между requirements, acceptance criteria, тестами, контрактами и verification evidence.
---

# SDD Test Gap

## Inputs

- `requirements.md`
- `test-plan.md`
- Текущие тесты и контракты.
- `work-log.md`

## Procedure

1. Построй матрицу `REQ -> AC -> tests/contracts -> evidence`.
2. Отметь критические AC без проверки.
3. Проверь, что explicit gaps имеют причину и владельца.
4. Предложи минимальные тесты для закрытия gaps.
5. Не меняй production-код в рамках анализа.

## Output

- Список gaps по severity.
- Рекомендуемые тесты или contract checks.
- Обновления для traceability matrix, если разрешено редактирование.

