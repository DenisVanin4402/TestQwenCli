# Classification rules

Файл описывает эвристики классификации документов master-spec folder.

## kind

| `kind` | Признаки |
|---|---|
| `overview` | README, index, summary, context, overview, intro. |
| `workflow` | flow, process, scenario, user-journey, activity, sequence. |
| `api` | openapi, swagger, rest, grpc service, endpoint tables, request/response. |
| `integration` | external system, kafka, queue, topic, webhook, client, adapter, upstream/downstream. |
| `data` | entity, model, table, schema, migration, DDL, storage, database. |
| `state-machine` | FSM, state, transition, lifecycle, status matrix. |
| `security` | auth, roles, permissions, TLS, mTLS, masking, secrets. |
| `observability` | logging, metrics, tracing, audit, health, alerts. |
| `nfr` | performance, reliability, SLA, limits, capacity, timeout, retry. |
| `runbook` | operations, support, incident, runbook, rollback, deploy. |
| `diagram` | plantuml, drawio, mermaid, sequence diagram, C4, architecture diagram. |
| `schema` | JSON Schema, Avro, Protobuf, XSD, WSDL, OpenAPI components. |
| `other` | Не удалось уверенно классифицировать. |

## format

| `format` | Расширения и признаки |
|---|---|
| `markdown` | `.md`, `.markdown`. |
| `openapi` | YAML/JSON с `openapi:` или `swagger:`. |
| `json` | `.json`, кроме OpenAPI/JSON Schema при явном распознавании как schema. |
| `yaml` | `.yaml`, `.yml`, кроме OpenAPI. |
| `plantuml` | `.puml`, `.plantuml`. |
| `drawio` | `.drawio`, `.dio`. |
| `pdf` | `.pdf`. |
| `image` | `.png`, `.jpg`, `.jpeg`, `.gif`, `.svg`, `.webp`. |
| `binary` | Файл не читается как текст или явно бинарный. |
| `other` | Прочее. |

## read_priority

`high` ставится документам, без которых агент почти всегда ошибется:

- overview/index;
- основной workflow;
- публичный API;
- ключевая интеграция;
- модель данных или state-machine с доменными инвариантами;
- security/NFR документы, если сервис регулируемый или критичный.

`medium` ставится документам, которые нужны для конкретной зоны изменения.

`low` ставится вспомогательным, историческим, частным или редко используемым материалам.

## tags/entities/integrations/endpoints/events

- Извлекай теги из названий папок, заголовков и повторяющихся доменных слов.
- `entities` — имена доменных сущностей и таблиц.
- `integrations` — внешние системы, topics, queues, APIs, upstream/downstream.
- `endpoints` — REST `METHOD /path`, gRPC `Service.Method`, команды и webhook endpoints.
- `events` — доменные события и integration events.

## Бинарные и нечитаемые файлы

- Не пропускай бинарный файл только потому, что его нельзя прочитать.
- Заполни `format`, `size_bytes`, `hash`, `title`, `kind` по имени и соседним файлам.
- Если роль файла непонятна, `kind=other`, `status=unknown`, `read_priority=low`.
- Если бинарный файл выглядит важным, но рядом нет текстового описания, добавь warning в `stale-files.md`.
