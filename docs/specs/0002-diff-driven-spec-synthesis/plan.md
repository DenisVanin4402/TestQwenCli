# Plan: Diff-driven specification synthesis

## Summary

Реализация выполняется как расширение документного SDD-фреймворка. Добавляются новые шаблоны, команды, skills, roles и governance-правила для сценария, где входом является MR системного аналитика с изменениями в Markdown, OpenAPI и DBML.

## Affected Files

- `docs/sdd/workflow.md` - добавить второй pipeline.
- `docs/sdd/gates.md` - добавить Diff Intake, Source Context, Impact, Contract/Data, Synthesis и Drift gates.
- `docs/sdd/context-management.md` - описать source context и минимальный indirect context.
- `docs/sdd/instruction.md` - добавить практическую инструкцию по diff-driven mode.
- `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md`, `README.md` - зафиксировать новый режим и команды.
- `docs/specs/_template/` - добавить новые шаблоны.
- `.qwen/commands/sdd/` - добавить три команды.
- `.qwen/skills/` - добавить пять skills.
- `.qwen/agents/` - добавить четыре роли.
- `src/test/java/com/example/testqwencli/SddArtifactTests.java` - расширить структурную проверку.

## Public Contracts

Публичные HTTP-контракты приложения не меняются.

## Data Model

Модель данных приложения не меняется.

## ADR

ADR не требуется: расширение фреймворка следует уже принятому решению `ADR-0001` о хранении архитектурных решений и не меняет архитектуру приложения.

## Technical Approach

1. Создать SDD-спеку `0002-diff-driven-spec-synthesis`.
2. Добавить шаблоны intake/diff/impact/source и contract diff.
3. Добавить Qwen commands для трех новых фаз.
4. Добавить skills и agent roles.
5. Обновить governance docs и корневые инструкции.
6. Расширить `SddArtifactTests`.
7. Запустить `mvn test` и записать результат в `work-log.md`.

## Verification Commands

```bash
mvn test
```

## Risks

- Риск: шаблоны станут слишком тяжелыми для малых задач. Смягчение: `diff-driven mode` используется только при analyst MR/documentation diff.
- Риск: без автоматического OpenAPI/DBML parser часть анализа остается ручной. Смягчение: parser оставить follow-up после стабилизации формата.

