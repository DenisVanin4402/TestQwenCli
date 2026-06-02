# Tasks 2. Реализация change.md из git diff master-spec веток

> **Связанный план**: `docs/sdd/plan_2_step.md`
>
> **Зависимость**: сначала выполнить `tasks_1_step.md`, чтобы master specification была folder-based и имела manifest.

## 1. Документирование diff-based workflow

- [ ] 1.1 Обновить `docs/sdd/SDD_DETAILS.md`: добавить сценарий "аналитик меняет master-spec в ветке, разработчик генерирует change из diff".
- [ ] 1.2 Описать, что разработчик может находиться в любой текущей ветке.
- [ ] 1.3 Описать, что сравниваются только локальные refs.
- [ ] 1.4 Зафиксировать запрет на автоматический `git fetch`, `git pull`, checkout base/analyst веток.
- [ ] 1.5 Описать default diff mode: `git diff <base_ref>...<analyst_ref> -- openspec/<service>/`.
- [ ] 1.6 Описать explicit override для two-dot diff.
- [ ] 1.7 Описать, что финальный `apply-change` по умолчанию заменяется verify-проверкой.

## 2. Новый skill `openspec-change-from-diff`

- [ ] 2.1 Создать директорию `docs/sdd/skills/openspec-change-from-diff/`.
- [ ] 2.2 Создать `docs/sdd/skills/openspec-change-from-diff/SKILL.md`.
- [ ] 2.3 Описать trigger phrases для генерации change из веток.
- [ ] 2.4 Описать обязательные inputs: `service`, `base_ref`, `analyst_ref`, `change_name`.
- [ ] 2.5 Описать optional inputs: `diff_mode`, `approval`, `thoroughness`, `include_context`, `allow_binary`, `write_diff_artifacts`.
- [ ] 2.6 Описать default values.
- [ ] 2.7 Описать output: путь к `change.md`, diff metadata, warnings, next steps.

## 3. Git validation

- [ ] 3.1 Добавить шаг `git rev-parse --verify <base_ref>`.
- [ ] 3.2 Добавить шаг `git rev-parse --verify <analyst_ref>`.
- [ ] 3.3 Добавить проверку, что `openspec/<service>/` существует хотя бы в одном ref.
- [ ] 3.4 Добавить проверку, что diff по `openspec/<service>/` не пустой.
- [ ] 3.5 Для `diff_mode=three-dot` добавить `git merge-base <base_ref> <analyst_ref>`.
- [ ] 3.6 Записать base SHA, analyst SHA, merge-base SHA в metadata.
- [ ] 3.7 Запретить fallback на network или fetch.

## 4. Diff artifacts

- [ ] 4.1 Создать `.spec-diff/` внутри `openspec/changes/<change-name>/`.
- [ ] 4.2 Записать `.spec-diff/refs.txt`.
- [ ] 4.3 Записать `.spec-diff/name-status.txt`.
- [ ] 4.4 Записать `.spec-diff/stat.txt`.
- [ ] 4.5 Записать `.spec-diff/patch.diff`.
- [ ] 4.6 Сформировать `.spec-diff/changed-files.yaml`.
- [ ] 4.7 Сформировать `.spec-diff/context-files.yaml`.
- [ ] 4.8 Проверить, что artifacts не содержат локальные копии обеих веток.

## 5. Changed files analysis

- [ ] 5.1 Использовать `git diff --name-status --find-renames`.
- [ ] 5.2 Нормализовать statuses: added, modified, deleted, renamed, copied, typechanged.
- [ ] 5.3 Для renamed сохранять `old_path` и `path`.
- [ ] 5.4 Для deleted читать old content из `base_ref`.
- [ ] 5.5 Для added читать new content из `analyst_ref`.
- [ ] 5.6 Для modified читать old и new content.
- [ ] 5.7 Для binary changed фиксировать artifact и искать текстовый контекст рядом.
- [ ] 5.8 Все changed files в master root должны попасть в `change.md`.

## 6. Чтение файлов через git object database

