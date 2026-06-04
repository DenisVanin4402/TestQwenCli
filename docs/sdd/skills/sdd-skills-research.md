# Исследование SDD/OpenSpec skills

Дата исследования: 2026-06-04.

Область исследования: только каталог `docs/sdd/skills`. Внешние источники, код приложения, другие документы проекта и интернет не использовались.

Проверено: 44 файла, включая `README.md`, 10 `SKILL.md`, шаблоны и reference-документы. Механическая проверка локальных Markdown-ссылок не выявила битых ссылок.

## 1. Краткий вывод

Фреймворк выглядит как зрелый SDD/OpenSpec workflow для работы AI-агента и системного аналитика: `teach -> init-master-spec -> propose/change-from-diff -> design -> implement -> apply/verify -> archive`. Основная архитектурная идея корректная: не делать один гигантский файл спецификации, а хранить master specification как папку документов с машинным навигационным слоем `_sdd/manifest.yaml`, `navigation.md`, `coverage.md`, `stale-files.md`.

Сильные стороны:

- жизненный цикл change явно разделяет требования, технический проект, реализацию и проверку;
- `change.md` удерживается в роли аналитического документа, а `design.md` отделяет технические решения;
- `branch-diff` как режим работы с веткой аналитика снижает риск небезопасного автоматического merge в folder-based документы;
- `openspec-explore` хорошо продуман для контекстной воронки: субагенты, YAML-контракты, агрегация, дедупликация, `.research` и `.research-notes.md`;
- многие правила написаны с учетом слабых моделей: лимиты, запрет рекурсии, явные fallback-цепочки, копирование ролевых промптов дословно.

Главные проблемы:

- нет строгой машинной валидации артефактов: схемы описаны текстом, но нет JSON Schema/YAML Schema, валидаторов и CI-проверок;
- справочники `change-examples.md` и `example-change.md` устарели относительно текущего шаблона `change.md`;
- жизненный цикл `.research/` противоречив: в одних местах он архивируется вместе с change, в других должен удаляться и не попадать в git;
- обычный `openspec-propose/templates/change.md` не содержит раздел `17. Уверенность и вопросы`, а diff-based шаблон содержит;
- есть напряжение между требованием непересекающихся зон субагентов и обязательным пересечением по схемам/OpenAPI/Proto/DDL;
- механизмы `branch-diff verify` пока во многом процедурные и зависят от внимательности модели.

Общая оценка: концепция сильная и современная, но для стабильной работы на слабых моделях требуется перевести больше правил из текста в исполняемые проверки, схемы, deterministic scripts и state-machine контракты.

## 2. Архитектура workflow

Фреймворк делит работу на следующие стадии:

| Стадия | Skill | Назначение |
|---|---|---|
| Обучение агента | `openspec-teach` | Объяснить layout, статусы, mapping запросов |
| Инициализация master spec | `openspec-init-master-spec` | Создать `_sdd/manifest.yaml`, `navigation.md`, `coverage.md`, `stale-files.md` |
| Исследование | `openspec-explore` | Read-only research кода или зоны изменения |
| Change вручную | `openspec-propose` | Интервью, выбор master-spec sources, создание `change.md` |
| Change из diff | `openspec-change-from-diff` | Создание `change.md` из diff refs по `openspec/<service>/` |
| Проектирование | `openspec-design` | Создание `design.md` и `tasks.md` |
| Реализация | `openspec-implement` | Выполнение задач, чекбоксы, сборка/тесты/линтер |
| Проверка master spec | `openspec-apply-change` | Verify/manual apply gateway |
| Архив | `openspec-archive-change` | Перенос change в archive |
| Deprecated stub | `openspec-new-spec` | Стоп-сигнал против single-file workflow |

Статусная модель в целом согласована:

- `На согласовании`: создан change;
- `Согласовано`: ручное подтверждение после ревью;
- `В реализации`: auto-переход при `openspec-implement`;
- `Реализовано`: после проверки реализации и master-spec update;
- `Архивировано`: после архивации.

Проблема: `openspec-archive-change` разрешает архивировать незавершенный change без смены статуса. Это полезно для аудита, но конфликтует с простой трактовкой из `README.md` и `openspec-teach`, где `Архивировано` означает перемещение в архив. Лучше явно разделить `archive_location` и `change_status`.

## 3. Непротиворечивость и корректность

### 3.1. Согласованные решения

