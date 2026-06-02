---
name: openspec-propose
description: Создать `change.md` — предложение на изменение folder-based master specification. Использует `openspec/<service>/_sdd/navigation.md` и `manifest.yaml`, выбирает релевантные документы по tags/entities/integrations/endpoints/events/relations, проводит интервью и structured research зоны изменения. Используй до написания кода, когда пользователь формулирует новую фичу, баг-фикс, изменение требований или change-request.
license: MIT
compatibility: Требуется `openspec/` layout, master-spec folder `openspec/<service>/`, AskUserQuestion tool, доступ к bash/git и tool запуска read-only субагентов.
metadata:
  author: openspec-distillate
  version: "5.0"
---

Создать предложение на изменение master specification — `openspec/changes/<name>/change.md`.

`change.md` описывает ЗАЧЕМ и ЧТО меняется. Он не описывает КАК реализовать изменение в коде и не требует single-file target spec.

**Input**: название change в kebab-case или описание изменения. Нужно определить `service` и `change name`.

**Bundle-пути**

- `templates/change.md`
- `references/interview-playbook.md`
- `references/section-guide.md`
- `references/guardrails.md`
- `references/change-examples.md`
- `references/example-change.md`
- `references/common-requirements.md`
- `references/analytics-rules.md`
- `references/terms-and-abbreviations.md`

Внешние references:

- `../openspec-explore/references/invocation-contract.md`
- `../openspec-explore/references/research-roles.md`
- `../openspec-explore/references/research-orchestration.md`
- `../openspec-explore/references/code-analysis-priority.md`

---

## Steps

### 1. Этап 1 интервью — ЧТО и ЗАЧЕМ

Задай открытый вопрос и уточнения порциями по 2-3:

- мотивация;
- скоуп;
- пользователи;
- ожидаемый результат;
- ограничения;
- обратная совместимость;
- предполагаемый `Spec update mode`: `manual-change` или `branch-diff`.

Не создавай `change.md` после одного ответа. Подведи итог и получи явное подтверждение.

### 2. Найди master-spec root

Определи `service` и root:

```text
openspec/<service>/
```

Проверь:

- root существует;
- service не равен `changes`, `archive`, `_sdd`, `_system`;
- `openspec/<service>/_sdd/navigation.md` существует;
- `openspec/<service>/_sdd/manifest.yaml` существует.

Если root есть, но `_sdd/manifest.yaml` отсутствует, остановись и предложи сначала `openspec-init-master-spec`.

Если `stale-files.md` существует и непустой, предупреди пользователя и спроси подтверждение продолжения. По умолчанию предложи refresh через `openspec-init-master-spec`.

### 3. Прочитай navigation и manifest

Порядок:

1. Прочитай `openspec/<service>/_sdd/navigation.md`.
2. Прочитай `openspec/<service>/_sdd/manifest.yaml`.
3. Выбери документы по:
   - `tags`;
   - `entities`;
   - `integrations`;
   - `endpoints`;
   - `events`;
   - `related_files`;
   - `depends_on`;
   - `read_priority=high`.
4. Прочитай только выбранные документы и high-priority documents.

Не читай всю master-spec папку рекурсивно при наличии manifest.

### 4. Зафиксируй sources

Собери таблицу для раздела `## 0. Источники master specification`:

| Роль | Файл | Почему использован |
|---|---|---|

Каждый прочитанный документ master spec должен иметь причину: high-priority, entity match, integration match, endpoint match, relation, coverage gap.

### 5. Structured research зоны изменения

Лид не читает исходный код проекта массово. Для кода используй read-only субагентов по контракту `openspec-explore`:

- `feature-scope`;
- `dependencies`;
- `cross-cutting`.

Параметры:

- `target=change`;
- `name=<change-name>`;
- `service=<service>`;
- `master_spec_root=openspec/<service>/`;
- `anchor=<пути/символы/endpoint/entities из интервью и manifest>`;
- `thoroughness=quick` по умолчанию.

Создай каталог:

```text
openspec/changes/<name>/.research
```

Сохрани `.research/<role>.yaml`, `.research/_aggregate.yaml`, `.research-notes.md` до перехода к этапу 2.

### 6. Этап 2 интервью — детали изменения

Привязывай вопросы к:

- выбранным master-spec documents;
- gaps из `coverage.md`, `stale-files.md`, `.research-notes.md`;
- текущей реализации в зоне изменения;
- breaking risk и миграции.

Подведи итог всех требований и получи явное подтверждение перед записью файла.

### 7. Проверь change-директорию

Путь:

```text
openspec/changes/<name>/
```

Если `change.md` уже существует, спроси: продолжить редактирование или выбрать другое имя.

### 8. Заполни `change.md`

Открой `templates/change.md`.

Заполни шапку:

- статус `На согласовании`;
- дату;
- автора;
- версию;
- `Мастер-спецификация: openspec/<service>/`;
- `Manifest: openspec/<service>/_sdd/manifest.yaml`;
- `Spec update mode: manual-change | branch-diff`.

Заполни `## 0. Источники master specification`.

Для каждого раздела:

- если раздел затрагивается — реальные данные из интервью, master-spec sources и research;
- если не затрагивается — ровно `Нет изменений.`;
- не копируй примеры из references как реальные данные.

Запиши файл:

```text
openspec/changes/<name>/change.md
```

### 9. Ревью

Проверь:

- change не содержит технический план реализации;
- все затронутые master-spec documents указаны в sources;
- `Spec update mode` заполнен;
- нет ссылки на single-file target spec;
- все 16 основных разделов, подраздел 1.5 и раздел 7А присутствуют;
- gaps и open questions не скрыты.

---

## Output

Сообщи:

- имя change и путь к `change.md`;
- master-spec root;
- manifest;
- `Spec update mode`;
- какие master-spec documents использованы;
- какие разделы заполнены, какие — `Нет изменений.`;
- результат ревью;
- следующий шаг: PR review, затем ручной статус `Согласовано`, затем `openspec-design` для сложного CR или ручная работа для простого.

---

## Guardrails

- Не создавай `change.md`, пока остаются неясности.
- Всегда читай `_sdd/navigation.md` и `_sdd/manifest.yaml` перед выбором документов.
- Не требуй single-file target spec.
- Если manifest отсутствует или stale — предложи `openspec-init-master-spec`.
- `change.md` описывает ЧТО и ЗАЧЕМ, не КАК в коде.
- Код приложения, unit-тесты и технический план внедрения в `change.md` запрещены.
- `Spec update mode` обязателен.
