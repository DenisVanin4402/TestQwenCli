---
description: Создать или обновить спецификацию для новой фичи. Используется на фазе Specify.
---

# /sdd:specify

Создай спецификацию для новой задачи согласно SDD-процессу.

## Входные данные

Пользователь описывает что хочет реализовать — {{args}}

## Что читать

1. `docs/sdd/constitution.md` — проверить конституционные ограничения
2. `docs/sdd/workflow.md` — понять фазу Specify
3. `docs/sdd/gates.md` — Spec Ready gate
4. `docs/specs/_template/spec.md` — шаблон спецификации
5. Существующие спеки в `docs/specs/` — проверить дубликаты

## Что делать

1. Определить следующий доступный ID спецификации (000N-slug)
2. Скопировать `docs/specs/_template/spec.md` в `docs/specs/000N-slug/spec.md`
3. Заполнить секции problem, users, goals, non-goals
4. Сформировать user stories
5. Создать requirements с уникальными IDs (REQ-1, REQ-2, ...)
6. Создать acceptance criteria с IDs (AC-1, AC-2, ...)
7. Добавить NFR секции (security, privacy, performance, availability, observability)
8. Выявить неоднозначности — пометить как `[NEEDS CLARIFICATION]`
9. Заполнить initial матрицу трассировки

## Разрешено изменять

- `docs/specs/000N-slug/spec.md` — создание новой спеки

## ЗАПРЕЩЕНО изменять

- Существующий код
- Другие спеки
- Существующие ADR

## Gate

Остановиться после создания spec.md. Не переходить к plan или tasks. Сообщить пользователю:
- Какие REQ и AC созданы
- Сколько `[NEEDS CLARIFICATION]` вопросов требует ответа
- Предложить следующий шаг: `/sdd:clarify`
