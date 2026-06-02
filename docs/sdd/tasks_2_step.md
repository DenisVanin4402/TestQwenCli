# Tasks 2. Реализация change.md из git diff master-spec веток

> **Связанный план**: `docs/sdd/plan_2_step.md`
>
> **Зависимость**: сначала выполнить `tasks_1_step.md`, чтобы master specification была folder-based и имела manifest.

## 1. Документирование diff-based workflow

- [x] 1.1 Обновить `docs/sdd/SDD_DETAILS.md`: добавить сценарий "аналитик меняет master-spec в ветке, разработчик генерирует change из diff".
- [x] 1.2 Описать, что разработчик может находиться в любой текущей ветке.
- [x] 1.3 Описать, что сравниваются только локальные refs.
- [x] 1.4 Зафиксировать запрет на автоматический `git fetch`, `git pull`, checkout base/analyst веток.
- [x] 1.5 Описать default diff mode: `git diff <base_ref>...<analyst_ref> -- openspec/<service>/`.
- [x] 1.6 Описать explicit override для two-dot diff.
- [x] 1.7 Описать, что финальный `apply-change` по умолчанию заменяется verify-проверкой.

## 2. Новый skill `openspec-change-from-diff`

- [x] 2.1 Создать директорию `docs/sdd/skills/openspec-change-from-diff/`.
- [x] 2.2 Создать `docs/sdd/skills/openspec-change-from-diff/SKILL.md`.
- [x] 2.3 Описать trigger phrases для генерации change из веток.
- [x] 2.4 Описать обязательные inputs: `service`, `base_ref`, `analyst_ref`, `change_name`.
- [x] 2.5 Описать optional inputs: `diff_mode`, `approval`, `thoroughness`, `include_context`, `allow_binary`, `write_diff_artifacts`.
- [x] 2.6 Описать default values.
- [x] 2.7 Описать output: путь к `change.md`, diff metadata, warnings, next steps.

## 3. Git validation

- [x] 3.1 Добавить шаг `git rev-parse --verify <base_ref>`.
- [x] 3.2 Добавить шаг `git rev-parse --verify <analyst_ref>`.
- [x] 3.3 Добавить проверку, что `openspec/<service>/` существует хотя бы в одном ref.
- [x] 3.4 Добавить проверку, что diff по `openspec/<service>/` не пустой.
- [x] 3.5 Для `diff_mode=three-dot` добавить `git merge-base <base_ref> <analyst_ref>`.
- [x] 3.6 Записать base SHA, analyst SHA, merge-base SHA в metadata.
- [x] 3.7 Запретить fallback на network или fetch.

## 4. Diff artifacts

- [x] 4.1 Создать `.spec-diff/` внутри `openspec/changes/<change-name>/`.
- [x] 4.2 Записать `.spec-diff/refs.txt`.
- [x] 4.3 Записать `.spec-diff/name-status.txt`.
- [x] 4.4 Записать `.spec-diff/stat.txt`.
- [x] 4.5 Записать `.spec-diff/patch.diff`.
- [x] 4.6 Сформировать `.spec-diff/changed-files.yaml`.
- [x] 4.7 Сформировать `.spec-diff/context-files.yaml`.
- [x] 4.8 Проверить, что artifacts не содержат локальные копии обеих веток.

## 5. Changed files analysis

- [x] 5.1 Использовать `git diff --name-status --find-renames`.
- [x] 5.2 Нормализовать statuses: added, modified, deleted, renamed, copied, typechanged.
- [x] 5.3 Для renamed сохранять `old_path` и `path`.
- [x] 5.4 Для deleted читать old content из `base_ref`.
- [x] 5.5 Для added читать new content из `analyst_ref`.
- [x] 5.6 Для modified читать old и new content.
- [x] 5.7 Для binary changed фиксировать artifact и искать текстовый контекст рядом.
- [x] 5.8 Все changed files в master root должны попасть в `change.md`.

## 6. Чтение файлов через git object database

