---
name: openspec-init-master-spec
description: Инициализировать или обновить folder-based master specification в `openspec/<service-name>/`: создать `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md` и при необходимости `_sdd/stale-files.md`. Используй, когда пользователь говорит "инициализируй master spec", "подключи папку как мастер спецификацию", "собери manifest для openspec/<service>", "обнови navigation/manifest", "refresh master spec".
license: MIT
compatibility: Требуется `openspec/` layout, доступ к файловой системе проекта и git для чтения списка файлов.
metadata:
  author: openspec-distillate
  version: "1.0"
---

Инициализировать или обновить master specification сервиса в формате папки `openspec/<service-name>/`.

Скилл создает навигационный слой `_sdd/` поверх уже существующей документации. Он не переписывает пользовательские документы, не пытается слить материалы в один Markdown-файл и не создает single-file master specification.

**Input**

- `service` — имя сервиса в kebab-case.
- `root` — путь к master-spec root. По умолчанию `openspec/<service>`.
- `thoroughness` — `quick | medium | full`, по умолчанию `medium`.
- `include_patterns` — опциональные glob-паттерны включения.
- `exclude_patterns` — опциональные glob-паттерны исключения.
- `update_mode` — `init | refresh`, по умолчанию `init`.
- `manifest_only` — `true | false`, по умолчанию `false`.

**Reserved names**

Имена `changes`, `archive`, `_sdd`, `_system` зарезервированы. Если `service` равен одному из них, остановись и попроси другое имя.

**Bundle-пути**

- `templates/manifest.yaml`
- `templates/navigation.md`
- `templates/coverage.md`
- `templates/stale-files.md`
- `references/manifest-schema.md`
- `references/classification-rules.md`
- `references/refresh-workflow.md`
- `references/review-checklist.md`

Пути всегда относительны к директории этого `SKILL.md`.

---

## Steps

### 1. Проверка входа

1. Определи `service` и `root`.
2. Проверь reserved names.
3. Проверь, что `root` существует и находится внутри `openspec/`.
4. Проверь, что `root` не равен `openspec/changes` и не лежит внутри `openspec/changes/archive`.
5. Создай `root/_sdd/`, если его нет.

Если `root` отсутствует, остановись: пользователь должен сначала скопировать или создать документы master specification.

### 2. Сбор inventory

1. Получи список tracked-файлов через `git ls-files <root>`.
2. Получи список untracked-файлов обычным filesystem scan.
3. Объедини списки и дедуплицируй пути.
4. Исключи:
   - `root/_sdd/**`;
   - `.git/**`;
   - временные файлы редакторов;
   - build output;
   - архивы и generated artifacts, если они не являются явной частью документации.
5. Применяй `include_patterns` и `exclude_patterns`, если они заданы.

Не начинай с полного чтения содержимого всех файлов, если уже существует актуальный manifest и режим `refresh`: сначала сравни пути, размеры и hash.

### 3. Классификация файлов

Используй `references/classification-rules.md`.

Для каждого файла определи:

- `kind`;
- `format`;
- `title`;
- `status`;
- `hash`;
- `size_bytes`;
- `last_seen_at`;
- `tags`;
- `entities`;
- `integrations`;
- `endpoints`;
- `events`;
- `depends_on`;
- `related_files`;
- `read_priority`;
- `agent_notes`.

Markdown, YAML, JSON, OpenAPI и PlantUML можно читать как текст. Для PDF, изображений, Draw.io и других бинарных артефактов фиксируй путь, размер, расширение и предполагаемую роль по имени файла и соседним текстовым документам. Если бинарный файл важен, но рядом нет текстового описания, добавь warning в `stale-files.md` или coverage gaps.

### 4. Init mode

В режиме `init`:

1. Сформируй полный inventory.
2. Если папка большая, раздели анализ по ролям:
   - `doc-inventory` — структура и классификация;
   - `business-map` — домены, процессы, инварианты;
   - `contract-map` — API, события, интеграции, схемы;
   - `data-map` — модели данных, состояния, миграции;
   - `quality-map` — ошибки, безопасность, мониторинг, НФТ;
   - `cross-link-map` — связи, дубли, противоречия, пробелы.
3. Запиши `manifest.yaml`.
4. Если `manifest_only=false`, запиши `navigation.md` и `coverage.md`.
5. Запиши `stale-files.md` только при наличии предупреждений, stale-состояний или ручных проверок.

### 5. Refresh mode

В режиме `refresh` используй `references/refresh-workflow.md`:

1. Прочитай текущий `root/_sdd/manifest.yaml`.
2. Пересчитай список файлов, размеры и content hash.
3. Раздели файлы на `unchanged`, `added`, `modified`, `deleted`.
4. Не перечитывай `unchanged` без необходимости.
5. Для `added` и `modified` обнови classification, tags, entities, relations.
6. Для `deleted` удали записи из manifest и проверь ссылки на них.
7. Обнови `navigation.md` и `coverage.md`.
8. Запиши `stale-files.md`, если остались сомнения, hash mismatch, битые связи или конфликт классификации.

### 6. Self-review

Перед финальным ответом выполни чеклист из `references/review-checklist.md`:

- все файлы из master root либо описаны в manifest, либо явно исключены;
- `related_files` и `depends_on` указывают на существующие файлы;
- для каждого `read_priority=high` есть `agent_notes`;
- `navigation.md` содержит "что читать первым" и карту по областям;
- `coverage.md` честно показывает пробелы;
- `stale-files.md` создан, если есть warnings;
- `openspec/changes/` не попал в manifest сервиса.

---

## Output

Сообщи:

- service и root;
- созданные или обновленные файлы;
- режим `init` или `refresh`;
- статистику: всего файлов, added, modified, deleted, binary, warnings;
- документы, требующие ручной проверки;
- следующий шаг: для изменений требований использовать `openspec-propose`, для refresh после ручных правок снова `openspec-init-master-spec`.

---

## Guardrails

- Не переписывай пользовательские документы master specification.
- Не создавай один общий single-file master specification.
- Не включай `openspec/changes/**` в manifest сервиса.
- Не игнорируй бинарные файлы: фиксируй их как артефакты, даже если содержимое нельзя прочитать.
- Не скрывай gaps: если документации нет или она противоречива, отрази это в `coverage.md` и `stale-files.md`.
- Не копируй плейсхолдеры шаблонов как реальные данные.
