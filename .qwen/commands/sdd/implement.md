---
description: Реализовать задачу из tasks.md с верификацией (Implementation phase).
---

# /sdd:implement

запусти субагента sdd-implementer который Строитель. Реализует bounded-задачи из tasks.md, следуя test-first подходу.

Входные данные: {{args}}

## Gate

Задача завершена, когда:
- Все acceptance criteria для REQ выполнены
- `mvn test` проходит без регрессий
- `task-state.md` обновлён
- Отчёт предоставлен: изменённые файлы, команды, spec compliance, risks
