# План 1. Master specification как папка документов

## 1. Цель

Заменить текущую модель `openspec/specs/<service>/<service>.md` на модель, где master specification сервиса - это папка:

```text
openspec/<service-name>/
```

Внутри папки находится произвольная структура документов, которую команда уже использует в проекте: `workflow/`, `integrations/`, `api/`, `data/`, `security/`, диаграммы, схемы, таблицы, OpenAPI, PlantUML, Draw.io и другие артефакты. Framework не должен требовать переписывать эти материалы в один большой Markdown-файл.

Основная задача первого шага - сделать такую папку удобной для AI-агентов: агент должен быстро понять, какие документы есть, за что они отвечают, какие файлы читать для конкретного изменения и какие файлы являются опорными для проверки связности.

## 2. Ключевые решения

1. Старый путь `openspec/specs/<service>/<service>.md` больше не является каноническим.
2. Новый master-spec root: `openspec/<service-name>/`.
3. `openspec/changes/` остается системной директорией для `change.md`, `design.md`, `tasks.md` и архива.
4. Имена сервисов `changes`, `archive`, `_sdd`, `_system` резервируются и не могут использоваться как `<service-name>`.
5. Master specification может содержать файлы любых типов. Markdown читается как основной текстовый формат, остальные файлы учитываются в manifest и анализируются по доступности содержимого.
6. После копирования документов пользователь запускает отдельную инициализацию master-spec папки.
7. Инициализация создает навигационный слой в `_sdd/`, но не переписывает пользовательские документы.
8. Обратная совместимость с `service-spec.md` не требуется.

## 3. Новый layout

```text
openspec/
  <service-name>/
    _sdd/
      manifest.yaml
      navigation.md
      coverage.md
      stale-files.md
    workflow/
      ...
    integrations/
      ...
    api/
      ...
    data/
      ...
    ...
  changes/
    <change-name>/
      change.md
      design.md
      tasks.md
      .research/
      .research-notes.md
    archive/
      YYYY-MM-DD-<change-name>/
```

`_sdd/` - служебная директория SDD framework внутри master-spec root. Она коммитится вместе с документацией, потому что является частью навигации по master specification.

## 4. Manifest

### 4.1. Назначение

`openspec/<service-name>/_sdd/manifest.yaml` - машинно-читаемый индекс master-spec папки. Он нужен, чтобы агент не перечитывал всю документацию каждый раз и мог выбирать релевантные файлы по домену, сущностям, интеграциям, процессам и типам требований.

Manifest не является заменой документации. Он только описывает, где лежит знание и как оно связано.

### 4.2. Минимальная структура

```yaml
schema_version: 1
service: <service-name>
root: openspec/<service-name>
generated_at: "YYYY-MM-DDTHH:mm:ssZ"
generator: openspec-init-master-spec

summary:
  purpose: "<краткое назначение сервиса>"
  bounded_context: "<границы сервиса>"
  main_domains:
    - "<домен>"
  main_flows:
    - "<процесс>"
  main_integrations:
    - "<интеграция>"

files:
  - path: openspec/<service-name>/workflow/order-flow.md
    kind: workflow
    format: markdown
    title: "Order flow"
    status: active
    hash: "<content-hash>"
    size_bytes: 12345
    last_seen_at: "YYYY-MM-DDTHH:mm:ssZ"
    tags:
      - workflow
      - order
    entities:
      - Order
    integrations:
      - external-billing
    endpoints:
      - "POST /orders"
    events:
      - order.created
    depends_on:
      - openspec/<service-name>/integrations/billing.md
    related_files:
      - openspec/<service-name>/data/order.md
    read_priority: high
    agent_notes: "<когда читать этот файл>"

reserved_paths:
  - openspec/<service-name>/_sdd/
```

### 4.3. Поля файла