1. **Folder-based master specification** последовательно закреплена в `README.md`, `openspec-teach`, `openspec-init-master-spec`, `openspec-propose`, `openspec-design`, `openspec-apply-change` и deprecated `openspec-new-spec`.
2. **Single-file workflow** явно запрещен и заменен на manifest/navigation layer.
3. **Spec update mode** присутствует в основном шаблоне change, diff-based шаблоне и downstream skills.
4. **Branch-diff** корректно отделяет source-of-truth документы аналитика от текущего worktree и запрещает `git fetch`, `git pull`, checkout base/analyst refs.
5. **Implement** правильно ограничен задачами из `tasks.md`, не считает `apply-change` обязательным финальным шагом и требует верификацию.
6. **Apply-change** осознанно работает как gateway, а не как blind merge.

### 3.2. Несогласованности и риски

| Риск | Где проявляется | Почему важно | Приоритет |
|---|---|---|---|
| Устаревшие примеры `change.md` | `openspec-propose/references/change-examples.md`, `example-change.md` | Примеры говорят про 13 разделов или пропускают 3, 4, 5, 7А; слабая модель будет копировать старую структуру | Высокий |
| Нет раздела 17 в обычном шаблоне | `openspec-propose/templates/change.md` против `openspec-change-from-diff/templates/change.md` | Уверенность, ограничения анализа и вопросы есть только у diff-based change | Высокий |
| `.research` одновременно временный и архивируемый | `README.md`, `openspec-archive-change`, `research-orchestration.md`, `invocation-contract.md` | Неясно, коммитить, удалять или архивировать research artifacts | Высокий |
| Непересекающиеся зоны против обязательного пересечения схем | `research-orchestration.md` и `invocation-contract.md` | Одна инструкция запрещает пересечение, другая ожидает общий поиск OpenAPI/Proto/DDL | Средний |
| AskUserQuestion fallback непоследователен | Явно есть в `archive`, частично в `implement`, отсутствует в `propose` | В разных harness модель может зависнуть или нарушить правило подтверждения | Средний |
| Много POSIX-команд в skills | `mkdir -p`, `mv`, `ls`, `find` | На Windows/PowerShell нужна адаптация или wrapper; слабая модель может выполнить неверную команду | Средний |
| Нет формального state-machine файла статусов | Статусы описаны таблицами в нескольких местах | Риск дрейфа правил переходов и ручных исключений | Средний |
| Branch-diff verify пока процедурный | `README.md` roadmap, `apply-change`, `merge-workflow` | Проверка diff metadata зависит от модели, а не от детерминированного валидатора | Высокий |

## 4. Оценка качества написания skills

### Сильные практики

- У каждого основного skill есть frontmatter: `name`, `description`, `license`, `compatibility`, `metadata.version`.
- Описания триггеров достаточно конкретны для выбора skill.
- Роль каждого документа понятна: `change.md` описывает требования, `design.md` проектирует, `tasks.md` исполняется.
- Guardrails написаны прагматично: запрещены silent overwrite, blind merge, рекурсия субагентов, копирование примеров, скрытие gaps.
- Есть хорошие runbook-документы для сложных skills: `openspec-implement/references/runbook.md`, `verification.md`, `edge-cases.md`.
- `openspec-change-from-diff` особенно силен: фиксирует refs, SHA, merge-base, diff command, changed/context files и confidence.

### Что снижает качество

- Много правил продублированы в разных документах без единого источника истины.
- Примеры не обновлены под текущую схему и могут тянуть модель назад к старому формату.
- В шаблонах есть HTML-комментарии, но нет механизма проверки, что они удалены из итогового файла.
- Некоторые reference-файлы имеют локально-доменные правила (`analytics-rules.md`, `common-requirements.md`), но не ясно, являются ли они обязательными для всех сервисов или примерными.
- В `change.md` жестко запрещены ссылки на исходный код. Это правильно для аналитического документа, но нужно сохранять evidence в `.research/_aggregate.yaml`, иначе теряется проверяемость утверждений о текущем поведении.

## 5. Оценка примененных SDD-техник

### Современно и корректно

1. **Spec as source of truth**: folder-based master spec вместо одного монолитного Markdown.
2. **Change proposal workflow**: отдельный change до кода, с ручным согласованием.
3. **Разделение WHAT/WHY и HOW**: `change.md` против `design.md`.
4. **Traceability by sources**: `## 0. Источники master specification` и diff metadata.
5. **Context funnel**: `navigation.md -> manifest.yaml -> selected docs -> research aggregate`.
6. **Contract-first элементы**: поддержка OpenAPI, Proto, Avro, JSON Schema, GraphQL, SQL DDL.
7. **Acceptance criteria**: формат `КОГДА/ТОГДА`, связь с FSM и инвариантами.
8. **Verification gate**: implement не завершается без сборки/тестов/линтера или явного объяснения невозможности.
9. **Read-only research**: отдельные роли и YAML-агрегация уменьшают контекстный шум.
10. **Branch-diff mode**: хорошо подходит для аналитической ветки с уже измененными документами.

