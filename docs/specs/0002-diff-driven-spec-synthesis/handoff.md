# Handoff: Diff-driven specification synthesis

## Next agent role

Reviewer.

## Goal

Проверить, что фреймворк поддерживает diff-driven mode согласно `docs/sdd/diff-driven-spec-synthesis-plan.md`.

## Source of truth

- `docs/sdd/diff-driven-spec-synthesis-plan.md`
- `docs/specs/0002-diff-driven-spec-synthesis/spec.md`
- `docs/specs/0002-diff-driven-spec-synthesis/test-plan.md`

## Done

- Добавлены шаблоны diff-driven артефактов.
- Добавлены команды, skills и agent roles.
- Обновлены governance и root instructions.
- Расширен структурный тест.

## Not done

- Нет.

## Modified files

- `docs/specs/0002-diff-driven-spec-synthesis/**`
- `docs/specs/_template/**`
- `docs/sdd/**`
- `.qwen/**`
- `AGENTS.md`
- `QWEN.md`
- `CONVENTIONS.md`
- `README.md`
- `src/test/java/com/example/testqwencli/SddArtifactTests.java`

## Commands run

- `mvn test` -> passed, 7 tests, 0 failures, 0 errors, 0 skipped

## Blockers

- Нет.

## Do not change

- Runtime-код Spring Boot приложения.

## Required output

- Findings first.
- Проверка `REQ-*` и `AC-*`.
- Команды проверки и результат.
