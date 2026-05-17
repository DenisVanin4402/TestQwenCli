# Handoff: SDD Bootstrap

## Следующая роль агента

Builder — продолжить создание Qwen CLI команд, навыков и ролей агентов.

## Цель

Завершить TSK-4, TSK-5, TSK-6, TSK-9: создать все Qwen CLI команды, навыки, роли агентов и spec lint скрипт.

## Source of truth

- Spec: `docs/specs/0001-sdd-bootstrap/spec.md`
- Plan: `docs/specs/0001-sdd-bootstrap/plan.md`
- Tasks: `docs/specs/0001-sdd-bootstrap/tasks.md`
- Current task state: `docs/specs/0001-sdd-bootstrap/task-state.md`

## Сделано

- Все директории созданы
- Governance-документы (4 файла) написаны
- AGENTS.md и CONVENTIONS.md созданы
- Все 12 шаблонов артефактов созданы
- ADR template и первая ADR созданы
- Spec, plan, tasks, test-plan, task-state, work-log для bootstrap

## Не сделано

- TSK-4: 6 Qwen CLI команд в `.qwen/commands/sdd/`
- TSK-5: 5 Qwen skills в `.qwen/skills/`
- TSK-6: 4 роли агентов в `.qwen/agents/`
- TSK-9: `scripts/sdd-lint.sh`

## Изменённые файлы

См. `task-state.md` — список изменённых файлов.

## Запущенные команды и результаты

- None пока — следующему агенту нужно запустить `mvn test` для регрессионной проверки.

## Блокеры

- None

## НЕЛЬЗЯ менять

- `docs/sdd/constitution.md` — конституция проекта
- `docs/sdd/gates.md` — gate определения
- `docs/adr/0001-record-architecture-decisions.md` — первое архитектурное решение
- Существующий Java-код приложения (`src/`)

## Требуемый результат

- Все 6 команд SDD созданы в `.qwen/commands/sdd/`
- Все 5 навыков созданы в `.qwen/skills/`
- Все 4 роли агентов созданы в `.qwen/agents/`
- Spec lint скрипт создан и работает
- Обновить `task-state.md` и `work-log.md`
- Запустить `mvn test` — без регрессий
