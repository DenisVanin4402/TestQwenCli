# Intake

## Mode

spec-first / diff-driven

## Business request

<Краткое бизнес-требование или ссылка на источник.>

## Analyst change source

- Merge request: <URL или ID>
- Base branch: `<base branch>`
- Analyst branch: `<branch>`
- Diff command: `git diff <base>...<branch>`

## Input artifacts

| Artifact | Path / link | Purpose |
|---|---|---|
| Business requirements | <path/link> | Why |
| Markdown docs | <path/link> | Business/process rules |
| OpenAPI | <path/link> | API contracts |
| DBML | <path/link> | Data model |

## Constraints

- <Ограничение>

## Intake gate

- [ ] Base branch известна.
- [ ] Diff воспроизводим.
- [ ] Измененные файлы классифицированы.
- [ ] Scope не включает несвязанные изменения.

