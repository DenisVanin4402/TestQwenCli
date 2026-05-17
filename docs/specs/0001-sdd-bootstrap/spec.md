# Feature: SDD Bootstrap

Дата: 2026-05-17  
Статус: accepted for implementation  
Источник: `docs/sdd-plan.md`

## Problem

Проект уже содержит исследование и план SDD, но у него нет переносимой файловой структуры, через которую агенты и люди могут вести задачи по единому pipeline. Без такой структуры спецификации, команды, роли агентов, проверки и handoff остаются неформальными.

## Users

- Разработчик, который ведет задачу через SDD.
- Coding agent, который реализует ограниченный scope.
- Reviewer или security reviewer, который проверяет результат read-only.
- Maintainer, который принимает изменения и обновляет правила.

## Goals

- Ввести версионируемые SDD-артефакты в `docs/specs/`, `docs/sdd/` и `docs/adr/`.
- Добавить Qwen CLI entrypoints в `.qwen/commands/sdd/`.
- Добавить роли агентов в `.qwen/agents/`.
- Добавить skills для повторяемых SDD-процедур в `.qwen/skills/`.
- Обновить проектные инструкции так, чтобы новый pipeline был виден без чтения исследования целиком.

## Non-goals

- Не внедрять RAG, CI gates и сложную multi-agent orchestration.
- Не менять runtime-поведение Spring Boot приложения.
- Не добавлять production-зависимости.
- Не требовать ADR для каждой мелкой правки.

## Requirements

| ID | Требование |
|---|---|
| REQ-1 | Проект должен содержать bootstrap-спеку SDD с планом, задачами и test plan. |
| REQ-2 | Проект должен содержать governance-документы SDD: constitution, workflow, gates и context management. |
| REQ-3 | Проект должен содержать шаблон новой SDD-задачи с обязательными секциями и traceability matrix. |
| REQ-4 | Проект должен содержать Qwen slash-command definitions для фаз specify, clarify, plan, tasks, implement и review. |
| REQ-5 | Проект должен содержать Qwen agent role definitions для orchestration, specification, planning, implementation, testing, review и security review. |
| REQ-6 | Проект должен содержать Qwen skills для spec review, planning, task slicing, test-gap analysis и review. |
| REQ-7 | Проектные инструкции должны объяснять, как запускать SDD pipeline и какие gates обязательны. |

## Acceptance Criteria

| ID | Критерий |
|---|---|
| AC-1 | `docs/specs/0001-sdd-bootstrap/` содержит `spec.md`, `plan.md`, `tasks.md`, `test-plan.md`, `task-state.md`, `work-log.md` и `handoff.md`. |
| AC-2 | `docs/sdd/` содержит `constitution.md`, `workflow.md`, `gates.md` и `context-management.md`. |
| AC-3 | `docs/specs/_template/` содержит полный набор шаблонов для новой задачи, включая `contracts/README.md`. |
| AC-4 | `.qwen/commands/sdd/` содержит Markdown-файлы для всех основных фаз SDD. |
| AC-5 | `.qwen/agents/` содержит read-only роли reviewer и security reviewer. |
| AC-6 | `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md` или `README.md` содержат инструкции по SDD pipeline. |
| AC-7 | `mvn test` проходит после добавления артефактов. |

## Edge Cases

- Если задача слишком мала для полной спеки, пользователь может явно разрешить direct implementation, но результат все равно должен ссылаться на запрос и проверки.
- Если требования и код расходятся, работа останавливается на gate и расхождение фиксируется в `work-log.md`.
- Если review обнаруживает архитектурное решение без ADR, создается follow-up или ADR до приемки.

## NFR

- Security: секреты и локальные credentials не записываются в SDD-артефакты.
- Privacy: пользовательские данные не сохраняются в memory без явного основания.
- Performance: SDD-процесс не должен требовать чтения всего репозитория для каждой команды.
- Availability: отсутствие Qwen CLI не блокирует ручное использование Markdown-артефактов.
- Observability: проверки и решения фиксируются в `work-log.md`.

## Open Questions

- Нет критических открытых вопросов для bootstrap.