| Поле | Обязательность | Назначение |
|---|---|---|
| `path` | Да | Путь от корня репозитория |
| `kind` | Да | `overview`, `workflow`, `api`, `integration`, `data`, `state-machine`, `security`, `observability`, `nfr`, `runbook`, `diagram`, `schema`, `other` |
| `format` | Да | `markdown`, `openapi`, `json`, `yaml`, `plantuml`, `drawio`, `pdf`, `image`, `binary`, `other` |
| `title` | Да | Человеческое название документа |
| `status` | Да | `active`, `deprecated`, `draft`, `unknown` |
| `hash` | Да | Хэш содержимого для определения устаревшего manifest |
| `tags` | Да | Доменные и технические метки |
| `entities` | Нет | Доменные сущности |
| `integrations` | Нет | Внешние системы, topics, queues, APIs |
| `endpoints` | Нет | API endpoints, gRPC methods, commands |
| `events` | Нет | Доменные и интеграционные события |
| `depends_on` | Нет | Файлы, без которых документ нельзя корректно читать |
| `related_files` | Нет | Файлы, которые стоит читать рядом |
| `read_priority` | Да | `high`, `medium`, `low` |
| `agent_notes` | Нет | Короткая подсказка агенту |

### 4.4. Human-readable навигация

`openspec/<service-name>/_sdd/navigation.md` - краткая карта для человека и LLM:

- назначение сервиса;
- как устроена папка;
- какие документы читать первыми;
- какие документы читать для API, интеграций, данных, workflow, ошибок, безопасности, мониторинга;
- карта связей между основными файлами;
- предупреждения о deprecated/draft документах;
- список файлов, которые нельзя игнорировать при change.

### 4.5. Coverage

`openspec/<service-name>/_sdd/coverage.md` показывает, какие области покрыты документацией:

| Область | Покрытие | Основные файлы | Пробелы |
|---|---|---|---|
| Workflow | Полное / частичное / нет | ... | ... |
| API | ... | ... | ... |
| Интеграции | ... | ... | ... |
| Модели данных | ... | ... | ... |
| Ошибки | ... | ... | ... |
| Безопасность | ... | ... | ... |
| НФТ | ... | ... | ... |

Этот файл нужен, чтобы агент честно видел пробелы и не выдавал неполную документацию за исчерпывающую.

### 4.6. Stale files

`openspec/<service-name>/_sdd/stale-files.md` создается при refresh, если manifest не совпадает с текущим деревом:

- добавленные файлы без описания в manifest;
- удаленные файлы, еще присутствующие в manifest;
- измененные файлы, по которым нужно обновить summary/tags/relations;
- возможные конфликты классификации.

## 5. Инициализация master-spec папки

### 5.1. Новый skill

Добавить skill:

```text
openspec-init-master-spec
```

Назначение: создать или обновить `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`, `_sdd/stale-files.md` для `openspec/<service-name>/`.

Триггеры:

- "инициализируй master spec";
- "подключи папку как мастер спецификацию";
- "собери manifest для openspec/<service>";
- "обнови navigation/manifest";
- "refresh master spec".

### 5.2. Input

Обязательные параметры:

- `service` - имя сервиса;
- `root` - по умолчанию `openspec/<service>`.

Опциональные параметры:

- `thoroughness=quick|medium|full`;
- `include_patterns`;
- `exclude_patterns`;
- `update_mode=init|refresh`;
- `manifest_only=true|false`.

### 5.3. Алгоритм init

1. Проверить, что `openspec/<service-name>/` существует.
2. Проверить, что `<service-name>` не входит в reserved names.
3. Получить список файлов через `git ls-files`, а для еще не закоммиченных документов - через обычный filesystem scan.
4. Исключить `_sdd/`, `.git/`, временные файлы, build output, editor swap files.
5. Классифицировать файлы по расширению и пути.
6. Для Markdown/YAML/JSON/OpenAPI/PlantUML прочитать содержимое.
7. Для бинарных файлов зафиксировать путь, размер, расширение и роль по имени/соседним файлам.
8. Сформировать первичный inventory.
9. Для больших папок запустить read-only субагентов по ролям:
   - `doc-inventory` - структура и классификация;
   - `business-map` - домены, процессы, инварианты;
   - `contract-map` - API, события, интеграции, схемы;
   - `data-map` - модели данных, состояния, миграции;
   - `quality-map` - ошибки, безопасность, мониторинг, НФТ;
   - `cross-link-map` - связи, дубли, противоречия, пробелы.