### Чего не хватает

1. **Requirements IDs**: нет стабильных идентификаторов требований, правил, инвариантов, FSM-переходов и acceptance criteria.
2. **Traceability matrix**: нет машинной связи `requirement -> source doc -> design decision -> task -> test -> verification command`.
3. **Executable validation**: нет скрипта, который проверяет структуру `change.md`, наличие разделов, отсутствие плейсхолдеров, корректный статус.
4. **Contract linting**: OpenAPI/AsyncAPI/Proto/Avro упоминаются, но не подключены валидаторы.
5. **ADR/RFC слой**: design содержит decisions, но нет отдельного ADR-формата для долговременных архитектурных решений.
6. **CI gate для specs**: нет описанного pipeline, который валидирует `_sdd`, diff metadata и stale manifest.
7. **Policy-as-code**: правила роли change/design/implement описаны текстом, но не выражены как проверяемые политики.

## 6. Метаданные, классификация и хранение информации

### Текущее состояние

`_sdd/manifest.yaml` хранит:

- `schema_version`, `service`, `root`, `generated_at`, `generator`;
- `summary` с purpose, bounded context, domains, flows, integrations;
- `files[]` с `path`, `kind`, `format`, `title`, `status`, `hash`, `size_bytes`, `last_seen_at`, `tags`, `entities`, `integrations`, `endpoints`, `events`, `depends_on`, `related_files`, `read_priority`, `agent_notes`.

Это хорошая doc-level модель. Она позволяет строить контекстную воронку и избегать чтения всей папки.

### Ограничения текущей модели

- Не указан алгоритм hash (`sha256`, `git blob sha`, другое).
- Нет `confidence` для классификации файла.
- Нет `source`/`provenance` по извлеченным `entities`, `endpoints`, `events`.
- Нет `owner`, `reviewed_at`, `reviewed_by`, `last_verified_commit`.
- Нет `token_estimate` и `summary_short`, важных для контекстной воронки.
- Нет `chunk_id` или `sections[]`, поэтому большие документы индексируются только целиком.
- Нет `requirements[]` как нормализованной коллекции с IDs.
- Нет отдельной схемы для graph relations: `defines`, `depends_on`, `updates`, `implements`, `tests`.
- Нет формального `staleness_policy`: когда warning блокирует, а когда только снижает confidence.

### Рекомендуемая модель метаданных

Добавить рядом с текущим manifest:

1. `manifest.schema.json` или `manifest.schema.yaml`: строгая схема.
2. `_sdd/requirements.yaml`: нормализованные требования.
3. `_sdd/traceability.yaml`: связи между source docs, change, design, tasks, tests.
4. `_sdd/context-pack.yaml`: готовые наборы контекста для задач.
5. `_sdd/index.sqlite` или `_sdd/index.duckdb` как опциональный машинный индекс для быстрых запросов.

Минимальные новые поля в `files[]`:

```yaml
classification:
  confidence: high|medium|low
  reasons: []
  extracted_by: openspec-init-master-spec
  verified_by: null
content:
  hash_algorithm: sha256
  token_estimate: 0
  summary_short: ""
  sections:
    - id: ""
      title: ""
      line_start: 0
      line_end: 0
relations:
  defines_requirements: []
  references_requirements: []
  implements_contracts: []
```

## 7. Контекстная воронка

Текущая воронка:

1. Не читать всю папку, если есть manifest.
2. Читать `navigation.md`.
3. Читать `manifest.yaml`.
4. Выбрать документы по tags/entities/integrations/endpoints/events/relations/read_priority.
5. Запустить structured research.
6. Работать с YAML aggregate, а не с сырыми файлами.

Это правильный подход. Для повышения надежности нужно сделать воронку плановой и проверяемой.

Рекомендуемый артефакт: `.context-plan.yaml` рядом с change:

```yaml
target: change
service: external-gateway
change: add-callback-retry-policy
budget:
  max_files: 20
  max_tokens: 60000
selected_sources:
  - path: openspec/external-gateway/integrations/callback.md
    reason: shared_endpoint
    read_mode: full
  - path: openspec/external-gateway/data/callback-delivery.md
    reason: related_files
    read_mode: summary
research:
  roles: [feature-scope, dependencies, cross-cutting]
  aggregate: openspec/changes/add-callback-retry-policy/.research/_aggregate.yaml
gates:
  manifest_fresh: true
  stale_files_empty: true
  yaml_valid: true
```

