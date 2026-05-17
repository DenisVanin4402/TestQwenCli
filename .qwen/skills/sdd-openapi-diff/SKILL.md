---
name: sdd-openapi-diff
description: Анализировать OpenAPI changes и выделять изменения endpoints, schemas, responses и security.
---

# SDD OpenAPI Diff

## Inputs

- Base OpenAPI source.
- Changed OpenAPI source.
- Git diff или MR diff.

## Procedure

1. Выдели changed endpoints.
2. Выдели changed request/response schemas.
3. Выдели changed error responses.
4. Выдели changed security schemes/scopes.
5. Свяжи каждое изменение с requirement candidate.
6. Не делай выводов о бизнес-логике без source context.

## Output

- `contracts/openapi-diff.md`.
- Contract questions.
- Requirement candidates.

