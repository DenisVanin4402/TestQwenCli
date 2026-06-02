---
name: openspec-change-from-diff
description: Создать аналитический `change.md` из git diff двух локальных refs по folder-based master specification `openspec/<service>/`. Используй, когда аналитик уже изменил master-spec в отдельной ветке, а разработчик в любой рабочей ветке хочет получить полноценный change-request из diff без checkout base/analyst веток.
license: MIT
compatibility: Требуется `openspec/` layout, локальные git refs, master-spec folder `openspec/<service>/`, manifest `openspec/<service>/_sdd/manifest.yaml` хотя бы в analyst/base ref, доступ к bash/git и опционально tool запуска read-only субагентов.
metadata:
  author: openspec-distillate
  version: "1.0"
---

Создать `openspec/changes/<change-name>/change.md` из разницы между двумя локальными git refs по master-spec folder.

Этот skill не делает `git fetch`, `git pull`, `git checkout <base_ref>` или `git checkout <analyst_ref>`. Он работает из любой текущей ветки пользователя и читает файлы из git object database через `git show <ref>:<path>`.

**Input**:

Обязательные параметры:

- `service` — имя сервиса, master-spec root `openspec/<service>/`;
- `base_ref` — базовая локальная ветка или ref;
- `analyst_ref` — локальная ветка или ref с измененной master specification;
- `change_name` — имя change в kebab-case.

Опциональные параметры:

- `diff_mode=three-dot|two-dot`;
- `approval=approved|draft`;
- `thoroughness=quick|medium|full`;
- `include_context=manifest|manifest+related|full`;
- `allow_binary=true|false`;
- `write_diff_artifacts=true|false`.

Значения по умолчанию:

```text
diff_mode=three-dot
approval=draft
thoroughness=medium
include_context=manifest+related
allow_binary=true
write_diff_artifacts=true
```

**Bundle-пути**

- `templates/change.md`
- `references/diff-workflow.md`
- `references/context-selection.md`
- `references/change-generation.md`
- `references/review-checklist.md`

Внешние references:

- `../openspec-explore/references/invocation-contract.md`
- `../openspec-explore/references/research-roles.md`
- `../openspec-explore/references/research-orchestration.md`

---

## Steps

### 1. Проверить входные параметры

Проверь:

- `service` не пустой и не равен reserved names: `changes`, `archive`, `_sdd`, `_system`;
- `change_name` в kebab-case;
- `diff_mode` равен `three-dot` или `two-dot`;
- `approval` равен `draft` или `approved`;
- `allow_binary`, `write_diff_artifacts` приведены к boolean.

Если обязательного параметра нет, спроси пользователя. Не угадывай refs.

### 2. Проверить локальные refs

Выполни проверки только локально:

```bash
git rev-parse --verify <base_ref>
git rev-parse --verify <analyst_ref>
```

Запиши SHA обоих refs. Если ref отсутствует, остановись и попроси пользователя подготовить ref вручную.

Запрещено автоматически выполнять:

```bash
git fetch
git pull
git checkout <base_ref>
git checkout <analyst_ref>
```

### 3. Проверить master-spec root

Root:

```text
openspec/<service>/
```

Проверь, что root существует хотя бы в одном ref через git object database, например:

```bash
git cat-file -e <base_ref>:openspec/<service>/
git cat-file -e <analyst_ref>:openspec/<service>/
```

Если root есть только в `analyst_ref`, это добавление master specification. Если root есть только в `base_ref`, это удаление master specification; продолжай только после явного подтверждения пользователя.

### 4. Определить range diff

Для `diff_mode=three-dot` используй:

```bash
git merge-base <base_ref> <analyst_ref>
git diff --name-status --find-renames <base_ref>...<analyst_ref> -- openspec/<service>/
git diff --stat <base_ref>...<analyst_ref> -- openspec/<service>/
git diff --find-renames --find-copies <base_ref>...<analyst_ref> -- openspec/<service>/
```

Для `diff_mode=two-dot` используй только при явном выборе пользователя:

```bash
git diff --name-status --find-renames <base_ref>..<analyst_ref> -- openspec/<service>/
git diff --stat <base_ref>..<analyst_ref> -- openspec/<service>/
git diff --find-renames --find-copies <base_ref>..<analyst_ref> -- openspec/<service>/
```

Если diff пустой, не создавай `change.md`: сообщи, что изменений master-spec по выбранному root нет.

### 5. Подготовить change-директорию

Путь:

```text
openspec/changes/<change_name>/
```

Если `change.md` уже существует, спроси пользователя: продолжить редактирование или выбрать другое имя. Не перезаписывай молча.

Если `write_diff_artifacts=true`, создай:

```text
openspec/changes/<change_name>/.spec-diff/
  refs.txt
  name-status.txt
  stat.txt
  patch.diff
  changed-files.yaml
  context-files.yaml
```

Artifacts не должны содержать локальные копии обеих веток или полную копию master-spec folder.

### 6. Нормализовать changed files

Используй `git diff --name-status --find-renames`.

Нормализуй статусы:

| Git status | Нормализованный status |
|---|---|
| `A` | `added` |
| `M` | `modified` |
| `D` | `deleted` |
| `R*` | `renamed` |
| `C*` | `copied` |
| `T` | `typechanged` |

