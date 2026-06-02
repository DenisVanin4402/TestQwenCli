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
- для `branch-diff` проверяет, что документы уже обновлены;
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
- `## 0. Источники master specification`.

Затем прочитать:

- `root/_sdd/navigation.md`;
- `root/_sdd/manifest.yaml`;
- `root/_sdd/stale-files.md`, если существует.

Если manifest отсутствует или stale, предложить `openspec-init-master-spec`.

### 4. Обработать `Spec update mode`

#### `branch-diff`

1. Сравнить документы master spec, указанные в `change.md`, с base branch через git diff.
2. Проверить, что изменения требований из `change.md` отражены в соответствующих документах.
3. Проверить, что `_sdd/manifest.yaml`, `navigation.md` и `coverage.md` обновлены или явно требуют refresh.
4. Если все подтверждено, можно заменить статус change на `Реализовано`.
5. Если не подтверждено, вывести список документов и gaps для ручной правки.

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
- Не требуй single-file target spec.
- Не создавай `.bak`-файлы.
- Не скрывай gaps: если документ не обновлен, покажи это как blocker.
- В `change.md` меняй только строку статуса.
