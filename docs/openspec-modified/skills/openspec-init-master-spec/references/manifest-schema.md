# Manifest schema

`_sdd/manifest.yaml` — машинно-читаемый индекс master-spec папки `openspec/<service-name>/`.

## Top-level fields

| Поле | Обязательность | Назначение |
|---|---|---|
| `schema_version` | Да | Версия схемы manifest. Для шага 1 — `1`. |
| `service` | Да | Имя сервиса в kebab-case. |
| `root` | Да | Путь к master-spec root от корня репозитория. |
| `generated_at` | Да | Время генерации в ISO-8601 UTC. |
| `generator` | Да | Имя генератора, обычно `openspec-init-master-spec`. |
| `summary` | Да | Краткая карта назначения, контекста, доменов, флоу и интеграций. |
| `files` | Да | Список описанных файлов master-spec root. |
| `reserved_paths` | Да | Системные пути, которые не являются пользовательской документацией. |

## File entry

| Поле | Обязательность | Назначение |
|---|---|---|
| `path` | Да | Путь от корня репозитория. |
| `kind` | Да | Семантический тип документа. |
| `format` | Да | Формат файла. |
| `title` | Да | Человеческое название документа. |
| `status` | Да | `active`, `deprecated`, `draft`, `unknown`. |
| `hash` | Да | Content hash для refresh. |
| `size_bytes` | Да | Размер файла. |
| `last_seen_at` | Да | Время последнего сканирования. |
| `tags` | Да | Доменные и технические метки. |
| `entities` | Нет | Доменные сущности. |
| `integrations` | Нет | Внешние системы, topics, queues, APIs. |
| `endpoints` | Нет | API endpoints, gRPC methods, commands. |
| `events` | Нет | Доменные и интеграционные события. |
| `depends_on` | Нет | Файлы, без которых документ нельзя корректно читать. |
| `related_files` | Нет | Файлы, которые стоит читать рядом. |
| `read_priority` | Да | `high`, `medium`, `low`. |
| `agent_notes` | Нет | Короткая подсказка агенту, когда читать файл. |

## Allowed values

`kind`: `overview`, `workflow`, `api`, `integration`, `data`, `state-machine`, `security`, `observability`, `nfr`, `runbook`, `diagram`, `schema`, `other`.

`format`: `markdown`, `openapi`, `json`, `yaml`, `plantuml`, `drawio`, `pdf`, `image`, `binary`, `other`.

`read_priority`: `high`, `medium`, `low`.

## Правила

- Все пути пишутся от корня репозитория с `/` как разделителем.
- `hash` считается по содержимому файла, не по имени.
- `_sdd/**` не включается в `files`, но указывается в `reserved_paths`.
- `openspec/changes/**` не включается в manifest сервиса.
- Для `read_priority=high` поле `agent_notes` обязательно по смыслу, даже если схема допускает отсутствие.
