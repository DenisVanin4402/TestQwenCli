# Технический план: SDD Bootstrap

## Обзор

Создание полной структуры SDD-артефактов в проекте Spring Boot для полигона Specification-Driven Development и AI-агентной разработки.

## Подход

Последовательное создание файлов по фазам из `docs/sdd-plan.md`:

- **Фаза 0** (Bootstrap): governance docs + AGENTS.md + CONVENTIONS.md + first spec
- **Фаза 1** (Templates): `docs/specs/_template/` + ADR шаблон
- **Фаза 2** (Commands/Skills): Qwen CLI commands + skills + agents
- **Фаза 3** (Verification): spec lint скрипт

Все артефакты создаются как Markdown-файлы. No code changes к существующему приложению.

## Затронутые компоненты

| Компонент | Изменение |
|-----------|-----------|
| `docs/sdd/` | 4 governance файла (создание) |
| `docs/specs/` | 1 spec (0001-sdd-bootstrap) (создание) |
| `docs/specs/_template/` | 11+ шаблонов (создание) |
| `docs/adr/` | 2 файла (template + первый ADR) (создание) |
| `.qwen/commands/sdd/` | 6 команд (создание) |
| `.qwen/skills/` | 5 навыков (создание) |
| `.qwen/agents/` | 4 роли агентов (создание) |
| `scripts/` | 1 скрипт (sdd-lint.sh) (создание) |
| `AGENTS.md` | новый файл (создание) |
| `CONVENTIONS.md` | новый файл (создание) |
| `src/` | no change |
| `pom.xml` | no change |

## Зависимости

- Нет внешних зависимостей. Только Markdown-файлы и shell-скрипт.

## Контракты

- Нет изменений API. Spec lint скрипт — новый внутренний tool.

## Спецификации

- Эта реализация соответствует `docs/sdd-plan.md` Phase 0-3.
- Requirements: REQ-1 через REQ-8 из `docs/specs/0001-sdd-bootstrap/spec.md`.

## Риски

- Большой объём файлов — важно поддерживать единый стиль.
- Spec lint script должен быть cross-platform (bat/ps1 для Windows, bash для Linux/Mac).
