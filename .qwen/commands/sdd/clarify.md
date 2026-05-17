---
description: Закрыть неоднозначности в SDD-спецификации перед планированием.
---

# /sdd:clarify

Уточни спецификацию для:

```text
{{args}}
```

## Вход

- Каталог `docs/specs/<id>-<slug>/`.
- Open questions из `spec.md` или `requirements.md`.

## Читать

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/requirements.md`
- `docs/sdd/gates.md`

## Можно изменять

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/requirements.md`
- `docs/specs/<id>-<slug>/work-log.md`

## Процедура

1. Найди критические open questions.
2. Сгруппируй вопросы по влиянию на scope, contract, data, tests и security.
3. Если ответ уже выводится из файлов проекта, зафиксируй источник.
4. Если нужен human gate, задай короткий список вопросов.
5. После ответов обнови требования и AC.

## Результат

- Закрытые вопросы.
- Оставшиеся некритические вопросы.
- Обновленные requirement IDs и AC.
- Следующий gate: `/sdd:plan`.

## Stop gate

Остановись, если без ответа пользователя можно ошибочно изменить публичное поведение, данные или security boundary.

