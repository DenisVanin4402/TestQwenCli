---
description: Создать или обновить SDD-спецификацию для новой задачи.
---

# /sdd:specify

Создай или обнови спецификацию для:

```text
{{args}}
```

## Вход

- Запрос пользователя, issue или краткое описание задачи.
- `docs/sdd/constitution.md`
- `docs/sdd/gates.md`
- `docs/specs/_template/spec.md`
- При наличии: существующий каталог `docs/specs/<id>-<slug>/`.

## Читать

- `AGENTS.md`
- `QWEN.md`
- `CONVENTIONS.md`
- `docs/sdd/constitution.md`
- `docs/specs/_template/spec.md`
- Только релевантные существующие specs.

## Можно изменять

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/requirements.md`
- `docs/specs/<id>-<slug>/work-log.md`

## Процедура

1. Определи ID и slug задачи.
2. Сформулируй problem, users, goals и non-goals.
3. Запиши требования с ID `REQ-*`.
4. Запиши acceptance criteria с ID `AC-*`.
5. Отдельно запиши edge cases и NFR.
6. Если есть критическая неопределенность, остановись на clarification gate.

## Результат

- Путь к созданной или обновленной spec.
- Список requirement IDs.
- Список открытых вопросов.
- Следующий gate: `/sdd:clarify` или `/sdd:plan`.

## Stop gate

Остановись, если критические требования неоднозначны или пользовательское намерение нельзя проверить acceptance criteria.