- [x] 6.1 Описать команду `git show <ref>:<path>` для old/new content.
- [x] 6.2 Добавить обработку отсутствующего файла в одном из refs.
- [x] 6.3 Добавить ограничение размера чтения и стратегию summarization для больших файлов.
- [x] 6.4 Добавить правило: не делать checkout base/analyst refs.
- [x] 6.5 Добавить правило: не создавать полную копию master-spec folder из refs.

## 7. Context selection через manifest

- [x] 7.1 Читать manifest из `analyst_ref`.
- [x] 7.2 Если manifest отсутствует в `analyst_ref`, читать manifest из `base_ref`.
- [x] 7.3 Если manifest отсутствует в обоих refs, остановиться или продолжить только после подтверждения пользователя.
- [x] 7.4 Для каждого changed file найти `depends_on`.
- [x] 7.5 Для каждого changed file найти `related_files`.
- [x] 7.6 Найти файлы с общими `entities`.
- [x] 7.7 Найти файлы с общими `integrations`.
- [x] 7.8 Найти файлы с общими `endpoints`.
- [x] 7.9 Найти файлы с общими `events`.
- [x] 7.10 Добавить `read_priority=high` документы.
- [x] 7.11 Записать выбранный контекст в `.spec-diff/context-files.yaml`.

## 8. Structured analysis references

- [x] 8.1 Создать `docs/sdd/skills/openspec-change-from-diff/references/diff-workflow.md`.
- [x] 8.2 Создать `docs/sdd/skills/openspec-change-from-diff/references/context-selection.md`.
- [x] 8.3 Создать `docs/sdd/skills/openspec-change-from-diff/references/change-generation.md`.
- [x] 8.4 Создать `docs/sdd/skills/openspec-change-from-diff/references/review-checklist.md`.
- [x] 8.5 Описать роли анализа: `diff-scope`, `business-impact`, `contract-impact`, `data-impact`, `quality-impact`, `consistency-review`.
- [x] 8.6 Описать YAML output для каждой роли.
- [x] 8.7 Описать дедупликацию результатов ролей.

## 9. Обновление change template

- [x] 9.1 Обновить `docs/sdd/skills/openspec-propose/templates/change.md` или создать отдельный template для diff-based change.
- [x] 9.2 Добавить header fields: `Мастер-спецификация`, `Manifest`, `Spec update mode`, `Base ref`, `Analyst ref`, `Diff mode`, `Merge base`, `Diff command`.
- [x] 9.3 Добавить `## 0. Источники изменения`.
- [x] 9.4 Добавить таблицу changed files.
- [x] 9.5 Добавить таблицу context files.
- [x] 9.6 Добавить `## 17. Уверенность и вопросы`.
- [x] 9.7 Проверить, что существующие 16 аналитических разделов сохранены.

## 10. Generation rules

- [x] 10.1 Новые файлы master spec преобразовывать в ADDED требования.
- [x] 10.2 Измененные файлы master spec преобразовывать в MODIFIED требования.
- [x] 10.3 Удаленные файлы master spec преобразовывать в REMOVED требования.
- [x] 10.4 Renamed файлы анализировать как rename + возможные content changes.
- [x] 10.5 Не копировать patch напрямую в `change.md`.
- [x] 10.6 Для незатронутых разделов писать ровно `Нет изменений.`.
- [x] 10.7 Генерировать acceptance criteria из changed + context docs.
- [x] 10.8 Если мотивация не выводится надежно, добавить вопрос в раздел 17.
- [x] 10.9 Если manifest stale или есть binary-only изменения, снижать confidence.

## 11. Status policy

- [x] 11.1 Добавить input `approval=draft|approved`.
- [x] 11.2 При `approval=draft` ставить статус `На согласовании`.
- [x] 11.3 При `approval=approved` ставить статус `Согласовано`.
- [x] 11.4 Если approval не указан, использовать `На согласовании`.
- [x] 11.5 Объяснить пользователю, что для немедленного design/implement нужен согласованный change.

