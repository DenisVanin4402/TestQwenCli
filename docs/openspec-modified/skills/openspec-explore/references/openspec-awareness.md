# Осведомленность об OpenSpec

Этот файл — справочник для lead-agent structured-режима. Conversational-режим в `openspec-explore` не поддерживается. Назначение файла: быстро определить состояние OpenSpec-артефактов и выбрать правильный skill для фиксации.

## 1. Что такое OpenSpec

OpenSpec — каталог master specifications и журнал изменений в репозитории.

Артефакты:

- `openspec/<service>/` — folder-based master specification сервиса.
- `openspec/<service>/_sdd/manifest.yaml` — машинный индекс master spec.
- `openspec/<service>/_sdd/navigation.md` — карта чтения.
- `openspec/<service>/_sdd/coverage.md` — покрытие документацией.
- `openspec/<service>/_sdd/stale-files.md` — stale/warning отчет.
- `openspec/changes/<name>/change.md` — активный change.
- `openspec/changes/<name>/design.md` — опциональный технический проект.
- `openspec/changes/<name>/tasks.md` — план реализации.
- `openspec/changes/<name>/.spec-diff/` — diff metadata для branch-diff change.
- `openspec/changes/archive/<name>/` — архивированные changes.

`openspec-explore` не создает и не правит эти артефакты, кроме `.research/*.yaml` и `.research-notes.md`.

## 2. Проверка состояния

Выполняй до запуска ролей исследования:

```text
list openspec/changes/
check openspec/<service>/
check openspec/<service>/_sdd/manifest.yaml
```

Состояния:

| Состояние | Признак | Следствие |
|---|---|---|
| Нет master-spec folder | `openspec/<service>/` отсутствует | Нужна папка документов; предложить пользователю создать ее |
| Folder есть, manifest отсутствует | `openspec/<service>/` есть, `_sdd/manifest.yaml` нет | Следующий шаг — `openspec-init-master-spec` |
| Manifest есть, active change нет | `_sdd/manifest.yaml` есть, change по anchor нет | Для требований — `openspec-propose`; для разового картирования можно продолжать research |
| Active change есть | `openspec/changes/<name>/change.md` относится к anchor | Прочитать change для контекста; после research предложить design/implement/update change |
| Есть branch-diff metadata | `.spec-diff/changed-files.yaml` существует | Учитывать changed/context files как источники требований |

Если `anchor` неизвестен и `target` не указан, не запускайся: запроси параметры.

## 3. Что читать из существующих артефактов

Для folder-based master spec:

1. Прочитай `openspec/<service>/_sdd/navigation.md`.
2. Прочитай `openspec/<service>/_sdd/manifest.yaml`.
3. Если `stale-files.md` существует и непустой, зафиксируй warning.
4. Для change-контекста прочитай documents из `## 0. Источники master specification`.
5. Для active change прочитай `change.md`, а также `design.md` и `tasks.md`, если они есть.

В агрегированный YAML добавь `existing_artifacts`:

- `master_spec_root`;
- `manifest_path`;
- `navigation_path`;
- `coverage_path`;
- `stale_files_path`;
- `active_change_path`;
- `active_change_status`;
- `spec_update_mode`;
- `anchor_overlap`.

В `gaps[]` фиксируй противоречия между кодом, change и master-spec documents.

## 4. Маршрутизация инсайтов

| Инсайт | Куда направить |
|---|---|
| Папка документов есть, manifest отсутствует | `openspec-init-master-spec` |
| Сервис требует change | `openspec-propose` |
| Change нужен из base/analyst refs | `openspec-change-from-diff` |
| Требование изменилось в active change | Обновить `change.md` через `openspec-propose` |
| Архитектурное решение требует обоснования | `openspec-design` |
| Change согласован, пора планировать реализацию | `openspec-design` |
| Change согласован, tasks.md уже есть | `openspec-implement` |
| Код реализован, master-spec update нужно проверить | `openspec-apply-change` или branch-diff verify source |
| Change завершен | `openspec-archive-change` |

## 5. Запреты explore-режима

- Не создавать и не редактировать `change.md`, `design.md`, `tasks.md`, master-spec documents.
- Не вести свободное обсуждение и не предлагать архитектуру без запроса.
- Не запускать субагентов из субагентов.
- Не читать сырые файлы после возврата субагентов, если достаточно YAML-сводок.
