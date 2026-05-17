# Source Context

Этот файл содержит минимальный контекст, извлеченный из измененных документов и связанных источников. Не копируйте сюда всю документацию проекта.

## Business requirements

| Source | Summary | Used for |
|---|---|---|
| <path/link> | <summary> | REQ-? |

## Changed documentation snippets

| Source | Context summary | Requirement candidates |
|---|---|---|
| `<path>#<section>` | <summary, not full copy> | REQ-? |

## OpenAPI context

| Source | Summary | Requirement candidates |
|---|---|---|
| `<openapi.yaml#/paths/...>` | <summary> | REQ-? |

## DBML context

| Source | Summary | Requirement candidates |
|---|---|---|
| `<schema.dbml:table>` | <summary> | REQ-? |

## Indirect context

| Source | Why included | Requirement candidates |
|---|---|---|
| `<path>` | <reason> | REQ-? |

## Source context gate

- [ ] Каждый фрагмент связан с изменением или косвенным impact.
- [ ] Нет больших вставок документации без необходимости.
- [ ] Источники указаны достаточно точно для review.

