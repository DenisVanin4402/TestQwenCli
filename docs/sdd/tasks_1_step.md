# Tasks 1. Реализация folder-based master specification

> **Связанный план**: `docs/sdd/plan_1_step.md`
>
> **Цель**: заменить single-file `service-spec.md` на master-spec папку `openspec/<service-name>/` и добавить инициализацию manifest/navigation.

## 1. Обновление общей документации

- [x] 1.1 Обновить `docs/sdd/SDD_DETAILS.md`: заменить модель `openspec/specs/<service>/<service>.md` на `openspec/<service>/`.
- [x] 1.2 Обновить раздел layout: добавить `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`, `_sdd/stale-files.md`.
- [x] 1.3 Обновить жизненный цикл: заменить `new-spec` на `init-master-spec`.
- [x] 1.4 Обновить описание аналитического слоя: master specification теперь папка документов, а не один Markdown-файл.
- [x] 1.5 Обновить раздел рисков: stale manifest, бинарные файлы, reserved names, большие папки.
- [x] 1.6 Обновить критерии качества: manifest checks, navigation checks, coverage checks.

## 2. Новый skill `openspec-init-master-spec`

- [x] 2.1 Создать директорию `docs/sdd/skills/openspec-init-master-spec/`.
- [x] 2.2 Создать `docs/sdd/skills/openspec-init-master-spec/SKILL.md`.
- [x] 2.3 Описать trigger phrases для подключения папки как master specification.
- [x] 2.4 Описать input: `service`, `root`, `thoroughness`, `include_patterns`, `exclude_patterns`, `update_mode`.
- [x] 2.5 Описать reserved names: `changes`, `archive`, `_sdd`, `_system`.
- [x] 2.6 Описать init алгоритм для новой папки.
- [x] 2.7 Описать refresh алгоритм для уже инициализированной папки.
- [x] 2.8 Описать правила работы с бинарными файлами и нечитаемыми артефактами.
- [x] 2.9 Описать self-review manifest/navigation/coverage.
- [x] 2.10 Описать финальный output skill: созданные файлы, статистика, warnings, next steps.

## 3. Templates для `_sdd`

- [x] 3.1 Создать `docs/sdd/skills/openspec-init-master-spec/templates/manifest.yaml`.
- [x] 3.2 Создать `docs/sdd/skills/openspec-init-master-spec/templates/navigation.md`.
- [x] 3.3 Создать `docs/sdd/skills/openspec-init-master-spec/templates/coverage.md`.
- [x] 3.4 Создать `docs/sdd/skills/openspec-init-master-spec/templates/stale-files.md`.
- [x] 3.5 Проверить, что шаблоны не содержат примеров, которые агент может скопировать в итоговый файл как реальные данные.
- [x] 3.6 Добавить в manifest template поля `schema_version`, `service`, `root`, `generated_at`, `summary`, `files`, `reserved_paths`.
- [x] 3.7 Добавить в navigation template разделы "что читать первым", "карта по областям", "обязательные документы", "пробелы".
- [x] 3.8 Добавить в coverage template матрицу coverage по workflow/API/integrations/data/errors/security/NFR.

## 4. References для init

- [x] 4.1 Создать `docs/sdd/skills/openspec-init-master-spec/references/manifest-schema.md`.
- [x] 4.2 Создать `docs/sdd/skills/openspec-init-master-spec/references/classification-rules.md`.
- [x] 4.3 Создать `docs/sdd/skills/openspec-init-master-spec/references/refresh-workflow.md`.
- [x] 4.4 Создать `docs/sdd/skills/openspec-init-master-spec/references/review-checklist.md`.
- [x] 4.5 В `classification-rules.md` описать kind values: `overview`, `workflow`, `api`, `integration`, `data`, `state-machine`, `security`, `observability`, `nfr`, `runbook`, `diagram`, `schema`, `other`.
- [x] 4.6 В `refresh-workflow.md` описать hash mismatch, added/modified/deleted files, stale manifest.
- [x] 4.7 В `review-checklist.md` описать проверки битых ссылок, coverage gaps, read priority, stale files.

## 5. Обновление `openspec-teach`

- [x] 5.1 В `docs/sdd/skills/openspec-teach/SKILL.md` заменить структуру `openspec/specs/` на `openspec/<service>/`.
- [x] 5.2 Добавить `openspec-init-master-spec` в таблицу skills.
- [x] 5.3 Удалить рекомендацию создавать первую спецификацию через `openspec-new-spec`.
- [x] 5.4 Добавить правило: если master-spec папка есть, но `_sdd/manifest.yaml` отсутствует, следующий шаг - `openspec-init-master-spec`.
- [x] 5.5 Обновить mapping запросов пользователя.
- [x] 5.6 Обновить правила чтения: сначала `_sdd/navigation.md`, потом `_sdd/manifest.yaml`, затем релевантные документы.

