# Review checklist для openspec-change-from-diff

Сначала проверь профиль lite/full и mode. Полная структура обязательна только для full. Для document-copies проверь schema v3 / ai-strict, доступные Git descriptors, единственный content и результат spec_review.py check, включая совпадение change_sha256 из manifest с текущими требованиями. CH-ID связан с генерируемым review. Для three-dot baseline — merge-base, не tip base_ref. write_diff_artifacts=false не отключает обязательный .spec-copy.

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
- [ ] `deleted` файлы прочитаны из effective base SHA (merge-base для three-dot).
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

- [ ] Header содержит выбранный `Spec update mode` и `Change profile`.
- [ ] Header содержит base/analyst refs, diff mode, merge-base и diff command.
- [ ] Раздел `## 0. Источники изменения` заполнен.
- [ ] Changed files table содержит все файлы из YAML.
- [ ] Context files table содержит reasons.
- [ ] В full сохранены 16 аналитических разделов и 7А, заполнен 17; в lite используется краткий шаблон с источниками diff.
- [ ] Незатронутые разделы full содержат ровно `Нет изменений.`; lite их опускает.
- [ ] Patch не скопирован напрямую как основной текст change.
- [ ] Acceptance criteria есть и проверяемы.

## 7. Downstream

- [ ] `openspec-design` сможет прочитать `.spec-diff/changed-files.yaml`.
- [ ] Для document-copies после implement предусмотрены AI-анализ трёх версий, проверка/запись ограниченных правок и refresh; для legacy branch-diff остаётся verify.
- [ ] `openspec-apply-change` для branch-diff будет выполнять verify, а не merge.
- [ ] Legacy verify не выдаётся за фактическое обновление текущего master.
