# Plan: SDD Bootstrap

## Summary

Внедрение выполняется как документная и процедурная надстройка над существующим Spring Boot проектом. Runtime-код не меняется.

## Affected Areas

- `docs/sdd/` - governance и pipeline.
- `docs/specs/0001-sdd-bootstrap/` - dogfooding-спека внедрения.
- `docs/specs/_template/` - шаблоны новых задач.
- `docs/adr/` - правила фиксации архитектурных решений.
- `.qwen/commands/sdd/` - slash-command definitions.
- `.qwen/skills/` - повторяемые процедуры.
- `.qwen/agents/` - роли агентов.
- `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md`, `README.md` - инструкции.

## Technical Approach

1. Добавить переносимые Markdown-артефакты без новых зависимостей.
2. Разделить governance, шаблоны, команды, skills и agents по разным каталогам.
3. В Qwen-командах явно указать вход, читаемые файлы, разрешенные изменения, результат и stop gate.
4. В agent definitions зафиксировать права: reviewer и security reviewer read-only.
5. Обновить инструкции проекта, чтобы SDD pipeline был виден из корня репозитория.

## Contracts

Публичные HTTP-контракты приложения не меняются.

## Data Model

Модель данных приложения не меняется.

## ADR

Создается `docs/adr/0001-record-architecture-decisions.md`, потому что проект вводит новый decision log.

## Verification

- `mvn test`
- Ручная проверка наличия файлов из `AC-*`.
- Ручная проверка, что Qwen-команды не требуют чтения всего репозитория.

## Risks

- Слишком подробные шаблоны могут замедлять малые задачи. Смягчение: direct implementation допускается по явному запросу пользователя.
- Команды Qwen могут потребовать дальнейшей адаптации под конкретную версию CLI. Смягчение: команды оформлены как Markdown wrappers без runtime-зависимостей.

