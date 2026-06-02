# План 2. Change.md из git diff master-spec веток

## 1. Цель

Добавить workflow, в котором аналитик меняет master specification напрямую в отдельной git-ветке, а разработчик в своей рабочей ветке получает полноценный `change.md` из разницы между базовой веткой и веткой аналитика.

Ключевое требование: сравнение выполняется средствами git по локальным refs, без checkout двух веток и без скачивания веток локально для сравнения. Пользователь сам обеспечивает наличие локальных веток или refs.

## 2. Зависимость от плана 1

Этот workflow опирается на folder-based master specification:

```text
openspec/<service-name>/
```

Для качественной генерации `change.md` должен существовать manifest:

```text
openspec/<service-name>/_sdd/manifest.yaml
```

Если manifest отсутствует или устарел, агент должен предупредить пользователя и предложить сначала выполнить `openspec-init-master-spec` на ветке аналитика или в ветке, где master-spec документы актуальны.

## 3. Основной сценарий

1. Есть базовая ветка, например `release/2026-06`.
2. Аналитик создает ветку от базовой, например `analysis/add-callback-retry`.
3. Аналитик меняет файлы в `openspec/<service-name>/`.
4. Аналитик коммитит изменения в своей ветке.
5. Разработчик находится в любой своей рабочей ветке.
6. Разработчик запускает генерацию change:

```text
base_ref=release/2026-06
analyst_ref=analysis/add-callback-retry
service=<service-name>
change=<change-name>
```

7. Framework выполняет git diff только по `openspec/<service-name>/`.
8. Framework читает не только patch, но и контекст master-spec folder через manifest.
9. Framework создает `openspec/changes/<change-name>/change.md`.
10. Разработчик запускает `openspec-design`, затем `openspec-implement`.
11. Финального влития `change.md` в master specification по умолчанию нет, потому что master specification уже изменена в ветке аналитика.
12. Вместо merge выполняется проверка, что spec source действительно существует и соответствует metadata change.

## 4. Новый skill

Добавить skill:

```text
openspec-change-from-diff
```

Назначение: создать аналитический `change.md` из git diff двух локальных refs по master-spec folder.

Триггеры:

- "создай change из diff веток";
- "сгенерируй change.md по ветке аналитика";
- "получи change по base branch и analyst branch";
- "change from git diff";
- "ветка аналитика уже изменила master spec".

## 5. Input

Обязательные параметры:

- `service` - имя сервиса;
- `base_ref` - базовая ветка/ref;
- `analyst_ref` - ветка/ref с измененной master specification;
- `change_name` - имя change в kebab-case.

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

## 6. Git semantics

### 6.1. Проверка refs

Использовать только локальные refs:

```bash
git rev-parse --verify <base_ref>
git rev-parse --verify <analyst_ref>
```

Запрещено автоматически выполнять:

```bash
git fetch
git pull
git checkout <base_ref>
git checkout <analyst_ref>
```

Если ref отсутствует, агент сообщает, что локального ref нет, и просит пользователя подготовить его вручную.

### 6.2. Основной diff

По умолчанию использовать three-dot:

```bash
git diff --name-status --find-renames <base_ref>...<analyst_ref> -- openspec/<service-name>/
git diff --stat <base_ref>...<analyst_ref> -- openspec/<service-name>/
git diff --find-renames --find-copies <base_ref>...<analyst_ref> -- openspec/<service-name>/
```

Three-dot означает: изменения ветки аналитика относительно merge-base с базовой веткой. Это подходит для сценария "аналитик сделал ветку от базовой и внес изменения".

### 6.3. Two-dot как явный override

Если пользователь хочет сравнить текущие снимки двух refs напрямую:

```bash
git diff <base_ref>..<analyst_ref> -- openspec/<service-name>/
```

Two-dot не используется по умолчанию, потому что он может включить изменения базовой ветки, появившиеся после ответвления.

### 6.4. Merge-base metadata

Для three-dot записывать merge-base:

```bash
git merge-base <base_ref> <analyst_ref>
```