- [ ] 6.1 Описать команду `git show <ref>:<path>` для old/new content.
- [ ] 6.2 Добавить обработку отсутствующего файла в одном из refs.
- [ ] 6.3 Добавить ограничение размера чтения и стратегию summarization для больших файлов.
- [ ] 6.4 Добавить правило: не делать checkout base/analyst refs.
- [ ] 6.5 Добавить правило: не создавать полную копию master-spec folder из refs.

## 7. Context selection через manifest

- [ ] 7.1 Читать manifest из `analyst_ref`.
- [ ] 7.2 Если manifest отсутствует в `analyst_ref`, читать manifest из `base_ref`.
- [ ] 7.3 Если manifest отсутствует в обоих refs, остановиться или продолжить только после подтверждения пользователя.
- [ ] 7.4 Для каждого changed file найти `depends_on`.
- [ ] 7.5 Для каждого changed file найти `related_files`.
- [ ] 7.6 Найти файлы с общими `entities`.
- [ ] 7.7 Найти файлы с общими `integrations`.
- [ ] 7.8 Найти файлы с общими `endpoints`.
- [ ] 7.9 Найти файлы с общими `events`.
- [ ] 7.10 Добавить `read_priority=high` документы.
- [ ] 7.11 Записать выбранный контекст в `.spec-diff/context-files.yaml`.

## 8. Structured analysis references

- [ ] 8.1 Создать `docs/sdd/skills/openspec-change-from-diff/references/diff-workflow.md`.
- [ ] 8.2 Создать `docs/sdd/skills/openspec-change-from-diff/references/context-selection.md`.
- [ ] 8.3 Создать `docs/sdd/skills/openspec-change-from-diff/references/change-generation.md`.
- [ ] 8.4 Создать `docs/sdd/skills/openspec-change-from-diff/references/review-checklist.md`.
- [ ] 8.5 Описать роли анализа: `diff-scope`, `business-impact`, `contract-impact`, `data-impact`, `quality-impact`, `consistency-review`.
- [ ] 8.6 Описать YAML output для каждой роли.
- [ ] 8.7 Описать дедупликацию результатов ролей.

## 9. Обновление change template

- [ ] 9.1 Обновить `docs/sdd/skills/openspec-propose/templates/change.md` или создать отдельный template для diff-based change.
- [ ] 9.2 Добавить header fields: `Мастер-спецификация`, `Manifest`, `Spec update mode`, `Base ref`, `Analyst ref`, `Diff mode`, `Merge base`, `Diff command`.
- [ ] 9.3 Добавить `## 0. Источники изменения`.
- [ ] 9.4 Добавить таблицу changed files.
- [ ] 9.5 Добавить таблицу context files.
- [ ] 9.6 Добавить `## 17. Уверенность и вопросы`.
- [ ] 9.7 Проверить, что существующие 16 аналитических разделов сохранены.

## 10. Generation rules

- [ ] 10.1 Новые файлы master spec преобразовывать в ADDED требования.
- [ ] 10.2 Измененные файлы master spec преобразовывать в MODIFIED требования.
- [ ] 10.3 Удаленные файлы master spec преобразовывать в REMOVED требования.
- [ ] 10.4 Renamed файлы анализировать как rename + возможные content changes.
- [ ] 10.5 Не копировать patch напрямую в `change.md`.
- [ ] 10.6 Для незатронутых разделов писать ровно `Нет изменений.`.
- [ ] 10.7 Генерировать acceptance criteria из changed + context docs.
- [ ] 10.8 Если мотивация не выводится надежно, добавить вопрос в раздел 17.
- [ ] 10.9 Если manifest stale или есть binary-only изменения, снижать confidence.

## 11. Status policy

- [ ] 11.1 Добавить input `approval=draft|approved`.
- [ ] 11.2 При `approval=draft` ставить статус `На согласовании`.
- [ ] 11.3 При `approval=approved` ставить статус `Согласовано`.
- [ ] 11.4 Если approval не указан, использовать `На согласовании`.
- [ ] 11.5 Объяснить пользователю, что для немедленного design/implement нужен согласованный change.

