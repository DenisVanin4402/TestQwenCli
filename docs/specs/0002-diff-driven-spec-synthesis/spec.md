# Feature: Diff-driven specification synthesis

Дата: 2026-05-17  
Статус: implemented

## Problem

Текущий SDD-фреймворк поддерживает `spec-first` режим, где первая спецификация создается из бизнес-запроса. В реальном процессе возможен другой вход: системный аналитик уже внес изменения в Markdown-документацию, OpenAPI и DBML в отдельном merge request. Разработке нужна первая SDD-спецификация, синтезированная из бизнес-требований, diff аналитика и минимального затронутого контекста.

## Users

- Разработчик, который реализует изменения после MR системного аналитика.
- Coding agent, который строит change-spec по diff.
- Reviewer, который проверяет, что synthesized spec не включает лишний scope.
- System analyst, который предоставляет MR с изменениями документации, OpenAPI и DBML.

## Goals

- Добавить в фреймворк `diff-driven mode`.
- Добавить шаблоны `intake.md`, `diff-map.md`, `impact-map.md`, `source-context.md`.
- Добавить шаблоны сводок OpenAPI и DBML diff.
- Добавить Qwen-команды для diff intake, impact map и spec synthesis.
- Добавить skills и agent roles для анализа Markdown, OpenAPI, DBML и синтеза спеки.
- Обновить governance и инструкции фреймворка.

## Non-goals

- Не реализовывать полноценный парсер OpenAPI/DBML в первом проходе.
- Не подключать внешние системы GitLab/GitHub.
- Не внедрять CI-gates.
- Не менять runtime-код Spring Boot приложения.

## Requirements

| ID | Требование |
|---|---|
| REQ-1 | Фреймворк должен описывать два режима входа: `spec-first` и `diff-driven`. |
| REQ-2 | Шаблон SDD-задачи должен поддерживать артефакты diff intake, impact map и source context. |
| REQ-3 | Шаблон должен иметь отдельные артефакты для OpenAPI diff и DBML diff. |
| REQ-4 | Qwen commands должны поддерживать `/sdd:intake-diff`, `/sdd:impact-map` и `/sdd:synthesize-spec`. |
| REQ-5 | Qwen skills должны поддерживать diff intake, impact analysis, OpenAPI diff, DBML diff и spec synthesis. |
| REQ-6 | Agent roles должны покрывать doc diff analysis, contract analysis, data model analysis и spec synthesis. |
| REQ-7 | Структурная проверка SDD-артефактов должна учитывать новые файлы diff-driven режима. |

## Acceptance Criteria

| ID | Requirement | Критерий |
|---|---|---|
| AC-1 | REQ-1 | `docs/sdd/workflow.md`, `docs/sdd/instruction.md`, `AGENTS.md`, `QWEN.md` или `CONVENTIONS.md` описывают `diff-driven mode`. |
| AC-2 | REQ-2 | `docs/specs/_template/` содержит `intake.md`, `diff-map.md`, `impact-map.md`, `source-context.md`. |
| AC-3 | REQ-3 | `docs/specs/_template/contracts/` содержит `openapi-diff.md` и `dbml-diff.md`. |
| AC-4 | REQ-4 | `.qwen/commands/sdd/` содержит `intake-diff.md`, `impact-map.md`, `synthesize-spec.md`. |
| AC-5 | REQ-5 | `.qwen/skills/` содержит новые diff-driven skills. |
| AC-6 | REQ-6 | `.qwen/agents/` содержит новые roles для анализа diff и синтеза спеки. |
| AC-7 | REQ-7 | `SddArtifactTests` проверяет наличие новых артефактов. |

## Edge Cases

- Если MR аналитика содержит несвязанные изменения, они должны попасть в `diff-map.md` как excluded changes.
- Если OpenAPI изменился без соответствующего бизнес-описания, это фиксируется как open question.
- Если DBML изменился без миграционной стратегии, это фиксируется как data question.
- Если synthesized spec требует поведения, которого нет в diff или бизнес-требованиях, это drift и gate должен остановить работу.

## NFR

- Security: diff-driven анализ не должен записывать секреты из diff в specs или logs.
- Privacy: source context должен содержать summary и ссылки, а не большие копии приватных документов.
- Performance: агент не должен читать всю документацию проекта, если impact map позволяет ограничить контекст.
- Observability: итог анализа фиксируется в `work-log.md`.

## Open Questions

- Нет критических вопросов для первого прохода. Автоматический парсинг OpenAPI/DBML остается follow-up.