Этот commit фиксируется в `change.md`, чтобы было понятно, от какой точки считался diff.

## 7. Артефакты diff

Если `write_diff_artifacts=true`, создать:

```text
openspec/changes/<change-name>/.spec-diff/
  refs.txt
  name-status.txt
  stat.txt
  patch.diff
  changed-files.yaml
  context-files.yaml
```

Эти файлы являются исследовательскими артефактами. Они помогают восстановить ход генерации, но не заменяют `change.md`.

Запрещено создавать локальные копии двух веток целиком. Допустимо:

- сохранить diff;
- сохранить список измененных файлов;
- прочитать конкретный файл из git object database через `git show <ref>:<path>`;
- сохранить короткие summaries по прочитанным файлам.

## 8. Определение измененных файлов

Команда:

```bash
git diff --name-status --find-renames <base_ref>...<analyst_ref> -- openspec/<service-name>/
```

Результат нормализуется в `changed-files.yaml`:

```yaml
service: <service-name>
base_ref: <base_ref>
analyst_ref: <analyst_ref>
diff_mode: three-dot
merge_base: <sha>
root: openspec/<service-name>/
files:
  - status: modified
    path: openspec/<service-name>/workflow/order-flow.md
    old_path: null
    binary: false
    extension: .md
  - status: added
    path: openspec/<service-name>/integrations/new-partner.md
    old_path: null
    binary: false
    extension: .md
  - status: deleted
    path: openspec/<service-name>/api/old-api.md
    old_path: null
    binary: false
    extension: .md
  - status: renamed
    path: openspec/<service-name>/workflow/new-name.md
    old_path: openspec/<service-name>/workflow/old-name.md
    binary: false
    extension: .md
```

Все добавленные, измененные, удаленные и переименованные файлы master-spec root попадают в анализ независимо от расширения.

## 9. Чтение содержимого без checkout

Для каждого измененного файла:

### 9.1. Файл есть в analyst_ref

```bash
git show <analyst_ref>:<path>
```

Используется как новая версия требований.

### 9.2. Файл есть в base_ref

```bash
git show <base_ref>:<path>
```

Используется как старая версия требований.

### 9.3. Deleted файл

Для deleted файла новая версия отсутствует. Читается старая версия из `base_ref`, а change фиксирует удаление и последствия.

### 9.4. Added файл

Для added файла старая версия отсутствует. Читается новая версия из `analyst_ref`, а change фиксирует новые требования.

### 9.5. Binary файл

Binary файл включается в список изменений. Если содержимое нельзя прочитать как текст:

- зафиксировать тип, путь, размер, статус;
- найти соседние Markdown/YAML файлы через manifest;
- если контекста недостаточно, добавить open question;
- не игнорировать файл молча.

## 10. Контекст остальной документации

`change.md` не должен быть механическим пересказом patch. Нужно использовать контекст всей master-spec папки.

Алгоритм выбора контекста:

1. Прочитать manifest из `analyst_ref`:

```bash
git show <analyst_ref>:openspec/<service-name>/_sdd/manifest.yaml
```

2. Если manifest отсутствует в `analyst_ref`, попробовать manifest из `base_ref`.
3. Если manifest отсутствует в обоих refs - предупредить и предложить `openspec-init-master-spec`.
4. По manifest найти для каждого changed file:
   - `depends_on`;
   - `related_files`;
   - файлы с теми же `entities`;
   - файлы с теми же `integrations`;
   - файлы с теми же `endpoints`;
   - файлы с теми же `events`;
   - `read_priority=high`.
5. Прочитать выбранные context files из `analyst_ref` через `git show`.
6. Если related файл тоже изменен, прочитать его обе версии.
7. Сформировать `context-files.yaml`.

Контекст нужен для:

- понимания мотивации изменения;
- выявления влияния на workflow;
- проверки связей между API, интеграциями, данными, FSM, ошибками;
- генерации качественных acceptance criteria;
- обнаружения противоречий.

## 11. Structured analysis

Для больших diff запускать read-only субагентов по ролям:

1. `diff-scope` - какие области требований изменились.
2. `business-impact` - бизнес-логика, workflow, инварианты, FSM.
3. `contract-impact` - API, интеграции, events, schemas.
4. `data-impact` - модели данных, миграции, совместимость.
5. `quality-impact` - ошибки, безопасность, логирование, мониторинг, НФТ.
6. `consistency-review` - противоречия между changed files и context files.

Каждый субагент работает read-only:

- читает только diff artifacts;
- читает выбранные через manifest файлы;
- не редактирует `change.md`;
- возвращает YAML summary.

Лид агрегирует YAML и создает `change.md`.

## 12. Генерация change.md

### 12.1. Header

Новая шапка для diff-based change:

```md
# Change: <change-name>

> **Статус**: На согласовании | Согласовано | В реализации | Реализовано | Архивировано
>
> **Дата создания**: YYYY-MM-DD
>
> **Автор**: <автор>
>
> **Версия**: 1.0
>
> **Мастер-спецификация**: openspec/<service-name>/
>
> **Manifest**: openspec/<service-name>/_sdd/manifest.yaml
>
> **Spec update mode**: branch-diff
>
> **Base ref**: <base_ref>
>
> **Analyst ref**: <analyst_ref>
>
> **Diff mode**: three-dot
>
> **Merge base**: <sha>
>
> **Diff command**: git diff --find-renames <base_ref>...<analyst_ref> -- openspec/<service-name>/
```

### 12.2. Status policy

- Если `approval=draft`, ставить `На согласовании`.
- Если `approval=approved`, ставить `Согласовано`.
- Если пользователь не указал approval, не угадывать. Дефолт - `На согласовании`.

Для рабочего сценария разработчика можно передать `approval=approved`, если ветка аналитика уже считается согласованным источником требований.

### 12.3. Sources section

Добавить раздел перед "Предложение":

```md
## 0. Источники изменения

### Git refs

| Поле | Значение |
|---|---|
| Base ref | ... |
| Analyst ref | ... |
| Diff mode | ... |
| Merge base | ... |

### Changed files

| Статус | Файл | Роль в изменении |
|---|---|---|

### Context files

| Файл | Почему прочитан |
|---|---|
```

### 12.4. Analytical content

Заполнение 16 разделов `change.md` выполняется не по строкам patch, а по смыслу:

- новые файлы превращаются в ADDED требования;
- измененные файлы превращаются в MODIFIED требования;
- удаленные файлы превращаются в REMOVED требования;
- контекстные файлы используются для связей и acceptance criteria;
- противоречия фиксируются как open questions или risks.

Разделы без изменений получают ровно:

```text
Нет изменений.
```

### 12.5. Confidence и вопросы

Добавить в конец:

```md
## 17. Уверенность и вопросы

### Уверенность генерации

Высокая / Средняя / Низкая

### Что требует подтверждения аналитика

- ...

### Ограничения анализа

- ...
```

Если patch большой, есть бинарные файлы или manifest stale, confidence не может быть "Высокая".

## 13. Изменения в downstream workflow

### 13.1. `openspec-design`

Должен понимать `Spec update mode: branch-diff`.

Новый порядок:

1. Прочитать `change.md`.
2. Прочитать `.spec-diff/changed-files.yaml`, если есть.
3. Прочитать manifest из текущей рабочей ветки, если master-spec docs присутствуют.
4. Если текущая ветка не содержит docs из analyst_ref, читать нужные master-spec файлы через `git show <analyst_ref>:<path>`.
5. Использовать `change.md` как основной документ требований.
6. Использовать diff metadata для трассируемости.

### 13.2. `openspec-implement`

Изменения минимальные:

- выполнять tasks как раньше;
- не предлагать обязательный final `apply-change`;
- после верификации предлагать проверку source-of-truth docs.

### 13.3. `openspec-apply-change`

Для `Spec update mode: branch-diff` default behavior:

