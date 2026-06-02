# Tasks 1. Реализация folder-based master specification

> **Связанный план**: `docs/sdd/plan_1_step.md`
>
> **Цель**: заменить single-file `service-spec.md` на master-spec папку `openspec/<service-name>/` и добавить инициализацию manifest/navigation.

## 1. Обновление общей документации

- [ ] 1.1 Обновить `docs/sdd/SDD_DETAILS.md`: заменить модель `openspec/specs/<service>/<service>.md` на `openspec/<service>/`.
- [ ] 1.2 Обновить раздел layout: добавить `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`, `_sdd/stale-files.md`.
- [ ] 1.3 Обновить жизненный цикл: заменить `new-spec` на `init-master-spec`.
- [ ] 1.4 Обновить описание аналитического слоя: master specification теперь папка документов, а не один Markdown-файл.
- [ ] 1.5 Обновить раздел рисков: stale manifest, бинарные файлы, reserved names, большие папки.
- [ ] 1.6 Обновить критерии качества: manifest checks, navigation checks, coverage checks.

## 2. Новый skill `openspec-init-master-spec`

- [ ] 2.1 Создать директорию `docs/sdd/skills/openspec-init-master-spec/`.
- [ ] 2.2 Создать `docs/sdd/skills/openspec-init-master-spec/SKILL.md`.
- [ ] 2.3 Описать trigger phrases для подключения папки как master specification.
- [ ] 2.4 Описать input: `service`, `root`, `thoroughness`, `include_patterns`, `exclude_patterns`, `update_mode`.
- [ ] 2.5 Описать reserved names: `changes`, `archive`, `_sdd`, `_system`.
- [ ] 2.6 Описать init алгоритм для новой папки.
- [ ] 2.7 Описать refresh алгоритм для уже инициализированной папки.
- [ ] 2.8 Описать правила работы с бинарными файлами и нечитаемыми артефактами.
- [ ] 2.9 Описать self-review manifest/navigation/coverage.
- [ ] 2.10 Описать финальный output skill: созданные файлы, статистика, warnings, next steps.

## 3. Templates для `_sdd`

- [ ] 3.1 Создать `docs/sdd/skills/openspec-init-master-spec/templates/manifest.yaml`.
- [ ] 3.2 Создать `docs/sdd/skills/openspec-init-master-spec/templates/navigation.md`.
- [ ] 3.3 Создать `docs/sdd/skills/openspec-init-master-spec/templates/coverage.md`.
- [ ] 3.4 Создать `docs/sdd/skills/openspec-init-master-spec/templates/stale-files.md`.
- [ ] 3.5 Проверить, что шаблоны не содержат примеров, которые агент может скопировать в итоговый файл как реальные данные.
- [ ] 3.6 Добавить в manifest template поля `schema_version`, `service`, `root`, `generated_at`, `summary`, `files`, `reserved_paths`.
- [ ] 3.7 Добавить в navigation template разделы "что читать первым", "карта по областям", "обязательные документы", "пробелы".
- [ ] 3.8 Добавить в coverage template матрицу coverage по workflow/API/integrations/data/errors/security/NFR.

## 4. References для init

- [ ] 4.1 Создать `docs/sdd/skills/openspec-init-master-spec/references/manifest-schema.md`.
- [ ] 4.2 Создать `docs/sdd/skills/openspec-init-master-spec/references/classification-rules.md`.
- [ ] 4.3 Создать `docs/sdd/skills/openspec-init-master-spec/references/refresh-workflow.md`.
- [ ] 4.4 Создать `docs/sdd/skills/openspec-init-master-spec/references/review-checklist.md`.
- [ ] 4.5 В `classification-rules.md` описать kind values: `overview`, `workflow`, `api`, `integration`, `data`, `state-machine`, `security`, `observability`, `nfr`, `runbook`, `diagram`, `schema`, `other`.
- [ ] 4.6 В `refresh-workflow.md` описать hash mismatch, added/modified/deleted files, stale manifest.
- [ ] 4.7 В `review-checklist.md` описать проверки битых ссылок, coverage gaps, read priority, stale files.

## 5. Обновление `openspec-teach`

- [ ] 5.1 В `docs/sdd/skills/openspec-teach/SKILL.md` заменить структуру `openspec/specs/` на `openspec/<service>/`.
- [ ] 5.2 Добавить `openspec-init-master-spec` в таблицу skills.
- [ ] 5.3 Удалить рекомендацию создавать первую спецификацию через `openspec-new-spec`.
- [ ] 5.4 Добавить правило: если master-spec папка есть, но `_sdd/manifest.yaml` отсутствует, следующий шаг - `openspec-init-master-spec`.
- [ ] 5.5 Обновить mapping запросов пользователя.
- [ ] 5.6 Обновить правила чтения: сначала `_sdd/navigation.md`, потом `_sdd/manifest.yaml`, затем релевантные документы.

