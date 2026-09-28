# Legacy folder-based apply / verify workflow

Для новых Markdown-change действует [document-copies](document-copies.md) с `Apply policy: ai-strict`: ИИ сопоставляет три версии, сохраняет независимые правки и проверяет смысловые конфликты по [строгому протоколу](ai-apply.md). Алгоритмическое слияние не используется. Ниже описана только совместимость legacy `branch-diff` и `manual-change`.

Этот файл заменяет старую механику автоматического Analyst Merge в один Markdown-файл.

## 1. Почему single-file merge больше не основной путь

Master specification теперь является папкой `openspec/<service>/`, где документы могут быть Markdown, OpenAPI, YAML, JSON Schema, SQL, PlantUML, Draw.io, PDF, изображениями и другими артефактами.

Автоматически переносить смысл `change.md` в произвольный набор таких файлов небезопасно:

- разные форматы требуют разных правил редактирования;
- часть документов может быть бинарной;
- один change может затрагивать несколько связанных файлов;
- порядок применения зависит от доменной структуры папки;
- неверный merge может создать противоречие между manifest, navigation и фактическими документами.

## 2. Два режима обновления

### `branch-diff`

Документы master specification уже изменены в ветке. Skill проверяет, что изменения из `change.md` отражены в документах, а `_sdd` navigation layer не stale.

Проверки:

- `Base ref` и `Analyst ref` существуют локально;
- для `three-dot` merge-base совпадает с metadata change;
- повторный `git diff --name-status --find-renames <range> -- openspec/<service>/` совпадает с `.spec-diff/changed-files.yaml`, если artifact есть;
- документы из `## 0. Источники master specification` имеют релевантный diff;
- измененные entities/endpoints/integrations/events отражены в документах;
- manifest hash обновлен или требуется refresh;
- coverage gaps не скрыты;
- `stale-files.md` пустой или содержит понятные manual-review пункты.

Default mode для `branch-diff` ничего не применяет в текущий worktree. Проверочные команды:

```bash
git rev-parse --verify <base_ref>
git rev-parse --verify <analyst_ref>
git diff --name-status --find-renames <base_ref>...<analyst_ref> -- openspec/<service>/
git diff --stat <base_ref>...<analyst_ref> -- openspec/<service>/
```

Если нужно проверить, что текущая ветка уже содержит master-spec документы analyst ref:

```bash
git diff --quiet <analyst_ref> -- openspec/<service>/
```

Несовпадение выводится как warning. Автоматически применять изменения нельзя.

Для explicit apply Markdown-документов сформируй document-copies из фиксированного effective base SHA и analyst SHA, проверь согласование и выполни строгий AI apply. Не используй checkout всего master root. Для three-dot baseline — merge-base. Для не-MD форматов перечисли отдельные операции и проверки; ИИ не должен угадывать содержание нечитаемых артефактов.

### `manual-change`

Пользователь явно просит внести изменения в документы master spec.

Правила:

- сначала показать список candidate documents;
- получить подтверждение, какие документы редактировать;
- править только подтвержденные документы;
- после правок запустить или предложить `openspec-init-master-spec` refresh;
- статус `Реализовано` ставить только после проверки.

## 3. Что проверять перед статусом `Реализовано`

- Код реализован и верификация прошла или невозможность верификации зафиксирована.
- Документы целевого master spec обновлены или явно не требовали изменений; наличие версии только в analyst ref недостаточно.
- `Spec update mode` известен.
- `_sdd/manifest.yaml` соответствует текущему дереву или создан `stale-files.md`.
- В `coverage.md` отражены новые gaps, если они есть.
- Нет битых `related_files` и `depends_on`.

## 4. Статус change.md

Если update проверен или применен, меняй только строку:

```md
> **Статус**: Реализовано
```

Не редактируй остальные поля `change.md`.

## 5. Что не делать

- Не создавать `.bak`-файлы.
- Не выполнять blind merge в несколько документов.
- Не применять branch-diff docs в текущий worktree без clean status и явного подтверждения.
- Не игнорировать бинарные или нечитаемые артефакты.
- Не считать отсутствие diff успешной проверкой.
- Не обновлять manifest вручную без пересчета hash.
