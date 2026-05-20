---
description: Создать или обновить спецификацию для новой фичи. Используется на фазе Specify.
---

# /sdd:specify

запусти субагента sdd-spec-writer который Формирует спецификацию из пользовательского запроса: problem, users, goals, requirements, acceptance criteria, NFR.

Входные данные: {{args}}

## Gate

Остановиться после создания spec.md. Сообщить:
- Какие REQ и AC созданы
- Сколько `[NEEDS CLARIFICATION]` вопросов требует ответа
- Предложить следующий шаг: `/sdd:clarify`
