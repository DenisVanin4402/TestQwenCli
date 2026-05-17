# Tasks: Diff-driven specification synthesis

| ID | Requirement | Ownership scope | Depends on | Verification | Status |
|---|---|---|---|---|---|
| TASK-1 | REQ-1 | `docs/sdd/*`, `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md`, `README.md` | - | AC-1 | done |
| TASK-2 | REQ-2, REQ-3 | `docs/specs/_template/*` | - | AC-2, AC-3 | done |
| TASK-3 | REQ-4 | `.qwen/commands/sdd/*` | TASK-2 | AC-4 | done |
| TASK-4 | REQ-5 | `.qwen/skills/*` | TASK-2 | AC-5 | done |
| TASK-5 | REQ-6 | `.qwen/agents/*` | TASK-2 | AC-6 | done |
| TASK-6 | REQ-7 | `src/test/java/com/example/testqwencli/SddArtifactTests.java` | TASK-2, TASK-3, TASK-4, TASK-5 | AC-7, `mvn test` | done |
