---
description: Сгенерировать вопросы для уточнения требований (Clarification phase).
---

# /sdd:clarify

Сгенерируй вопросы для уточнения неоднозначностей в текущей спецификации.

## Входные данные

Путь к спецификации или `{{args}}`

## Что читать

1. `docs/specs/000N-slug/spec.md` — целевая спецификация
2. `docs/sdd/gates.md` — Spec Ready gate требования

## Что делать

1. Найти все `[NEEDS CLARIFICATION]` в spec.md
2. Для каждого вопроса сформулировать конкретный уточняющий вопрос пользователю
3. Определить какие пути критические (blocking) vs non-blocking
4. Создать `docs/specs/000N-slug/clarifications.md` или обновить spec.md секцию
5. Ответы пользователя интегрировать обратно в spec.md

## Разрешено изменять

- `docs/specs/000N-slug/spec.md` — обновить answered questions
- `docs/specs/000N-slug/clarifications.md` — создать, если нужно

## ЗАПРЕЩЕНО изменять

- Код
- ADR
- Другие спеки

## Gate

Остановиться когда:
- Все `[NEEDS CLARIFICATION]` получили ответы ИЛИ
- Пользователь явно перенёс вопросы в follow-up
