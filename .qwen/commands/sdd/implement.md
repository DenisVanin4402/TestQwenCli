---
description: Реализовать ограниченную SDD-задачу по context packet.
---

# /sdd:implement

Реализуй задачу:

```text
{{args}}
```

## Вход

- Один task ID из `docs/specs/<id>-<slug>/tasks.md`.
- Context packet с source of truth, AC, constraints, relevant files и verification commands.

## Читать

- `docs/specs/<id>-<slug>/spec.md`
- `docs/specs/<id>-<slug>/plan.md`
- `docs/specs/<id>-<slug>/tasks.md`
- `docs/specs/<id>-<slug>/test-plan.md`
- Только файлы в ownership scope.

## Можно изменять

- Файлы в ownership scope задачи.
- Релевантные тесты.
- `docs/specs/<id>-<slug>/work-log.md`
- `docs/specs/<id>-<slug>/task-state.md`
- `docs/specs/<id>-<slug>/handoff.md`

## Процедура

1. Проверь, что task ссылается на requirement IDs.
2. Напиши или обнови тесты для изменяемого поведения.
3. Реализуй минимальное изменение.
4. Запусти verification commands.
5. Запиши результат в `work-log.md`.
6. Если scope меняется, остановись и вернись к `/sdd:plan`.

## Результат

- Измененные файлы.
- Закрытые requirement IDs и AC.
- Команды проверки и результат.
- Остаточные риски.

## Stop gate

Остановись, если задача требует файлов вне ownership scope, нового публичного контракта, новой зависимости или незакрытого security-решения.