## 12. Обновление `openspec-design`

- [ ] 12.1 Научить skill читать `Spec update mode: branch-diff`.
- [ ] 12.2 Если есть `.spec-diff/changed-files.yaml`, читать его перед проектированием.
- [ ] 12.3 Если текущая ветка не содержит master-spec docs аналитика, читать документы через `git show <analyst_ref>:<path>`.
- [ ] 12.4 Добавить diff metadata в раздел "Источники требований" design.
- [ ] 12.5 Не требовать финального `apply-change` как next step.

## 13. Обновление `openspec-implement`

- [ ] 13.1 После завершения tasks определять `Spec update mode`.
- [ ] 13.2 Для `branch-diff` предлагать verify master-spec source вместо apply.
- [ ] 13.3 В итоговом отчете указывать, что master-spec изменения уже находятся в `analyst_ref`.
- [ ] 13.4 Не менять статусы `change.md` вне существующих правил implement, кроме разрешенного перехода в `В реализации`.

## 14. Обновление `openspec-apply-change`

- [ ] 14.1 Добавить отдельную ветку поведения для `Spec update mode: branch-diff`.
- [ ] 14.2 В default mode не изменять master-spec документы.
- [ ] 14.3 Проверять refs и повторять diff summary.
- [ ] 14.4 Сравнивать current diff metadata с `.spec-diff/changed-files.yaml`.
- [ ] 14.5 Проверять наличие master-spec изменений в `analyst_ref`.
- [ ] 14.6 Если нужно проверить текущую ветку, использовать `git diff --quiet <analyst_ref> -- openspec/<service>/`.
- [ ] 14.7 Если текущая ветка не содержит spec changes, выводить предупреждение, а не применять автоматически.
- [ ] 14.8 Реализовать explicit apply override только после clean status и подтверждения пользователя.
- [ ] 14.9 Для explicit apply использовать `git checkout <analyst_ref> -- openspec/<service>/`.

## 15. Обновление `openspec-archive-change`

- [ ] 15.1 Проверить, что archive не требует single-file spec.
- [ ] 15.2 Добавить в output archive сохранение diff metadata как части change history.
- [ ] 15.3 Убедиться, что `.spec-diff/` архивируется вместе с change.

## 16. README и teach

- [ ] 16.1 Добавить `openspec-change-from-diff` в `docs/sdd/skills/README.md`.
- [ ] 16.2 Добавить `openspec-change-from-diff` в `openspec-teach`.
- [ ] 16.3 Обновить lifecycle diagram: `init-master-spec -> change-from-diff -> design -> implement -> verify/archive`.
- [ ] 16.4 Добавить mapping пользовательских запросов.
- [ ] 16.5 Добавить предупреждение: refs должны быть локальными.

## 17. Проверка команд git

- [ ] 17.1 Проверить примеры команд на Windows PowerShell.
- [ ] 17.2 Проверить примеры команд на POSIX shell.
- [ ] 17.3 Избегать process substitution и shell-specific конструкций.
- [ ] 17.4 В документации использовать команды, которые одинаково понятны в git CLI.
- [ ] 17.5 Проверить, что pathspec `-- openspec/<service>/` всегда указан после `--`.

## 18. Acceptance criteria

- [ ] 18.1 Разработчик в любой текущей ветке может создать change из двух локальных refs.
- [ ] 18.2 Генерация использует git diff, а не checkout двух веток.
- [ ] 18.3 Все изменения master-spec root попадают в анализ.
- [ ] 18.4 `change.md` содержит не только patch summary, но и контекст из manifest/related docs.
- [ ] 18.5 `change.md` содержит base/analyst refs, diff mode, merge-base, changed files, context files.
- [ ] 18.6 `openspec-design` работает с diff-based change.
- [ ] 18.7 `openspec-implement` не требует final apply-change.
- [ ] 18.8 `openspec-apply-change` для branch-diff по умолчанию выполняет проверки и предупреждения, но не мержит документы.
- [ ] 18.9 Explicit apply возможен только с clean master-spec root и явным подтверждением.
