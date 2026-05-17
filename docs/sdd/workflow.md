# SDD Workflow

Проект использует минимальный агентский Specification-Driven Development pipeline:

```text
specification -> clarification -> plan -> tasks -> tests/contracts -> implementation -> verification -> spec/memory update
```

## Режимы входа

Фреймворк поддерживает два режима.

| Режим | Когда использовать | Первый источник |
|---|---|---|
| `spec-first` | Бизнес-изменение описано запросом, issue или устным требованием | `spec.md` |
| `diff-driven` | Системный аналитик уже изменил Markdown-документацию, OpenAPI или DBML в своем MR | `intake.md`, `diff-map.md`, `impact-map.md`, `source-context.md` |

### Spec-first pipeline

```text
business request -> specification -> clarification -> plan -> tasks -> implementation -> review
```

### Diff-driven pipeline

```text
business request + analyst MR diff
  -> intake-diff
  -> classify changes
  -> extract source context
  -> build impact map
  -> synthesize spec
  -> clarify gaps
  -> plan
  -> tasks
  -> implement
  -> review
```

В `diff-driven` режиме итоговый `spec.md` не должен копировать всю проектную документацию. Он содержит только изменения, source references, requirement candidates, acceptance criteria и минимальный косвенный контекст.

## Фазы

| Фаза | Вход | Выход | Gate |
|---|---|---|---|
| Specify | Запрос пользователя или issue | `spec.md`, `requirements.md` | Цели, non-goals, требования и AC понятны |
| Clarify | Черновик спеки | Закрытые вопросы или явные open questions | Нет критических незакрытых уточнений |
| Plan | Готовая спека | `plan.md`, `research.md`, ADR при необходимости | Выбран технический путь и проверки |
| Tasks | План | `tasks.md`, `task-state.md` | Задачи малы, проверяемы и имеют ownership |
| Verify-first | Требования и задачи | `test-plan.md`, `contracts/`, тесты или explicit gap | Критические AC покрыты тестом или исключением |
| Implement | Context packet и задача | Код, тесты, обновленный `work-log.md` | Релевантные проверки проходят |
| Review | Diff, спека, тесты | Findings или accepted review | Spec/code/tests согласованы |
| Learn | Итоги работы | Обновленные правила, ADR, templates или follow-up | Повторяемые знания вынесены из чата |

## Diff-driven артефакты

| Артефакт | Назначение |
|---|---|
| `intake.md` | Фиксирует business request, MR, base branch, analyst branch и входные источники |
| `diff-map.md` | Классифицирует измененные файлы и semantic blocks |
| `impact-map.md` | Показывает direct и indirect impacts |
| `source-context.md` | Содержит минимальный контекст с точными source references |
| `contracts/openapi-diff.md` | Отдельная сводка изменений API |
| `contracts/dbml-diff.md` | Отдельная сводка изменений модели данных |

## Context Packet

Перед реализацией или review агент получает не весь репозиторий, а короткий пакет:

```md
# Context Packet

## Goal
...

## Current phase
...

## Source of truth
- Spec: docs/specs/<id>-<slug>/spec.md
- Plan: docs/specs/<id>-<slug>/plan.md
- Tasks: docs/specs/<id>-<slug>/tasks.md

## Acceptance criteria
- AC-...

## Constraints
- ...

## Relevant files
- ...

## Current state
- Done: ...
- Not done: ...
- Blockers: ...

## Verification commands
- `mvn test`

## Output required
- Changed files
- Tests run
- Spec compliance notes
- Residual risks
```

## Обновление артефактов

- Новые стабильные правила идут в `AGENTS.md`, `QWEN.md` или `CONVENTIONS.md`.
- Архитектурные решения идут в `docs/adr/`.
- Текущее состояние задачи остается в `task-state.md` и `work-log.md`.
- Повторяемая процедура оформляется как skill или command.
