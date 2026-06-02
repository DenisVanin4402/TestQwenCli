# Branch-diff workflow

Этот reference фиксирует git-семантику генерации `change.md` из двух refs.

## 1. Инварианты

- Разработчик может находиться в любой текущей ветке.
- Сравниваются только локальные refs.
- Автоматические `git fetch`, `git pull`, checkout base/analyst веток запрещены.
- Файлы из refs читаются через git object database: `git show <ref>:<path>`.
- Pathspec master-spec root всегда указывается после `--`.
- Artifacts сохраняют diff, metadata и summaries, но не полные копии веток.

## 2. Проверка refs

Команды одинаково понятны в PowerShell и POSIX shell:

```bash
git rev-parse --verify <base_ref>
git rev-parse --verify <analyst_ref>
```

Если команда возвращает ошибку, ref отсутствует локально. Skill должен остановиться и попросить пользователя подготовить ref вручную.

Для записи metadata используй SHA:

```bash
git rev-parse <base_ref>
git rev-parse <analyst_ref>
```

## 3. Проверка root

Master-spec root:

```text
openspec/<service>/
```

Проверка наличия root в ref:

```bash
git cat-file -e <ref>:openspec/<service>/
```

Допустимые ситуации:

| Ситуация | Поведение |
|---|---|
| Root есть в обоих refs | Обычный diff |
| Root есть только в `analyst_ref` | Change описывает добавление master specification |
| Root есть только в `base_ref` | Потенциальное удаление master specification, нужно подтверждение |
| Root отсутствует в обоих refs | Блок: service/root выбран неверно |

## 4. Three-dot по умолчанию

Three-dot показывает изменения ветки аналитика от merge-base с базовой веткой:

```bash
git merge-base <base_ref> <analyst_ref>
git diff --name-status --find-renames <base_ref>...<analyst_ref> -- openspec/<service>/
git diff --stat <base_ref>...<analyst_ref> -- openspec/<service>/
git diff --find-renames --find-copies <base_ref>...<analyst_ref> -- openspec/<service>/
```

Это default, потому что аналитик обычно создает ветку от базовой и меняет master spec.

## 5. Two-dot как explicit override

Two-dot сравнивает снимки двух refs напрямую:

```bash
git diff --name-status --find-renames <base_ref>..<analyst_ref> -- openspec/<service>/
git diff --stat <base_ref>..<analyst_ref> -- openspec/<service>/
git diff --find-renames --find-copies <base_ref>..<analyst_ref> -- openspec/<service>/
```

Используй только если пользователь явно выбрал `diff_mode=two-dot`.

## 6. Diff artifacts

Если `write_diff_artifacts=true`, создай:

```text
openspec/changes/<change-name>/.spec-diff/
  refs.txt
  name-status.txt
  stat.txt
  patch.diff
  changed-files.yaml
  context-files.yaml
```

`refs.txt` содержит:

```text
service: <service>
root: openspec/<service>/
base_ref: <base_ref>
base_sha: <sha>
analyst_ref: <analyst_ref>
analyst_sha: <sha>
diff_mode: three-dot|two-dot
merge_base: <sha|null>
range: <base_ref>...<analyst_ref>
diff_command: git diff --find-renames --find-copies <range> -- openspec/<service>/
generated_at: <ISO-8601>
```

## 7. Changed files YAML

Нормализованный формат:

```yaml
service: <service>
base_ref: <base_ref>
base_sha: <sha>
analyst_ref: <analyst_ref>
analyst_sha: <sha>
diff_mode: three-dot
merge_base: <sha>
root: openspec/<service>/
files:
  - status: modified
    path: openspec/<service>/workflow/order-flow.md
    old_path: null
    binary: false
    extension: .md
```

Все changed files master root попадают в YAML независимо от расширения.

## 8. Чтение содержимого

Команды:

```bash
git show <base_ref>:<path>
git show <analyst_ref>:<path>
```

Не использовать:

```bash
git checkout <base_ref>
git checkout <analyst_ref>
git checkout <ref> -- openspec/<service>/
```

Исключение для последней команды существует только в `openspec-apply-change` explicit apply override после clean status и явного подтверждения пользователя.

## 9. Empty diff

Если `name-status.txt` пустой, `change.md` не создается. Сообщи:

```text
По openspec/<service>/ между <base_ref> и <analyst_ref> нет изменений.
```
