# Context selection через manifest

`change.md` из branch diff не должен быть механическим пересказом patch. Для понимания смысла изменений нужен контекст master-spec folder.

## 1. Порядок чтения manifest

Сначала читать manifest из analyst ref:

```bash
git show <analyst_ref>:openspec/<service>/_sdd/manifest.yaml
```

Если отсутствует, читать из base ref:

```bash
git show <base_ref>:openspec/<service>/_sdd/manifest.yaml
```

Если manifest отсутствует в обоих refs:

- остановиться и предложить `openspec-init-master-spec`;
- либо продолжить только после явного подтверждения пользователя;
- confidence не может быть `Высокая`.

## 2. Changed file entry

Для каждого changed file ищи запись manifest по:

- `path`;
- для renamed — `old_path` и `path`;
- если manifest изменен в diff, приоритет у analyst version.

Если файл не найден в manifest, зафиксируй stale manifest warning.

## 3. Reasons

Контекст выбирается по причинам:

| Reason | Источник |
|---|---|
| `depends_on` | Поле `depends_on` changed file |
| `related_files` | Поле `related_files` changed file |
| `shared_entity` | Совпадение `entities` |
| `shared_integration` | Совпадение `integrations` |
| `shared_endpoint` | Совпадение `endpoints` |
| `shared_event` | Совпадение `events` |
| `high_priority` | `read_priority=high` |
| `binary_neighbor` | Текстовый файл рядом с binary artifact |
| `stale_manifest_check` | Проверка устаревшего manifest |

## 4. Include context modes

### `manifest`

Минимальный режим:

- direct `depends_on`;
- direct `related_files`;
- `read_priority=high`;
- текстовые соседи для binary files.

### `manifest+related`

Default:

- все из `manifest`;
- shared `entities`;
- shared `integrations`;
- shared `endpoints`;
- shared `events`.

### `full`

Расширенный режим:

- все из `manifest+related`;
- документы из тех же областей navigation;
- документы, нужные для проверки противоречий.

Даже в `full` не читай всю master-spec папку рекурсивно без причины.

## 5. Context files YAML

Формат:

```yaml
service: <service>
source_manifest_ref: <analyst_ref|base_ref>
manifest_path: openspec/<service>/_sdd/manifest.yaml
manifest_found: true
manifest_stale_warnings:
  - <warning>
files:
  - path: openspec/<service>/workflow/order-flow.md
    source_ref: <analyst_ref>
    changed: true
    reasons:
      - changed_file
    read_status: read
    binary: false
  - path: openspec/<service>/data/order.md
    source_ref: <analyst_ref>
    changed: false
    reasons:
      - shared_entity:Order
      - depends_on
    read_status: read
    binary: false
```

Если файл не удалось прочитать:

```yaml
read_status: missing_in_ref
warning: "Файл указан в manifest, но отсутствует в analyst_ref"
```

## 6. Binary files

Binary файл не игнорируется. Для него фиксируются:

- status;
- path;
- old_path, если есть;
- размер blob в base/analyst ref, если доступен;
- соседние Markdown/YAML/JSON/OpenAPI документы;
- open question, если текстового контекста недостаточно.

## 7. Stale manifest

Manifest считается потенциально stale, если:

- changed file отсутствует в `files`;
- `hash` не соответствует содержимому в analyst ref;
- `related_files` или `depends_on` указывают на отсутствующий путь;
- изменен `_sdd/manifest.yaml`, но navigation/coverage не изменены и это выглядит подозрительно;
- `stale-files.md` в analyst ref непустой.

При stale manifest confidence не выше `Средняя`.