## 12. Обновление `openspec-design`

- [x] 12.1 Научить skill читать `Spec update mode: branch-diff`.
- [x] 12.2 Если есть `.spec-diff/changed-files.yaml`, читать его перед проектированием.
- [x] 12.3 Если текущая ветка не содержит master-spec docs аналитика, читать документы через `git show <analyst_ref>:<path>`.
- [x] 12.4 Добавить diff metadata в раздел "Источники требований" design.
- [x] 12.5 Не требовать финального `apply-change` как next step.

## 13. Обновление `openspec-implement`

- [x] 13.1 После завершения tasks определять `Spec update mode`.
- [x] 13.2 Для `branch-diff` предлагать verify master-spec source вместо apply.
- [x] 13.3 В итоговом отчете указывать, что master-spec изменения уже находятся в `analyst_ref`.
- [x] 13.4 Не менять статусы `change.md` вне существующих правил implement, кроме разрешенного перехода в `В реализации`.

## 14. Обновление `openspec-apply-change`

- [x] 14.1 Добавить отдельную ветку поведения для `Spec update mode: branch-diff`.
- [x] 14.2 В default mode не изменять master-spec документы.
- [x] 14.3 Проверять refs и повторять diff summary.
- [x] 14.4 Сравнивать current diff metadata с `.spec-diff/changed-files.yaml`.
- [x] 14.5 Проверять наличие master-spec изменений в `analyst_ref`.
- [x] 14.6 Если нужно проверить текущую ветку, использовать `git diff --quiet <analyst_ref> -- openspec/<service>/`.
- [x] 14.7 Если текущая ветка не содержит spec changes, выводить предупреждение, а не применять автоматически.
- [x] 14.8 Реализовать explicit apply override только после clean status и подтверждения пользователя.
- [x] 14.9 Для explicit apply использовать `git checkout <analyst_ref> -- openspec/<service>/`.

## 15. Обновление `openspec-archive-change`

- [x] 15.1 Проверить, что archive не требует single-file spec.
- [x] 15.2 Добавить в output archive сохранение diff metadata как части change history.
- [x] 15.3 Убедиться, что `.spec-diff/` архивируется вместе с change.

## 16. README и teach

- [x] 16.1 Добавить `openspec-change-from-diff` в `docs/sdd/skills/README.md`.
- [x] 16.2 Добавить `openspec-change-from-diff` в `openspec-teach`.
- [x] 16.3 Обновить lifecycle diagram: `init-master-spec -> change-from-diff -> design -> implement -> verify/archive`.
- [x] 16.4 Добавить mapping пользовательских запросов.
- [x] 16.5 Добавить предупреждение: refs должны быть локальными.

## 17. Проверка команд git

- [x] 17.1 Проверить примеры команд на Windows PowerShell.
- [x] 17.2 Проверить примеры команд на POSIX shell.
- [x] 17.3 Избегать process substitution и shell-specific конструкций.
- [x] 17.4 В документации использовать команды, которые одинаково понятны в git CLI.
- [x] 17.5 Проверить, что pathspec `-- openspec/<service>/` всегда указан после `--`.

## 18. Acceptance criteria

- [x] 18.1 Разработчик в любой текущей ветке может создать change из двух локальных refs.
- [x] 18.2 Генерация использует git diff, а не checkout двух веток.
- [x] 18.3 Все изменения master-spec root попадают в анализ.
- [x] 18.4 `change.md` содержит не только patch summary, но и контекст из manifest/related docs.
- [x] 18.5 `change.md` содержит base/analyst refs, diff mode, merge-base, changed files, context files.
- [x] 18.6 `openspec-design` работает с diff-based change.
- [x] 18.7 `openspec-implement` не требует final apply-change.
- [x] 18.8 `openspec-apply-change` для branch-diff по умолчанию выполняет проверки и предупреждения, но не мержит документы.
- [x] 18.9 Explicit apply возможен только с clean master-spec root и явным подтверждением.