## 6. Замена или удаление `openspec-new-spec`

- [ ] 6.1 Принять решение: удалить `openspec-new-spec` из активного workflow или оставить как deprecated.
- [ ] 6.2 Если deprecated - обновить description и явно запретить использовать для текущего framework.
- [ ] 6.3 Если удаляется - убрать упоминания из README, teach, SDD_DETAILS.
- [ ] 6.4 Убрать зависимость от `templates/service-spec.md` в маршрутизации.
- [ ] 6.5 Проверить, что ни один skill не требует `openspec/specs/<service>/<service>.md`.

## 7. Обновление `openspec-propose`

- [ ] 7.1 В `docs/sdd/skills/openspec-propose/SKILL.md` заменить "целевая спецификация" на "master-spec root".
- [ ] 7.2 Обновить входные проверки: искать `openspec/<service>/_sdd/manifest.yaml`.
- [ ] 7.3 Добавить fallback: если manifest отсутствует или stale, предложить `openspec-init-master-spec`.
- [ ] 7.4 Обновить scope исследования: разрешить чтение master-spec docs через manifest.
- [ ] 7.5 Обновить алгоритм выбора документов по tags/entities/integrations/endpoints/events/related_files.
- [ ] 7.6 Обновить `templates/change.md`: добавить `Мастер-спецификация`, `Manifest`, `Spec update mode`.
- [ ] 7.7 Добавить в `change.md` раздел `## 0. Источники master specification`.
- [ ] 7.8 Убрать обязательную ссылку на single-file spec.

## 8. Обновление `openspec-design`

- [ ] 8.1 В `docs/sdd/skills/openspec-design/SKILL.md` читать master-spec root из `change.md`.
- [ ] 8.2 Добавить чтение `_sdd/navigation.md` и `_sdd/manifest.yaml`.
- [ ] 8.3 Добавить выбор релевантных master-spec docs по sources из `change.md`.
- [ ] 8.4 В `templates/design.md` добавить раздел "Источники требований".
- [ ] 8.5 Проверить, что design может ссылаться и на документы master spec, и на кодовые сущности.

## 9. Обновление `openspec-implement`

- [ ] 9.1 В `docs/sdd/skills/openspec-implement/SKILL.md` убрать предположение об обязательном финальном `apply-change`.
- [ ] 9.2 Добавить чтение `Spec update mode` из `change.md`.
- [ ] 9.3 В completion output предлагать следующий шаг в зависимости от mode.
- [ ] 9.4 В `templates/tasks.md` добавить пункт проверки соответствия master-spec documents.

## 10. Обновление `openspec-apply-change`

- [ ] 10.1 В `docs/sdd/skills/openspec-apply-change/SKILL.md` отметить, что single-file Analyst Merge больше не основной путь.
- [ ] 10.2 Добавить предупреждение для folder-based docs: автоматический merge произвольных документов опасен.
- [ ] 10.3 Подготовить интерфейс для будущего branch-diff verify из tasks 2.
- [ ] 10.4 Убрать инструкции, которые требуют мержить `change.md` в `openspec/specs/<service>/<service>.md`.

## 11. README и skill index

- [ ] 11.1 Обновить `docs/sdd/skills/README.md` в корректной UTF-8 кодировке.
- [ ] 11.2 Добавить `openspec-init-master-spec` в список skills.
- [ ] 11.3 Обновить lifecycle diagram.
- [ ] 11.4 Убрать OpenSpec CLI/single-file wording, если он больше не соответствует процессу.

## 12. Проверка документации

- [ ] 12.1 Выполнить `rg "openspec/specs|service-spec.md|<service>.md|new-spec" docs/sdd`.
- [ ] 12.2 Для каждого найденного места решить: заменить, удалить или оставить как историческую заметку.
- [ ] 12.3 Проверить, что все новые Markdown-файлы на русском языке.
- [ ] 12.4 Проверить, что пути Windows/Unix описаны нейтрально.
- [ ] 12.5 Проверить, что нет противоречия между `SDD_DETAILS.md`, README и SKILL.md.

## 13. Acceptance criteria

- [ ] 13.1 Пользователь может скопировать существующую документацию в `openspec/<service>/`.
- [ ] 13.2 Пользователь может запустить `openspec-init-master-spec`.
- [ ] 13.3 После init появляются `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md`.
- [ ] 13.4 `openspec-propose` может создать `change.md` без `service-spec.md`.
- [ ] 13.5 `openspec-design` может создать `design.md` и `tasks.md`, используя master-spec folder.
- [ ] 13.6 В workflow больше нет требования создавать один огромный master Markdown.