1. Не мержить `change.md` в master-spec папку.
2. Проверить, что `Base ref`, `Analyst ref`, `Merge base` доступны локально.
3. Повторить diff summary и сравнить с `.spec-diff/changed-files.yaml`.
4. Проверить, что master-spec изменения уже существуют в analyst_ref.
5. Если текущая ветка должна содержать master-spec изменения, проверить:

```bash
git diff --quiet <analyst_ref> -- openspec/<service-name>/
```

6. Если изменения отсутствуют в текущей ветке - предупредить.
7. Если пользователь настаивает применить изменения master-spec в текущую ветку, разрешить explicit apply.

### 13.4. Explicit apply override

Если пользователь настаивает, можно применить master-spec изменения из ветки аналитика в текущий worktree:

```bash
git checkout <analyst_ref> -- openspec/<service-name>/
```

Перед этим обязательно:

1. Проверить `git status --porcelain -- openspec/<service-name>/`.
2. Если в master-spec root есть незакоммиченные изменения - остановиться.
3. Показать предупреждение:
   - операция изменит файлы master-spec папки в текущей ветке;
   - это не является обычным шагом branch-diff workflow;
   - возможны конфликты с документацией текущей ветки.
4. Получить явное подтверждение пользователя.

## 14. Проверки и предупреждения

### 14.1. Перед генерацией

- refs существуют локально;
- `openspec/<service-name>/` есть хотя бы в одном ref;
- diff по master root не пустой;
- manifest найден или явно подтвержден fallback без manifest;
- change directory не конфликтует с существующей.

### 14.2. Во время генерации

- все changed files учтены;
- deleted files не потеряны;
- renamed files не считаются одновременно delete+add, если git распознал rename;
- binary files отражены в `change.md`;
- context files выбраны через manifest;
- stale manifest снижает confidence.

### 14.3. После генерации

- `change.md` содержит diff metadata;
- `change.md` содержит changed files и context files;
- нет пустых разделов кроме `Нет изменений.`;
- есть acceptance criteria;
- есть open questions, если мотивация не выводится из документации;
- downstream skills понимают `Spec update mode: branch-diff`.

## 15. Edge cases

| Ситуация | Поведение |
|---|---|
| `base_ref` не существует | Блок, попросить локально подготовить ref |
| `analyst_ref` не существует | Блок, попросить локально подготовить ref |
| Diff пустой | Не создавать `change.md`, сообщить что изменений master-spec нет |
| Master root есть только в analyst_ref | Это ADDED master specification, генерировать change как добавление требований |
| Master root есть только в base_ref | Это удаление master specification, требовать подтверждение |
| Manifest отсутствует | Предупредить, предложить init, fallback только по подтверждению |
| Manifest изменен в diff | Читать новую версию из analyst_ref, old version использовать для сравнения |
| Удален документ с зависимостями | Проверить related/depends_on и зафиксировать impact |
| Binary changed | Включить как affected artifact, искать текстовый контекст рядом |
| Текущая ветка разработчика не содержит docs аналитика | Это допустимо; читать через `git show analyst_ref:path` |
| Ветка аналитика изменилась после генерации change | Verify step должен обнаружить несовпадение diff metadata |

## 16. Критерии готовности шага 2

Шаг 2 считается реализованным, если:

1. Есть documented workflow "change from branch diff".
2. Есть skill `openspec-change-from-diff` или обновленный `openspec-propose` с отдельным diff mode.
3. Генерация использует `git diff <base_ref>...<analyst_ref> -- openspec/<service>/`.
4. Генерация не делает checkout base и analyst веток.
5. Генерация работает из любой текущей ветки пользователя.
6. Все added/modified/deleted/renamed files master root попадают в анализ.
7. Контекст выбирается через manifest, а не только через patch.
8. `change.md` содержит diff metadata, changed files, context files и полноценные аналитические разделы.
9. `openspec-design` и `openspec-implement` работают с diff-based change.
10. Финальный `apply-change` для diff-based change по умолчанию не мержит документы, а выполняет проверки и предупреждения.
11. Explicit apply возможен только после clean status и явного подтверждения пользователя.