## 6. Замена или удаление `openspec-new-spec`

- [x] 6.1 Принять решение: удалить `openspec-new-spec` из активного workflow или оставить как deprecated.
- [x] 6.2 Если deprecated - обновить description и явно запретить использовать для текущего framework.
- [x] 6.3 Если удаляется - убрать упоминания из README, teach, SDD_DETAILS.
- [x] 6.4 Убрать зависимость от `templates/service-spec.md` в маршрутизации.
- [x] 6.5 Проверить, что ни один skill не требует `openspec/specs/<service>/<service>.md`.

## 7. Обновление `openspec-propose`

- [x] 7.1 В `docs/sdd/skills/openspec-propose/SKILL.md` заменить "целевая спецификация" на "master-spec root".
- [x] 7.2 Обновить входные проверки: искать `openspec/<service>/_sdd/manifest.yaml`.
- [x] 7.3 Добавить fallback: если manifest отсутствует или stale, предложить `openspec-init-master-spec`.
- [x] 7.4 Обновить scope исследования: разрешить чтение master-spec docs через manifest.
- [x] 7.5 Обновить алгоритм выбора документов по tags/entities/integrations/endpoints/events/related_files.
- [x] 7.6 Обновить `templates/change.md`: добавить `Мастер-спецификация`, `Manifest`, `Spec update mode`.
- [x] 7.7 Добавить в `change.md` раздел `## 0. Источники master specification`.
- [x] 7.8 Убрать обязательную ссылку на single-file spec.

## 8. Обновление `openspec-design`

- [x] 8.1 В `docs/sdd/skills/openspec-design/SKILL.md` читать master-spec root из `change.md`.
- [x] 8.2 Добавить чтение `_sdd/navigation.md` и `_sdd/manifest.yaml`.
- [x] 8.3 Добавить выбор релевантных master-spec docs по sources из `change.md`.
- [x] 8.4 В `templates/design.md` добавить раздел "Источники требований".
- [x] 8.5 Проверить, что design может ссылаться и на документы master spec, и на кодовые сущности.

## 9. Обновление `openspec-implement`

- [x] 9.1 В `docs/sdd/skills/openspec-implement/SKILL.md` убрать предположение об обязательном финальном `apply-change`.
- [x] 9.2 Добавить чтение `Spec update mode` из `change.md`.
- [x] 9.3 В completion output предлагать следующий шаг в зависимости от mode.
- [x] 9.4 В `templates/tasks.md` добавить пункт проверки соответствия master-spec documents.

## 10. Обновление `openspec-apply-change`

- [x] 10.1 В `docs/sdd/skills/openspec-apply-change/SKILL.md` отметить, что single-file Analyst Merge больше не основной путь.
- [x] 10.2 Добавить предупреждение для folder-based docs: автоматический merge произвольных документов опасен.
- [x] 10.3 Подготовить интерфейс для будущего branch-diff verify из tasks 2.
- [x] 10.4 Убрать инструкции, которые требуют мержить `change.md` в `openspec/specs/<service>/<service>.md`.

## 11. README и skill index

- [x] 11.1 Обновить `docs/sdd/skills/README.md` в корректной UTF-8 кодировке.
- [x] 11.2 Добавить `openspec-init-master-spec` в список skills.
- [x] 11.3 Обновить lifecycle diagram.
- [x] 11.4 Убрать OpenSpec CLI/single-file wording, если он больше не соответствует процессу.

## 12. Проверка документации

- [x] 12.1 Выполнить `rg "openspec/specs|service-spec.md|<service>.md|new-spec" docs/sdd`.
- [x] 12.2 Для каждого найденного места решить: заменить, удалить или оставить как историческую заметку.
- [x] 12.3 Проверить, что все новые Markdown-файлы на русском языке.
- [x] 12.4 Проверить, что пути Windows/Unix описаны нейтрально.
- [x] 12.5 Проверить, что нет противоречия между `SDD_DETAILS.md`, README и SKILL.md.

## 13. Acceptance criteria

- [x] 13.1 Пользователь может скопировать существующую документацию в `openspec/<service>/`.
- [x] 13.2 Пользователь может запустить `openspec-init-master-spec`.
- [x] 13.3 После init появляются `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`.
- [x] 13.4 `openspec-propose` может создать `change.md` без `service-spec.md`.
- [x] 13.5 `openspec-design` может создать `design.md` и `tasks.md`, используя master-spec folder.
- [x] 13.6 В workflow больше нет требования создавать один огромный master Markdown.