Для renamed/copied сохрани `old_path` и `path`. Для остальных `old_path: null`.

Для каждого файла зафиксируй:

- `status`;
- `path`;
- `old_path`;
- `binary`;
- `extension`;
- краткую роль в изменении.

### 7. Прочитать содержимое файлов без checkout

Для каждого changed file:

- `added` — читать new content из `analyst_ref`;
- `deleted` — читать old content из `base_ref`;
- `modified` / `typechanged` — читать old content из `base_ref` и new content из `analyst_ref`;
- `renamed` — читать old content по `old_path` из `base_ref`, new content по `path` из `analyst_ref`;
- `copied` — читать source/target по доступности в refs.

Команды:

```bash
git show <base_ref>:<path>
git show <analyst_ref>:<path>
```

Если файл отсутствует в одном ref — это ожидаемо для added/deleted/renamed. Зафиксируй это в анализе, а не как ошибку.

Для больших файлов не загружай всё в итоговый контекст: прочитай заголовки, оглавление, релевантные фрагменты и сделай summary. Для binary файла зафиксируй тип, путь, размер и соседние текстовые документы.

### 8. Выбрать context files через manifest

Прочитай manifest:

```bash
git show <analyst_ref>:openspec/<service>/_sdd/manifest.yaml
```

Если manifest отсутствует в `analyst_ref`, попробуй:

```bash
git show <base_ref>:openspec/<service>/_sdd/manifest.yaml
```

Если manifest отсутствует в обоих refs, остановись или продолжи только после явного подтверждения пользователя. Основная рекомендация — сначала выполнить `openspec-init-master-spec` на ветке, где master-spec documents актуальны.

По manifest выбери context files:

- `depends_on`;
- `related_files`;
- файлы с общими `entities`;
- файлы с общими `integrations`;
- файлы с общими `endpoints`;
- файлы с общими `events`;
- `read_priority=high`.

При `include_context=manifest` ограничься прямыми `depends_on`, `related_files` и high-priority. При `manifest+related` добавь совпадения по entities/integrations/endpoints/events. При `full` разрешены дополнительные документы из manifest, но всё равно не читай папку рекурсивно без причины.

Запиши выбранный контекст в `.spec-diff/context-files.yaml`.

### 9. Выполнить structured analysis при необходимости

Для большого diff, `thoroughness=full`, binary-only изменений или противоречивого manifest запусти read-only анализ по ролям:

- `diff-scope`;
- `business-impact`;
- `contract-impact`;
- `data-impact`;
- `quality-impact`;
- `consistency-review`.

Каждый анализ читает только `.spec-diff/*`, changed files и выбранные context files через `git show`. Результат — YAML summary. Лид агрегирует выводы и использует их для `change.md`.

### 10. Сгенерировать change.md

Используй `templates/change.md`.

Статус:

- `approval=draft` или не указан — `На согласовании`;
- `approval=approved` — `Согласовано`.

Обязательные header fields:

- `Мастер-спецификация`;
- `Manifest`;
- `Spec update mode: branch-diff`;
- `Base ref`;
- `Analyst ref`;
- `Diff mode`;
- `Merge base`;
- `Diff command`.

Добавь раздел `## 0. Источники изменения`:

- Git refs;
- Changed files;
- Context files.

Заполняй 16 аналитических разделов по смыслу, а не копированием patch. Для незатронутых разделов пиши ровно:

```text
Нет изменений.
```

В конце заполни `## 17. Уверенность и вопросы`.

### 11. Review

Проверь:

- refs существуют локально;
- diff command содержит pathspec `-- openspec/<service>/`;
- checkout base/analyst веток не выполнялся;
- все added/modified/deleted/renamed/copied/typechanged файлы учтены;
- manifest прочитан из analyst ref или fallback явно зафиксирован;
- context files выбраны через manifest;
- binary files не проигнорированы;
- `change.md` содержит diff metadata, changed files, context files;
- downstream skills смогут прочитать `Spec update mode: branch-diff`.

---

## Output

Сообщи:

- change name и путь к `change.md`;
- service и master-spec root;
- base ref/SHA, analyst ref/SHA, diff mode, merge-base;
- путь к `.spec-diff/`, если artifacts записаны;
- количество changed files по статусам;
- какие context files выбраны;
- warnings: missing/stale manifest, binary files, deleted docs, empty context, conflicts;
- confidence;
- следующий шаг:
  - если статус `На согласовании` — ревью/согласование change;
  - если статус `Согласовано` — `openspec-design`, затем `openspec-implement`;
  - после реализации — branch-diff verify master-spec source и archive.

---

## Guardrails

- Не делай `git fetch`, `git pull`, checkout base/analyst refs или worktree switch.
- Не создавай полную копию master-spec папки из refs.
- Не создавай `change.md` при пустом diff.
- Не скрывай отсутствие manifest: это warning/blocker в зависимости от подтверждения пользователя.
- Не копируй patch напрямую в `change.md`.
- Не ставь статус `Согласовано`, если `approval=approved` не указан явно.
- Не требуй финального `apply-change`: для `branch-diff` source of truth уже находится в analyst ref.
