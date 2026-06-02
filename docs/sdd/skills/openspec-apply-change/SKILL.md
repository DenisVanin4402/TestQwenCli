---
name: openspec-apply-change
description: Manual-apply/verify gateway для folder-based master specification. Используй, когда пользователь явно просит применить change к документам master spec или проверить, что изменения master spec уже внесены. Автоматический single-file merge больше не является основной моделью.
license: MIT
compatibility: Требуется `openspec/` layout и `change.md`.
metadata:
  author: openspec-distillate
  version: "4.0"
---

Применить или проверить изменения master specification после реализации change.

В folder-based docs прямой автоматический merge `change.md` в произвольные документы небезопасен: документы могут быть Markdown, OpenAPI, YAML, диаграммы, таблицы и бинарные артефакты. Поэтому skill работает как gateway:

- читает `Spec update mode`;
- определяет затронутые master-spec documents;
- предупреждает о рисках;
- для `branch-diff` проверяет, что документы уже обновлены в `Analyst ref` и diff metadata не разошлась;
- для `manual-change` помогает пользователю явно выбрать документы и режим правки;
- не требует и не выполняет single-file merge по умолчанию.

**Input**: имя change. Если не указано, выбрать active change.

**Bundle-пути**

- `references/merge-workflow.md` — историческая справка о старом Analyst Merge и новые правила folder-based apply/verify.

---

## Steps

### 1. Выбрать change

Если имя указано — использовать его. Иначе посмотреть `openspec/changes/`, исключая `archive/`. Если несколько active changes — спросить пользователя.

### 2. Проверить change.md

Прочитать:

```text
openspec/changes/<name>/change.md
```

Если файла нет — предложить `openspec-propose`.

Проверить статус:

| Статус | Действие |
|---|---|
| `Согласовано` / `В реализации` | Можно проверять или применять master-spec update |
| `На согласовании` | Блок: change не согласован |
| `Реализовано` | Предложить `openspec-archive-change` |
| `Архивировано` | Выйти: change уже архивирован |
| Другой / отсутствует | Спросить подтверждение |

### 3. Прочитать folder-based контекст

Из шапки change прочитать:

- `Мастер-спецификация`;
- `Manifest`;
- `Spec update mode`;
- для `Spec update mode: branch-diff`: `Base ref`, `Analyst ref`, `Diff mode`, `Merge base`, `Diff command`;
- `## 0. Источники master specification`.
- если есть: `## 0. Источники изменения`.

Затем прочитать:

- `root/_sdd/navigation.md`;
- `root/_sdd/manifest.yaml`;
- `root/_sdd/stale-files.md`, если существует.

Если manifest отсутствует или stale, предложить `openspec-init-master-spec`.

Для `branch-diff` текущая ветка может не содержать master-spec docs analyst ref. В этом случае не считай отсутствие локальных файлов blocker для verify source: читай документы через `git show <analyst_ref>:<path>`.

### 4. Обработать `Spec update mode`

#### `branch-diff`

Default mode не изменяет master-spec documents.

1. Проверить локальные refs:

```bash
git rev-parse --verify <base_ref>
git rev-parse --verify <analyst_ref>
```

2. Для `three-dot` проверить merge-base:

```bash
git merge-base <base_ref> <analyst_ref>
```

3. Повторить diff summary:

```bash
git diff --name-status --find-renames <range> -- openspec/<service>/
git diff --stat <range> -- openspec/<service>/
```

4. Если есть `.spec-diff/changed-files.yaml`, сравнить текущий `name-status` с записанным metadata.
5. Проверить, что master-spec изменения действительно существуют в `analyst_ref` через `git show <analyst_ref>:<path>`.
6. Проверить, что изменения требований из `change.md` отражены в changed/context documents.
7. Проверить, что `_sdd/manifest.yaml`, `navigation.md` и `coverage.md` обновлены или явно требуют refresh.
8. Если нужно проверить, что текущая ветка уже содержит документы analyst ref, использовать:

```bash
git diff --quiet <analyst_ref> -- openspec/<service>/
```

Если команда показывает отличия, вывести warning, но не применять изменения автоматически.

9. Если все подтверждено, можно заменить статус change на `Реализовано`.
10. Если не подтверждено, вывести список документов и gaps для ручной правки.

Explicit apply override разрешен только по явному запросу пользователя. Перед ним обязательно:

1. Проверить clean status master-spec root:

```bash
git status --porcelain -- openspec/<service>/
```

2. Если есть незакоммиченные изменения — остановиться.
3. Предупредить, что операция изменит master-spec documents текущей ветки и не является default branch-diff workflow.
4. Получить явное подтверждение пользователя.
5. Только после этого выполнить:

```bash
git checkout <analyst_ref> -- openspec/<service>/
```

#### `manual-change`

1. Показать список документов из `## 0. Источники master specification` и manifest relations.
2. Попросить пользователя явно подтвердить, какие документы править.
3. Править только подтвержденные документы.
4. После правки предложить `openspec-init-master-spec` в режиме refresh.
5. Если документы обновлены и проверены, можно заменить статус change на `Реализовано`.

#### Mode отсутствует

Остановиться и попросить выбрать `manual-change` или `branch-diff`. Не угадывать.

### 5. Статус change

Если master-spec update проверен или применен, заменить только строку:

```md
> **Статус**: Реализовано
```

Остальное в `change.md` не трогать.

---

## Output

Сообщи:

- change;
- master-spec root;
- mode;
- какие документы проверены или изменены;
- нужны ли refresh manifest/navigation/coverage;
- статус change;
- следующий шаг: `openspec-archive-change`.

---

## Guardrails

- Не выполняй автоматический merge в произвольные folder-based documents без явного подтверждения пользователя.
- Для `branch-diff` default mode — verify only, без изменения master-spec documents.
- Не требуй single-file target spec.
- Не создавай `.bak`-файлы.
- Не скрывай gaps: если документ не обновлен, покажи это как blocker.
- В `change.md` меняй только строку статуса.
