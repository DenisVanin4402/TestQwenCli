# Детализация SDD/OpenSpec framework

Документ фиксирует актуальную модель SDD/OpenSpec workflow после перехода на folder-based master specification. Framework работает через Markdown-шаблоны, reference-файлы и прямую работу с файловой системой; OpenSpec CLI не требуется.

## 1. Общая модель

Source of truth сервиса — master specification folder:

```text
openspec/<service-name>/
```

Это папка с документацией команды: `workflow/`, `integrations/`, `api/`, `data/`, `security/`, диаграммами, схемами, OpenAPI, PlantUML, Draw.io и другими артефактами. Framework больше не требует создавать один большой Markdown-файл для всей спецификации сервиса.

Навигационный слой для AI-агентов находится внутри master-spec root:

```text
openspec/<service-name>/_sdd/
  manifest.yaml
  navigation.md
  coverage.md
  stale-files.md
```

Ключевые слои:

- Аналитический слой: master-spec folder, `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`, `change.md`.
- Исследовательский слой: `.research/*.yaml`, `.research/_aggregate.yaml`, `.research-notes.md`.
- Инженерный слой: `design.md`, `tasks.md`, код проекта, сборка/тесты/линтер.
- Управляющий слой: статусы change, ручное согласование через PR, архив завершенных изменений.

## 2. Структура артефактов

Канонический layout:

