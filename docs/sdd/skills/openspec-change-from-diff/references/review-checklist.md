# Review checklist для openspec-change-from-diff

## 1. Pre-generation

- [ ] `service` определен и не является reserved name.
- [ ] `change_name` в kebab-case.
- [ ] `base_ref` существует локально.
- [ ] `analyst_ref` существует локально.
- [ ] Не выполнялись `git fetch`, `git pull`, checkout base/analyst веток.
- [ ] `openspec/<service>/` существует хотя бы в одном ref.
- [ ] Diff по `openspec/<service>/` не пустой.
- [ ] Для `three-dot` записан merge-base.
- [ ] Если root существует только в base ref, получено подтверждение удаления.

## 2. Diff artifacts

- [ ] `.spec-diff/refs.txt` содержит refs, SHA, mode, range и command.
- [ ] `.spec-diff/name-status.txt` записан.
- [ ] `.spec-diff/stat.txt` записан.
- [ ] `.spec-diff/patch.diff` записан.
- [ ] `.spec-diff/changed-files.yaml` содержит все changed files.
- [ ] `.spec-diff/context-files.yaml` содержит manifest source и reasons.
- [ ] Artifacts не содержат полные копии веток.

## 3. Changed files

- [ ] `added` файлы прочитаны из `analyst_ref`.
- [ ] `deleted` файлы прочитаны из `base_ref`.
- [ ] `modified` файлы прочитаны из обоих refs.
- [ ] `renamed` файлы имеют `old_path` и `path`.
- [ ] `copied` файлы имеют lineage, если git распознал copy.
- [ ] `typechanged` файлы проверены отдельно.
- [ ] Binary files отражены в artifacts и `change.md`.

## 4. Manifest context

- [ ] Manifest прочитан из `analyst_ref` или fallback в `base_ref` зафиксирован.
- [ ] Отсутствие manifest явно зафиксировано и подтверждено пользователем.
- [ ] `depends_on` и `related_files` учтены.
- [ ] Shared `entities`, `integrations`, `endpoints`, `events` учтены.
- [ ] `read_priority=high` документы учтены.
- [ ] Stale manifest warnings отражены.

## 5. Structured analysis roles

Для большого diff или `thoroughness=full` проверь YAML outputs:

```yaml
role: diff-scope|business-impact|contract-impact|data-impact|quality-impact|consistency-review
summary: ""
changed_files: []
context_files: []
findings:
  - type: added|modified|removed|risk|question|contradiction
    source: ""
    detail: ""
impact:
  business: []
  contracts: []
  data: []
  quality: []
open_questions: []
confidence: high|medium|low
```

Дедупликация:

- одинаковые findings объединяются по `type + source + detail`;
- противоречия не удаляются, а переносятся в risks/open questions;
- роли не редактируют `change.md`.

## 6. change.md

- [ ] Header содержит `Spec update mode: branch-diff`.
- [ ] Header содержит base/analyst refs, diff mode, merge-base и diff command.
- [ ] Раздел `## 0. Источники изменения` заполнен.
- [ ] Changed files table содержит все файлы из YAML.
- [ ] Context files table содержит reasons.
- [ ] 16 аналитических разделов сохранены.
- [ ] Раздел 7А сохранен.
- [ ] Раздел 17 заполнен.
- [ ] Незатронутые разделы содержат ровно `Нет изменений.`.
- [ ] Patch не скопирован напрямую как основной текст change.
- [ ] Acceptance criteria есть и проверяемы.

## 7. Downstream

- [ ] `openspec-design` сможет прочитать `.spec-diff/changed-files.yaml`.
- [ ] `openspec-implement` не будет требовать final apply-change.
- [ ] `openspec-apply-change` для branch-diff будет выполнять verify, а не merge.
- [ ] Explicit apply не описан как default path.