10. Сагрегировать результаты в manifest.
11. Сгенерировать `navigation.md`.
12. Сгенерировать `coverage.md`.
13. Сгенерировать `stale-files.md` только если есть проблемы.
14. Провести self-review:
    - все файлы из master root либо описаны, либо явно исключены;
    - нет битых `related_files`;
    - `read_priority=high` есть у ключевых overview/workflow/API/integration/data документов;
    - coverage не скрывает пробелы.

### 5.4. Алгоритм refresh

1. Прочитать текущий manifest.
2. Пересчитать список файлов и content hash.
3. Разделить файлы на unchanged, added, modified, deleted.
4. Не перечитывать unchanged файлы без необходимости.
5. Для added/modified файлов обновить classification, tags, entities, relations.
6. Для deleted файлов удалить записи из manifest и проверить ссылки на них.
7. Обновить `navigation.md` и `coverage.md`.
8. Записать `stale-files.md`, если остались сомнения или нарушены связи.
9. В финальном выводе показать:
   - сколько файлов добавлено;
   - сколько изменено;
   - сколько удалено;
   - какие документы требуют ручной проверки.

## 6. Изменения в SDD_DETAILS.md

Обновить разделы:

1. "Общая модель":
   - source of truth - folder-based master specification;
   - один Markdown-файл больше не создается.
2. "Структура артефактов":
   - новый layout `openspec/<service>/`;
   - `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`, `_sdd/stale-files.md`.
3. "Жизненный цикл":
   - заменить `new-spec` на `init-master-spec`;
   - `propose` работает от folder-based master spec;
   - `apply-change` перестает быть обязательным финальным этапом для diff-based сценария из плана 2.
4. "Роли скиллов":
   - добавить `openspec-init-master-spec`;
   - переписать `openspec-new-spec` или удалить из маршрутизации;
   - обновить `openspec-propose`, `openspec-design`, `openspec-implement`, `openspec-apply-change`.
5. "Аналитические документы":
   - заменить `service-spec.md` на "master-spec folder";
   - описать роль manifest.
6. "Что сохранить при трансформации":
   - сохранить разделение source of truth, change, design, tasks;
   - сохранить structured research;
   - заменить single-file merge на folder-aware navigation/checks.

## 7. Изменения в skills

### 7.1. `openspec-teach`

Обновить:

- структуру `openspec/`;
- жизненный цикл;
- mapping запросов;
- правила для агента;
- запрет считать `openspec/specs/<service>/<service>.md` обязательным.

### 7.2. `openspec-new-spec`

Так как обратная совместимость не требуется, есть два варианта.

Предпочтительный вариант: заменить skill на `openspec-init-master-spec`.

Альтернативный вариант: оставить имя `openspec-new-spec`, но изменить смысл на инициализацию папки. Этот вариант хуже, потому что имя продолжит обещать генерацию новой полной спецификации.

Рекомендуется первый вариант:

- удалить или депрекейтнуть `openspec-new-spec`;
- добавить `openspec-init-master-spec`;
- обновить README и teach, чтобы агенты не вызывали старый skill.

### 7.3. `openspec-propose`

Обновить вход:

- вместо `Целевая спецификация: openspec/specs/<service>/<service>.md`;
- использовать `Мастер-спецификация: openspec/<service>/`.

Новый порядок чтения:

1. Прочитать `_sdd/navigation.md`.
2. Прочитать `_sdd/manifest.yaml`.
3. Выбрать релевантные документы по tags/entities/integrations/endpoints/events.
4. Прочитать только релевантные документы и `read_priority=high`.
5. Если manifest отсутствует или stale - предложить `openspec-init-master-spec`.

### 7.4. `openspec-design`

Обновить:

- читать master-spec root из `change.md`;
- использовать manifest для выбора документации;
- при необходимости ссылаться на документы master folder так же, как на кодовые сущности;
- не требовать single-file target spec.

### 7.5. `openspec-implement`

Минимальные изменения:

- не предполагать, что после реализации обязательно будет `apply-change`;
- понимать в `change.md` поле `Spec update mode`;
- после выполнения tasks предлагать либо `openspec-verify-master-spec`, либо `openspec-apply-change` только при явном manual-apply режиме.

