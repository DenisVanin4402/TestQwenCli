---
name: openspec-design
description: Создать технический проект реализации (`design.md`) и план задач (`tasks.md`) на основе согласованного `change.md`. Читает master-spec root, manifest и источники требований из `change.md`, использует документы master specification и точечное изучение кода. Используй когда change согласован и пользователь готовится к реализации.
license: MIT
compatibility: Требуется `openspec/` layout, `change.md`, доступ к bash/git. Опционально Serena/LSP/embedding MCP.
metadata:
  author: openspec-distillate
  version: "4.0"
---

Создать `design.md` и `tasks.md` на основе согласованного `change.md`.

**Input**: имя change. Если не указано, выбрать активный change.

**Bundle-пути**

- `templates/design.md`
- `templates/tasks.md`
- `../openspec-explore/references/code-analysis-priority.md`

---

## Steps

### 1. Выбрать change

Если имя указано — использовать его. Иначе посмотреть `openspec/changes/` и выбрать единственный active change, исключая `archive/`. Если active changes несколько, спросить пользователя.

### 2. Проверить файлы

Прочитать:

```text
openspec/changes/<name>/change.md
```

Если файла нет — предложить сначала `openspec-propose`.

Если `design.md` уже существует — спросить, продолжать редактирование или пересоздать.

### 3. Проверить статус

`change.md` должен быть в статусе `Согласовано` или `В реализации`.

Если статус `На согласовании`, остановиться и попросить завершить ревью. Если статус `Реализовано` или `Архивировано`, не пересоздавать design без явного подтверждения.

### 4. Прочитать change.md

Изучи:

- шапку;
- `Мастер-спецификация`;
- `Manifest`;
- `Spec update mode`;
- для `Spec update mode: branch-diff`: `Base ref`, `Analyst ref`, `Diff mode`, `Merge base`, `Diff command`;
- `## 0. Источники master specification`;
- если есть: `## 0. Источники изменения`;
- все функциональные разделы change;
- критерии приемки.

Если старый change не содержит `Manifest` или `Spec update mode`, зафиксируй риск и попроси пользователя подтвердить миграцию change к актуальному шаблону.

### 5. Прочитать master-spec context

1. Из шапки change возьми master-spec root.
2. Если `Spec update mode: branch-diff` и существует `openspec/changes/<name>/.spec-diff/changed-files.yaml`, прочитай его перед проектированием.
3. Если `Spec update mode: branch-diff` и существует `openspec/changes/<name>/.spec-diff/context-files.yaml`, прочитай его перед выбором дополнительных документов.
4. Прочитай `root/_sdd/navigation.md`, если файл есть в текущей ветке.
5. Прочитай `root/_sdd/manifest.yaml`, если файл есть в текущей ветке.
6. Прочитай документы из `## 0. Источники master specification` или `## 0. Источники изменения`.
7. При необходимости прочитай `read_priority=high` документы и `related_files`/`depends_on` из manifest.

Не читай всю папку master spec рекурсивно.

Если manifest отсутствует или явно stale, предложи `openspec-init-master-spec`.

Для `branch-diff` текущая рабочая ветка может не содержать документы analyst ref. В этом случае читай нужные файлы без checkout:

```bash
git show <analyst_ref>:<path>
```

Если файл удален в analyst ref, читай old content из `base_ref`:

```bash
git show <base_ref>:<path>
```

Не выполняй checkout base/analyst refs.

### 6. Изучить код точечно

Используй `.research-notes.md` рядом с change, если он есть и актуален.

Код читай точечно: 3-5 файлов за раз, только затронутые узлы и ближайшие вызывающие слои. Не запускай structured research заново. Если контекста не хватает для нетривиального решения, поставь паузу и предложи дополнительный `openspec-explore`.

Приоритет анализа: Serena/LSP/embeddings/MCP/Grep согласно `openspec-explore/references/code-analysis-priority.md`.

### 7. Создать design.md

Заполни `templates/design.md`:

- добавь `## Источники требований`;
- перечисли master-spec documents и что из них взято;
- для `branch-diff` добавь diff metadata: base ref, analyst ref, diff mode, merge-base, changed-files.yaml;
- перечисли `change.md`, research notes и ключевые code references;
- ссылайся на конкретные сущности кода проекта;
- не придумывай технологии и библиотеки, которых нет в проекте.

Запиши:

```text
openspec/changes/<name>/design.md
```

### 8. Создать tasks.md

На основе `design.md` и критериев приемки:

- группируй задачи логически;
- каждая рабочая задача — `- [ ] X.Y ...` в пронумерованной H2-секции;
- добавь проверку соответствия документам master specification, указанным в `change.md`;
- добавь чеклист верификации.

Запиши:

```text
openspec/changes/<name>/tasks.md
```

### 9. Self-review

Проверь:

- design ссылается на master-spec documents и кодовые сущности;
- tasks покрывают change requirements и acceptance;
- tasks не требуют автоматического final apply-change;
- для `branch-diff` tasks предлагают verify master-spec source, а не обязательный apply-change;
- следующий шаг учитывает `Spec update mode`.

---

## Output

Сообщи:

- change;
- путь к `design.md`;
- путь к `tasks.md`;
- master-spec root;
- spec update mode;
- ключевые technical decisions;
- open questions;
- следующий шаг: `openspec-implement`.

---

## Guardrails

- Всегда читай `change.md` перед началом.
- Всегда читай `navigation.md`, `manifest.yaml` и sources из `change.md`.
- Не требуй single-file target spec.
- Не проектируй вслепую.
- Не добавляй технологии, которых нет в проекте.
- При обновлении файлов сохраняй корректные заполненные данные.
