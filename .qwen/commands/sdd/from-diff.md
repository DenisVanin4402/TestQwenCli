---
description: Сгенерировать delta-spec из git diff MR аналитика (branch или commit compare).
---

# /sdd:from-diff

при запуске в контекст передай обязательность использования скилла **sdd-diff-analyzer**

Действуй по процедуре из навыка: извлечь diff → классифицировать изменения → построить impact-map → сгенерировать delta-spec → plan.md → tasks.md → task-state.md.

Входные данные: {{args}}

## Gate

Остановиться после создания delta-spec, plan.md, tasks.md, task-state.md.
Сообщить: созданные файлы, количество REQ-NEW-*/AC-NEW-*, вопросы [NEEDS CLARIFICATION].