### 7.6. `openspec-apply-change`

Для первого шага достаточно подготовить изменение интерфейса:

- single-file merge больше не является основной моделью;
- для folder-based docs прямой merge из `change.md` в произвольные документы небезопасен;
- в плане 2 этот skill будет заменен на проверку, что изменения master spec уже присутствуют в ветке аналитика или применены явно.

## 8. Изменения в templates

### 8.1. `change.md`

Обновить шапку:

```md
> **Мастер-спецификация**: openspec/<service-name>/
>
> **Manifest**: openspec/<service-name>/_sdd/manifest.yaml
>
> **Spec update mode**: manual-change | branch-diff
```

Убрать обязательное поле:

```md
> **Целевая спецификация**: openspec/specs/<service>/<service>.md
```

Добавить раздел:

```md
## 0. Источники master specification

| Роль | Файл | Почему использован |
|---|---|---|
```

Этот раздел нужен, чтобы developer видел, какие документы повлияли на `change.md`.

### 8.2. `design.md`

Добавить блок:

```md
## Источники требований

### Master specification documents

| Файл | Что взято |
|---|---|
```

### 8.3. `tasks.md`

Добавить проверку:

```md
- [ ] Проверено, что реализация соответствует документам master specification, указанным в change.md.
```

## 9. Правила чтения master-spec папки агентом

1. Никогда не начинать с рекурсивного чтения всей папки, если manifest существует.
2. Сначала читать `_sdd/navigation.md`.
3. Затем читать `_sdd/manifest.yaml`.
4. Для конкретного change выбирать файлы по:
   - измененным доменным сущностям;
   - endpoints;
   - integrations;
   - events;
   - tags;
   - `related_files`;
   - `depends_on`;
   - `read_priority=high`.
5. Если manifest не содержит измененный файл - считать manifest устаревшим.
6. Если `stale-files.md` не пустой - предупреждать пользователя перед генерацией `change.md`, `design.md` или `tasks.md`.
7. Если документация противоречива - фиксировать open question, а не выбирать удобную версию молча.

## 10. Проверки качества

### 10.1. Manifest checks

- Все файлы master root представлены в manifest или явно исключены.
- Все `related_files` существуют.
- Все `depends_on` существуют.
- Для каждого `read_priority=high` есть объяснение в `agent_notes`.
- Все changed/added/deleted файлы при refresh отражены в `stale-files.md` или manifest.

### 10.2. Navigation checks

- Есть "что читать первым".
- Есть карта по областям: workflow, API, integrations, data, errors, security, NFR.
- Есть предупреждения о deprecated/draft документах.
- Есть список пробелов coverage.

### 10.3. Agent checks

- `openspec-propose` не падает из-за отсутствия `service-spec.md`.
- `openspec-design` может работать только по `change.md` и manifest.
- `openspec-implement` не ожидает обязательный финальный merge в spec file.

## 11. Риски

| Риск | Что делать |
|---|---|
| Manifest устаревает | Ввести refresh, hash и `stale-files.md` |
| Папка слишком большая | Использовать manifest, read priority и субагентов |
| В документах есть противоречия | Фиксировать в coverage/open questions |
| Бинарные файлы не читаются агентом | Учитывать как артефакт, но требовать текстовую сводку рядом |
| `openspec/changes` конфликтует с именем сервиса | Зарезервировать системные имена |
| Пользователь меняет документы без refresh | Предупреждать при hash mismatch |

## 12. Критерии готовности шага 1

Шаг 1 считается реализованным, если:

1. В SDD документации описан новый folder-based master-spec layout.
2. Есть skill или инструкции `openspec-init-master-spec`.
3. Для `openspec/<service>/` создаются `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`.
4. `openspec-propose` и `openspec-design` больше не требуют `openspec/specs/<service>/<service>.md`.
5. `change.md` указывает master-spec root, manifest и spec update mode.
6. Агент может по manifest выбрать релевантные документы без чтения всей папки.
7. README/teach больше не направляют пользователя к созданию одного большого `service-spec.md`.
