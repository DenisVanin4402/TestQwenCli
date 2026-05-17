# Test Plan: Diff-driven specification synthesis

## Strategy

Изменение является документным и процедурным. Проверка состоит из структурного теста на наличие новых артефактов и ручной проверки соответствия требованиям.

## Automated Checks

```bash
mvn test
```

## Manual Checks

| ID | Requirement | Проверка | Ожидаемый результат |
|---|---|---|---|
| CHECK-1 | REQ-1 | Проверить governance и root docs | `diff-driven mode` описан |
| CHECK-2 | REQ-2 | Проверить `docs/specs/_template/` | intake/diff/impact/source шаблоны есть |
| CHECK-3 | REQ-3 | Проверить `docs/specs/_template/contracts/` | OpenAPI/DBML diff шаблоны есть |
| CHECK-4 | REQ-4 | Проверить `.qwen/commands/sdd/` | Три новые команды есть |
| CHECK-5 | REQ-5 | Проверить `.qwen/skills/` | Пять новых skills есть |
| CHECK-6 | REQ-6 | Проверить `.qwen/agents/` | Четыре новые роли есть |
| CHECK-7 | REQ-7 | Проверить `SddArtifactTests` | Новые артефакты включены |

## Traceability Matrix

| Req | Acceptance criteria | Tests | Code/docs | Status |
|---|---|---|---|---|
| REQ-1 | AC-1 | CHECK-1 | `docs/sdd/*`, root docs | pass |
| REQ-2 | AC-2 | CHECK-2 | `docs/specs/_template/` | pass |
| REQ-3 | AC-3 | CHECK-3 | `docs/specs/_template/contracts/` | pass |
| REQ-4 | AC-4 | CHECK-4 | `.qwen/commands/sdd/` | pass |
| REQ-5 | AC-5 | CHECK-5 | `.qwen/skills/` | pass |
| REQ-6 | AC-6 | CHECK-6 | `.qwen/agents/` | pass |
| REQ-7 | AC-7 | `mvn test`, CHECK-7 | `SddArtifactTests.java` | pass |
