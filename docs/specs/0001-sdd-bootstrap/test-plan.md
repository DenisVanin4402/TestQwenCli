# Test Plan: SDD Bootstrap

## Strategy

Так как изменение документное и процедурное, основная проверка состоит из smoke-тестов приложения и ручной проверки структуры артефактов.

## Automated Checks

```bash
mvn test
```

Ожидаемый результат: существующие Spring Boot тесты проходят.

## Manual Checks

| ID | Requirement | Проверка | Ожидаемый результат |
|---|---|---|---|
| CHECK-1 | REQ-1 | Проверить `docs/specs/0001-sdd-bootstrap/` | Нужные файлы присутствуют |
| CHECK-2 | REQ-2 | Проверить `docs/sdd/` | Governance-файлы присутствуют |
| CHECK-3 | REQ-3 | Проверить `docs/specs/_template/` | Шаблоны и `contracts/README.md` присутствуют |
| CHECK-4 | REQ-4 | Проверить `.qwen/commands/sdd/` | Команды specify, clarify, plan, tasks, implement, review присутствуют |
| CHECK-5 | REQ-5 | Проверить `.qwen/agents/` | Reviewer и security reviewer read-only |
| CHECK-6 | REQ-7 | Проверить инструкции | Pipeline описан из корня проекта |

## Traceability Matrix

| Req | Acceptance criteria | Tests | Code/docs | Status |
|---|---|---|---|---|
| REQ-1 | AC-1 | CHECK-1 | `docs/specs/0001-sdd-bootstrap/` | pass |
| REQ-2 | AC-2 | CHECK-2 | `docs/sdd/` | pass |
| REQ-3 | AC-3 | CHECK-3 | `docs/specs/_template/` | pass |
| REQ-4 | AC-4 | CHECK-4 | `.qwen/commands/sdd/` | pass |
| REQ-5 | AC-5 | CHECK-5 | `.qwen/agents/` | pass |
| REQ-6 | AC-6 | CHECK-5 | `.qwen/skills/` | pass |
| REQ-7 | AC-6, AC-7 | `mvn test`, CHECK-6 | `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md`, `README.md`, `src/test/java/com/example/testqwencli/SddArtifactTests.java` | pass |
