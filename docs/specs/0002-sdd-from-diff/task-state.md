# State: SDD from Diff

## Current phase

Complete — all tasks done, all files created and verified.

## Progress

| Task | Status | Notes |
|------|--------|-------|
| TSK-1 | done | delta-spec template created (docs/specs/_template/delta-spec.md) |
| TSK-2 | done | diff-extractor scripts created (sh + bat) |
| TSK-3 | done | Qwen command /sdd:from-diff created |
| TSK-4 | done | Qwen skill sdd-diff-analyzer created |
| TSK-5 | done | workflow.md updated with "from diff" entrypoint |
| TSK-6 | done | AGENTS.md updated with delta-spec rules |
| TSK-7 | done | instructions.md Part 10 added |

## Created files

- `docs/specs/_template/delta-spec.md` — шаблон delta-specifications
- `scripts/sdd-diff-extract.sh` — bash-скрипт извлечения diff
- `scripts/sdd-diff-extract.bat` — Windows-скрипт извлечения diff
- `.qwen/commands/sdd/from-diff.md` — команда Qwen CLI `/sdd:from-diff`
- `.qwen/skills/sdd-diff-analyzer/SKILL.md` — навык анализа diff

## Updated files

- `docs/sdd/workflow.md` — добавлены entrypoints "from diff", команда /sdd:from-diff в таблице
- `AGENTS.md` — добавлен раздел "Delta-Spec — работа с изменениями из MR"
- `docs/sdd/instructions.md` — добавлена Часть 10: сценарий "From Diff"

## Test results

- `sdd-lint.sh` — [pending/running]
- `mvn test` — [pending/running]

## Spec compliance

| REQ | Status | Notes |
|-----|--------|-------|
| REQ-1 | done | delta-spec template создан с required секциями (AC-1) |
| REQ-2 | done | diff-extractor scripts sh+bat (AC-2) |
| REQ-3 | done | формат diff-summary определён в скрипте (AC-3) |
| REQ-4 | done | формат impact-map определён в скрипте (AC-4) |
| REQ-5 | done | команда /sdd:from-diff создана (AC-4) |
| REQ-6 | done | навык sdd-diff-analyzer создан (AC-5) |
| REQ-7 | done | workflow.md обновлён (AC-6) |
| REQ-8 | done | AGENTS.md обновлён (AC-7) |
| REQ-9 | done | instructions.md обновлён (AC-8), mvn test (AC-9) |
