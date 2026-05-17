# TestQwenCli

Spring Boot application scaffolded as a Maven project for IntelliJ IDEA.

## Requirements

- Java 21
- Maven 3.9+

## Development

Run tests:

```bash
mvn test
```

Run the application:

```bash
mvn spring-boot:run
```

In IntelliJ IDEA, open the repository root and reload/import `pom.xml` as a Maven project.

## SDD workflow

Проект содержит файловый Specification-Driven Development baseline:

- `docs/sdd/` - constitution, workflow, gates и context management.
- `docs/sdd/instruction.md` - подробная инструкция по идее и использованию SDD-фреймворка.
- `docs/specs/0001-sdd-bootstrap/` - первая dogfooding-спека.
- `docs/specs/_template/` - шаблон новой задачи.
- `.qwen/commands/sdd/` - Qwen slash-команды `/sdd:*`.
- `.qwen/skills/` и `.qwen/agents/` - процедуры и роли для агентской работы.

Основная цепочка:

```text
/sdd:specify -> /sdd:clarify -> /sdd:plan -> /sdd:tasks -> /sdd:implement -> /sdd:review
```

Для сценария, где системный аналитик уже внес изменения в документацию, OpenAPI или DBML в своем MR, используйте diff-driven цепочку:

```text
/sdd:intake-diff -> /sdd:impact-map -> /sdd:synthesize-spec -> /sdd:clarify -> /sdd:plan -> /sdd:tasks -> /sdd:implement -> /sdd:review
```

Перед существенной реализацией создайте `docs/specs/<id>-<slug>/` из шаблона и ведите проверки в `work-log.md`.