Это снижает риск, что модель случайно расширит scope или забудет, почему читала файл.

## 8. MCP, инструменты и фреймворки для усиления

### MCP и tooling

| Направление | Что подключить | Зачем |
|---|---|---|
| Семантический код | Serena MCP, LSP MCP | `find_symbol`, references, меньше grep-ошибок |
| Поиск и RAG | embedding/vector MCP, локальный code index | Выбор релевантных документов и кода без полного чтения |
| Git/diff | Git MCP или deterministic git helper scripts | Безопасная работа с refs, SHA, pathspec, diff metadata |
| OpenAPI | Spectral/OpenAPI validator MCP или CLI wrapper | Проверка контрактов и style guide |
| AsyncAPI/Kafka | AsyncAPI validator | Формализовать topics, payloads, retries |
| Proto/Buf | Buf CLI/MCP | Проверка breaking changes в Protobuf |
| Avro/JSON Schema | Schema validator | Проверка совместимости схем |
| PlantUML/Mermaid/Draw.io | Renderer/validator MCP | Проверка диаграмм, обнаружение битых ссылок |
| DB schema | Postgres/SQL parser MCP, Liquibase/Flyway parser | Связать DDL, entities, migrations |
| CI/test | CI MCP или task runner wrapper | Запуск и фиксация verification gates |
| Issue/PR | Jira/Bitbucket/GitHub MCP | Связь change с тикетом, PR и approval |

### Фреймворки и подходы

- OpenAPI/AsyncAPI/Proto/Avro как contract-first слой.
- ADR для архитектурных решений из `design.md`.
- C4 model для карты сервисов и bounded contexts.
- Gherkin/Cucumber или lightweight Given/When/Then для executable acceptance criteria.
- Pact или Spring Cloud Contract для consumer-driven contracts.
- Spectral rulesets для API style guide.
- Buf breaking checks для Proto.
- JSON Schema validation для manifest, change metadata, research aggregate.
- Semgrep/tree-sitter для статического извлечения patterns, если LSP недоступен.

## 9. Повышение надежности workflow

### Исполнимые проверки

Добавить `scripts/` или MCP-tooling внутри bundle:

- `validate-manifest`: проверка schema, paths, hashes, relations.
- `validate-change`: разделы 0-17, отсутствие HTML-комментариев, корректный статус, `Spec update mode`, отсутствие плейсхолдеров.
- `validate-research`: YAML parse, обязательные поля, summary limit, dedup keys.
- `validate-diff-artifacts`: refs, merge-base, changed files, context files, stale manifest.
- `validate-tasks`: рабочие секции `## N.`, чекбоксы, отсутствие задач в нерабочих секциях.
- `validate-state-transition`: разрешенные переходы статусов.

### Единый state machine

Создать `_system/change-state-machine.yaml`:

```yaml
states:
  - На согласовании
  - Согласовано
  - В реализации
  - Реализовано
  - Архивировано
transitions:
  - from: На согласовании
    to: Согласовано
    actor: analyst
    automatic: false
  - from: Согласовано
    to: В реализации
    actor: openspec-implement
    automatic: true
  - from: В реализации
    to: Реализовано
    actor: openspec-apply-change|user
    automatic: false
archive_policy:
  allow_unfinished_archive: true
  preserve_status_when_unfinished: true
```

### Regression fixtures

Сделать тестовые fixture-каталоги:

- маленький service с manifest;
- stale manifest;
- branch-diff с modified/renamed/binary files;
- invalid research YAML;
- outdated change without `Spec update mode`;
- tasks with verification checklist.

На них запускать валидаторы и prompt regression tests.

## 10. Механизмы для слабых моделей, включая Qwen Coder Next

Текущие docs уже учитывают Qwen Coder Next: явные числа, `≤200 слов`, готовые YAML-контракты, запрет рекурсии, fallback, персистентность `.research`, копирование prompts дословно.

Дополнительные меры:

1. **Схемы вместо прозы**. Каждому output (`manifest`, `change`, `research`, `tasks`) нужна JSON/YAML Schema. Qwen лучше следует проверяемым полям, чем длинным инструкциям.
2. **Один шаг - один артефакт**. Разбить сложные skills на micro-steps: read metadata, select context, write plan, validate, then generate.
3. **Обязательный preflight**. Перед генерацией файл `preflight.yaml`: inputs, missing fields, selected docs, blockers. Если blockers не пустой, генерация запрещена.
4. **Контекстные пакеты**. Слабой модели давать не весь manifest, а `context-pack.yaml` с 5-20 выбранными sources, reason и read mode.
5. **Детерминированные helper scripts**. Git diff, hash, path existence, YAML parse и section checks должны делать скрипты, не модель.
6. **Verifier pass**. После генерации отдельный validator или отдельный verifier-agent проверяет только структуру и противоречия, без переписывания.
7. **Positive/negative examples**. Для каждого шаблона добавить короткий correct/incorrect пример. Сейчас есть длинные примеры, но они устарели.
8. **Контроль плейсхолдеров**. Валидатор должен падать при `<!--`, `<...>`, `TODO`, `не применимо`, `n/a` в заполненном artifact.
9. **Жесткий словарь значений**. Все статусы, modes, kind, format, read_priority, confidence должны быть enum.
10. **Confidence budget**. Если confidence ниже `Высокая`, downstream skill обязан выводить open questions и не auto-approve.
11. **Line evidence**. В `.research` хранить `source_ref: file:line`; в `change.md` не обязательно показывать кодовые ссылки, но evidence должен сохраняться.
12. **Short summaries**. Каждый большой doc должен иметь `summary_short` и `key_sections`, чтобы Qwen не читал 200k контекста подряд.
13. **Stop conditions**. В каждом skill явно задать блокирующие условия в отдельной таблице `blockers`, а не только в prose.
14. **No hidden optionality**. Все "при необходимости" заменить на decision table: condition -> action.
15. **Модельный режим `weak_model=true`**. При включении: меньше параллельных ролей, больше валидаторов, больше disk checkpoints, меньше свободной генерации.

Практический режим для Qwen Coder Next:

```yaml
weak_model_profile:
  max_selected_docs: 12
  max_raw_file_read_tokens: 60000
  require_context_plan: true
  require_schema_validation: true
  require_preflight: true
  require_post_validation: true
  generation_style: section_by_section
  allow_freeform_reasoning_in_artifact: false
  require_confidence: true
  retry_invalid_yaml_once: true
```

## 11. Рекомендуемый план улучшений

### Быстрые правки

1. Обновить `change-examples.md`: заменить "13 разделов" на текущую структуру 16 + 1.5 + 7А.
2. Обновить `example-change.md`: добавить разделы 3, 4, 5, 7А и, желательно, 17.
3. Добавить `## 17. Уверенность и вопросы` в обычный `openspec-propose/templates/change.md`.
4. Явно решить lifecycle `.research`: архивируем как evidence или удаляем как временный материал. Не держать оба правила.
5. В `openspec-explore` уточнить: зоны субагентов не пересекаются кроме разрешенного shared schema surface.
6. Добавить AskUserQuestion fallback в `openspec-propose`, `openspec-design`, `openspec-apply-change`.
7. Добавить Windows/PowerShell альтернативы для `mkdir -p`, `mv`, `ls`, `find` или вынести команды в wrappers.

### Среднесрочные улучшения

1. Ввести schemas для `manifest`, `.research/_aggregate.yaml`, `changed-files.yaml`, `context-files.yaml`.
2. Сделать валидаторы `validate-*` и запускать их после генерации.
3. Ввести `requirements.yaml` и `traceability.yaml`.
4. Добавить `context-plan.yaml` и `context-pack.yaml`.
5. Добавить CI checks для `_sdd`, change templates и branch-diff metadata.
6. Подключить Spectral/OpenAPI, AsyncAPI, Buf, JSON Schema validators.

### Долгосрочные улучшения

1. Построить SDD knowledge graph: docs, requirements, contracts, code symbols, tasks, tests, PRs.
2. Поддержать semantic retrieval через embeddings с сохранением source refs.
3. Ввести continuous spec verification: diff code/contracts/spec -> gaps.
4. Добавить benchmark fixtures для оценки качества разных моделей, включая Qwen Coder Next.
5. Создать UI/CLI для выбора active change, просмотра context funnel и запуска валидаторов.

## 12. Итоговая оценка

Фреймворк уже содержит правильные современные идеи SDD: source-of-truth specs, change-first workflow, контекстная воронка, role-based research, branch-diff, acceptance criteria и verification gate. Он хорошо подходит как основа для AI-assisted SDD.

Основной следующий шаг - снизить зависимость от аккуратности модели. Для этого нужны не новые длинные инструкции, а проверяемые схемы, валидаторы, state-machine, context plan и deterministic helpers. Это особенно важно для Qwen Coder Next: модель сможет надежно работать, если ей давать маленькие структурированные пакеты, жесткие enum-значения, одну задачу за раз и автоматическую проверку каждого артефакта.