```text
openspec/
  <service-name>/
    _sdd/
      manifest.yaml
      navigation.md
      coverage.md
      stale-files.md
    workflow/
    integrations/
    api/
    data/
    security/
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

Назначение артефактов:

| Артефакт | Роль | Кто ведет | Что содержит |
|---|---|---|---|
| `openspec/<service>/` | Master specification сервиса | Команда / аналитик | Набор документов, контрактов, диаграмм и схем |
| `openspec/<service>/_sdd/manifest.yaml` | Машинный индекс master spec | `openspec-init-master-spec` | Файлы, типы, hash, tags, entities, integrations, endpoints, relations |
| `openspec/<service>/_sdd/navigation.md` | Карта чтения | `openspec-init-master-spec` | Что читать первым, карта по областям, связи, предупреждения |
| `openspec/<service>/_sdd/coverage.md` | Матрица покрытия | `openspec-init-master-spec` | Что документировано полностью, частично или отсутствует |
| `openspec/<service>/_sdd/stale-files.md` | Диагностика stale manifest | `openspec-init-master-spec` | Added/modified/deleted files, конфликты классификации, ручные проверки |
| `openspec/changes/<name>/change.md` | Предложение изменения | `openspec-propose` | Зачем и что меняется, источники master spec, spec update mode |
| `openspec/changes/<name>/design.md` | Технический проект | `openspec-design` | Как реализовать изменение в кодовой базе |
| `openspec/changes/<name>/tasks.md` | Исполняемый план | `openspec-design` / `openspec-implement` | Мелкие задачи-чекбоксы и чеклист верификации |
| `.research/*.yaml` | Машинные находки по ролям | `openspec-explore` или lead-skill | Структурированный read-only анализ кода |
| `.research/_aggregate.yaml` | Сводный агрегат | Lead-skill | Дедуплицированные коллекции по ролям |
| `.research-notes.md` | Человеко-читаемая сводка | Lead-skill | Статистика, gaps, ссылки на YAML |
| `archive/YYYY-MM-DD-<name>/` | История завершенных change | `openspec-archive-change` | Вся директория change после завершения |

Имена сервисов `changes`, `archive`, `_sdd`, `_system` зарезервированы.

## 3. Manifest и правила чтения master-spec folder

`manifest.yaml` нужен, чтобы агент выбирал релевантные документы без рекурсивного чтения всей папки.

Минимальная структура:

```yaml
schema_version: 1
service: <service-name>
root: openspec/<service-name>
generated_at: "YYYY-MM-DDTHH:mm:ssZ"
generator: openspec-init-master-spec
summary:
  purpose: "<назначение>"
  bounded_context: "<границы>"
  main_domains: []
  main_flows: []
  main_integrations: []
files:
  - path: openspec/<service-name>/<path>
    kind: workflow
    format: markdown
    title: "<название>"
    status: active
    hash: "<content-hash>"
    size_bytes: 0
    last_seen_at: "YYYY-MM-DDTHH:mm:ssZ"
    tags: []
    entities: []
    integrations: []
    endpoints: []
    events: []
    depends_on: []
    related_files: []
    read_priority: high
    agent_notes: "<когда читать>"
reserved_paths:
  - openspec/<service-name>/_sdd/
```

Правила чтения:

1. Если manifest существует, не начинать с рекурсивного чтения всей master-spec папки.
2. Сначала читать `_sdd/navigation.md`.
3. Затем читать `_sdd/manifest.yaml`.
4. Для конкретного change выбирать файлы по `tags`, `entities`, `integrations`, `endpoints`, `events`, `related_files`, `depends_on`, `read_priority=high`.
5. Если manifest не содержит измененный файл, считать manifest устаревшим.
6. Если `stale-files.md` непустой, предупреждать пользователя перед созданием `change.md`, `design.md` или `tasks.md`.
7. Если документация противоречива, фиксировать open question, а не выбирать удобную версию молча.

## 4. Жизненный цикл

Базовая цепочка:

```text
teach -> init-master-spec -> propose -> review/approve -> design? -> implement -> verify/apply? -> archive-change
                       \                                      /
                        -------- explore (read-only) --------
```

Этапы:

1. `openspec-teach` объясняет workflow и помогает выбрать следующий skill.
2. Пользователь копирует существующие документы сервиса в `openspec/<service>/`.
3. `openspec-init-master-spec` создает или обновляет `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`, `_sdd/stale-files.md`.
4. `openspec-propose` создает `change.md`, работая от master-spec folder и manifest.
5. Аналитик согласует change через PR и вручную переводит статус в `Согласовано`.
6. `openspec-design` опционально создает `design.md` и `tasks.md` для сложных изменений.
7. `openspec-implement` выполняет задачи из `tasks.md`, меняет статус change на `В реализации`, запускает обязательную верификацию.
8. Завершение зависит от `Spec update mode`:
   - `branch-diff` — master-spec документы уже изменены в ветке аналитика или в той же ветке; нужен verify folder-based docs.
   - `manual-change` — пользователь явно применяет изменения к master-spec documents; `openspec-apply-change` может использоваться только как ручной/осторожный интерфейс, не как автоматический merge в один файл.
9. `openspec-archive-change` переносит директорию change в архив.

`openspec-explore` остается standalone structured research и prompt-library для `openspec-propose`. Для размытого обсуждения он не используется.

## 5. Статусная модель

Статусы `change.md`:

| Статус | Кто ставит | Когда |
|---|---|---|
| `На согласовании` | `openspec-propose` | Change создан и отправляется на ревью |
| `Согласовано` | Аналитик вручную | PR с `change.md` вмержен, change одобрен |
| `В реализации` | `openspec-implement` | Начата разработка по `tasks.md` |
| `Реализовано` | Пользователь / verify / apply | Код и master-spec обновлены выбранным mode |
| `Архивировано` | `openspec-archive-change` | Change перенесен в архив |

Обратные переходы выполняются только вручную. Статусы `design.md` и `tasks.md` являются отдельным слоем реализации и ведутся разработчиком вручную.

## 6. Роли skills

### `openspec-teach`

Учебник и маршрутизатор. Объясняет folder-based layout, статусы, mapping пользовательских запросов на skills и следующий логичный шаг.

Важные правила:

- Не использовать для реальной работы, если задача однозначно относится к конкретному skill.
- Не считать старый single-file layout обязательным или каноническим.
- Если `openspec/<service>/` есть, но `_sdd/manifest.yaml` отсутствует, следующий шаг — `openspec-init-master-spec`.

### `openspec-init-master-spec`

Создает или обновляет `_sdd/` внутри `openspec/<service>/`.

Процесс:

1. Проверяет root и reserved names.
2. Собирает inventory через git и filesystem scan.
3. Исключает `_sdd/`, `openspec/changes/`, временные файлы и build output.
4. Классифицирует документы по `kind`, `format`, tags, entities, integrations, endpoints, events.
5. Считает hash и размеры.
6. Генерирует `manifest.yaml`, `navigation.md`, `coverage.md`.
7. Генерирует `stale-files.md`, если есть добавленные/измененные/удаленные файлы, broken links или сомнения.
8. Выполняет self-review manifest/navigation/coverage.

### `openspec-explore`

Structured research кодовой базы. Работает read-only и сохраняет результаты в `.research/`.

`target=change` используется для зоны изменения. `target=spec` может использоваться для картирования сервиса, но результат не создает единый master Markdown; он помогает заполнить или проверить folder-based master spec и `_sdd` navigation.

### `openspec-new-spec`

Deprecated. Старый skill создания одного полного документа больше не входит в активный workflow. Если пользователь просит "задокументировать сервис", агент должен предложить:

1. поместить существующую документацию или новые материалы в `openspec/<service>/`;
2. запустить `openspec-init-master-spec`;
3. при изменениях требований использовать `openspec-propose`.

### `openspec-propose`

Создает `openspec/changes/<name>/change.md`.

Новый порядок чтения:

1. Определить master-spec root `openspec/<service>/`.
2. Прочитать `_sdd/navigation.md`.
3. Прочитать `_sdd/manifest.yaml`.
4. Проверить `stale-files.md`.
5. Выбрать документы по `tags`, `entities`, `integrations`, `endpoints`, `events`, `related_files`, `depends_on`, `read_priority`.
6. Прочитать только выбранные документы и high-priority документы.
7. Если manifest отсутствует или stale, предложить `openspec-init-master-spec`.

`change.md` содержит:

- `Мастер-спецификация`;
- `Manifest`;
- `Spec update mode: manual-change | branch-diff`;
- раздел `## 0. Источники master specification`.

### `openspec-design`

Создает `design.md` и `tasks.md` на основе согласованного `change.md`.

Особенности:

- Читает master-spec root и manifest из шапки `change.md`.
- Использует раздел `## 0. Источники master specification`.
- Может ссылаться на документы master-spec folder так же, как на кодовые сущности.
- Не требует single-file target spec.
- Structured research не запускается заново, если рядом есть актуальный `.research-notes.md`.

### `openspec-implement`

Реализует задачи из `tasks.md`.

Изменения в folder-based workflow:

- Не предполагает обязательный финальный `apply-change`.
- Читает `Spec update mode` из `change.md`.
- После выполнения tasks предлагает следующий шаг:
  - для `branch-diff` — verify master-spec documents;
  - для `manual-change` — явно применить изменения к нужным документам master spec или запустить `openspec-apply-change` только при подтвержденном manual-apply сценарии.
- В `tasks.md` добавляется проверка соответствия документам master specification, указанным в `change.md`.

### `openspec-apply-change`

Больше не является основным финальным этапом. Для folder-based docs автоматический merge произвольных документов небезопасен.

В шаге 1 интерфейс skill сохраняется только как осторожный manual-apply/verification gateway:

- читает `Spec update mode`;
- блокирует автоматический single-file merge для folder-based master spec;
- предлагает ручное обновление конкретных документов или будущий branch-diff verify;
- не требует мержить `change.md` в один файл.

### `openspec-archive-change`

Архивирует директорию change целиком в `openspec/changes/archive/YYYY-MM-DD-<name>/`.

## 7. Structured research

Research-контур отделяет чтение кода от генерации аналитического документа и не перегружает контекст lead-агента.

Роли для `target=change`:

| Роль | Что ищет |
|---|---|
| `feature-scope` | Текущая реализация в зоне изменения, поведение обработчиков, флоу клиента, модели, правила, валидации |
| `dependencies` | Callers/потребители, breaking risk, миграция, обратная совместимость, rollback |
| `cross-cutting` | Ошибки, хедеры, безопасность, логи, метрики, конфигурация |

Роли для разового картирования сервиса остаются: `api`, `business-logic`, `data`, `integrations`, `config`, `observability`, `security`, `nfr`. Их результат помогает обновлять folder-based master spec и `_sdd`, но не создает один файл.

Инварианты research:

- Промпты ролей из `research-roles.md` копируются дословно.
- Субагенты возвращают YAML с `summary`, `key_files`, доменными коллекциями и `gaps`.
- Все промежуточные результаты пишутся на диск до агрегации.
- `.research-notes.md` используется как восстановление после compact и как источник вопросов.

## 8. Аналитические документы и их границы

### Master-spec folder

Master specification — набор документов системного аналитика и команды. Главный критерий: разработчик и агент должны понять поведение сервиса по папке документов и `_sdd` navigation/manifest без доступа к текущему коду как единственному источнику правды.

Разрешены:

- Markdown-документы;
- OpenAPI, Protobuf, Avro, JSON Schema, GraphQL SDL, SQL DDL/migrations, XSD/WSDL;
- диаграммы поведения и архитектуры;
- таблицы моделей данных, API, интеграций, конфигурации;
- критерии приемки в формате КОГДА/ТОГДА.

Запрещено выдавать неполную папку за полную. Пробелы фиксируются в `coverage.md`, stale-состояние — в `stale-files.md`.

### `change.md`

Change — аналитический инкремент к master specification. Он описывает ЗАЧЕМ и ЧТО меняется, но не КАК реализовать в коде.

Обязательные folder-based поля:

```md
> **Мастер-спецификация**: openspec/<service-name>/
>
> **Manifest**: openspec/<service-name>/_sdd/manifest.yaml
>
> **Spec update mode**: manual-change | branch-diff
```

Раздел `## 0. Источники master specification` фиксирует, какие документы повлияли на change.

### `design.md`

Design — инженерный документ. Он должен ссылаться на конкретные сущности кода и на документы master specification, если они являются источниками требований.

### `tasks.md`

Tasks — исполняемый план. Рабочие задачи находятся только в пронумерованных H2-секциях. Нечисловые секции служат справкой и не должны автоматически отмечаться implementation-skill.

## 9. Качество и cross-linking

Framework строит восстановимость через перекрестные ссылки:

- шаги клиентского флоу с изменением состояния ссылаются на FSM;
- обработчики с решающей логикой ссылаются на бизнес-правила и переходы FSM;
- модели данных с доменными типами ссылаются на глоссарий;
- конфигурация, влияющая на поведение, ссылается на правила, FSM или обработчики;
- критерии приемки покрывают FSM и бизнес-инварианты;
- manifest связывает документы через `depends_on` и `related_files`.

## 10. Проверки качества

### Manifest checks

- Все файлы master root представлены в manifest или явно исключены.
- Все `related_files` существуют.
- Все `depends_on` существуют.
- Для каждого `read_priority=high` есть `agent_notes`.
- Все added/modified/deleted файлы при refresh отражены в `stale-files.md` или manifest.

### Navigation checks

- Есть "что читать первым".
- Есть карта по областям: workflow, API, integrations, data, errors, security, NFR.
- Есть предупреждения о deprecated/draft документах.
- Есть список пробелов coverage.

### Coverage checks

- Workflow, API, интеграции, модели данных, ошибки, безопасность и НФТ имеют честную оценку покрытия.
- Пробелы не скрываются и не заменяются предположениями.
- Бинарные артефакты учтены и, если важны, имеют текстовую сводку рядом либо warning.

### Agent checks

- `openspec-propose` не падает из-за отсутствия single-file spec.
- `openspec-design` может работать по `change.md`, manifest и sources.
- `openspec-implement` не ожидает обязательный финальный merge в spec file.

## 11. Риски

| Риск | Что делать |
|---|---|
| Manifest устаревает | Использовать refresh, hash и `stale-files.md` |
| Папка слишком большая | Использовать manifest, read priority и субагентов |
| В документах есть противоречия | Фиксировать в coverage/open questions |
| Бинарные файлы не читаются агентом | Учитывать как артефакт, требовать текстовую сводку рядом |
| `openspec/changes` конфликтует с именем сервиса | Reserved names: `changes`, `archive`, `_sdd`, `_system` |
| Пользователь меняет документы без refresh | Предупреждать при hash mismatch |
| Автоматический merge произвольных docs небезопасен | Использовать branch-diff verify или явный manual-change |

## 12. Что сохранить при трансформации

Сохраняется:

- разделение source of truth, delta-change, design и tasks;
- двухэтапное интервью: сначала смысл, потом детали после исследования;
- read-only structured research с ролями и YAML-контрактом;
- персистентность research на диске для восстановления после compact;
- guardrails против кода в аналитических документах;
- фиксированный скелет обработчика по триггеру;
- бизнес-правила с формулировкой и пошаговым псевдокодом;
- cross-linking между флоу, обработчиками, правилами, FSM, инвариантами и acceptance;
- строгий executor по `tasks.md`;
- обязательная верификация перед объявлением реализации завершенной.

Меняется:

- single-file source of truth заменен на folder-based master spec;
- single-file merge заменен на folder-aware navigation, manifest checks, coverage checks и branch-diff/manual-change режимы.

## 13. Нормализованная карта этапов

1. Discovery: понять запрос и сервис.
2. Baseline: создать или обновить `openspec/<service>/_sdd/`.
3. Proposal: оформить change как аналитический delta-документ.
4. Approval: PR, ревью, ручное согласование.
5. Engineering design: создать design/tasks для сложных изменений.
6. Implementation: выполнить tasks с прогрессом и верификацией.
7. Spec verification/update: проверить или явно применить изменения master-spec folder.
8. Archive: зафиксировать историю и убрать active change.
