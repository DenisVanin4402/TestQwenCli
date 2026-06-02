# OpenSpec Skills

Набор skills для работы системного аналитика и AI-агента по SDD/OpenSpec workflow.

> Skills работают напрямую с файловой системой: OpenSpec CLI не требуется.
> Source of truth сервиса — folder-based master specification `openspec/<service-name>/`.

## Skills

| Skill | Назначение |
|---|---|
| `openspec-teach` | Учебник по workflow: layout, skills, статусы, правила чтения master spec |
| `openspec-init-master-spec` | Инициализация или refresh `openspec/<service>/_sdd/manifest.yaml`, `navigation.md`, `coverage.md`, `stale-files.md` |
| `openspec-explore` | Read-only structured research кодовой базы или зоны изменения |
| `openspec-propose` | Создание предложения об изменении master specification (`change.md`) |
| `openspec-design` | Создание технического проекта (`design.md`) и плана задач (`tasks.md`) на основе согласованного `change.md` |
| `openspec-implement` | Реализация `tasks.md` с последовательным выполнением задач и обязательной верификацией |
| `openspec-apply-change` | Осторожный manual-apply/verify интерфейс; автоматический single-file merge больше не является основным путем |
| `openspec-archive-change` | Архивирование завершенного изменения |
| `openspec-new-spec` | Deprecated: старый single-file workflow не используется |

## Жизненный цикл

0. Если агент впервые работает с OpenSpec в проекте или пользователь спрашивает про процесс — запусти `openspec-teach`.
1. Помести документацию сервиса в `openspec/<service-name>/`.
2. Запусти `openspec-init-master-spec`: он создаст `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md` и при необходимости `_sdd/stale-files.md`.
3. Для изменения требований запусти `openspec-propose`: он создаст `openspec/changes/<name>/change.md` и выберет документы master spec через manifest.
4. После PR-ревью аналитик вручную ставит статус `Согласовано`.
5. Для сложного CR запусти `openspec-design`: он создаст `design.md` и `tasks.md`.
6. Запусти `openspec-implement`: он выполнит задачи из `tasks.md` и прогонит сборку/тесты/линтер перед завершением.
7. Дальнейшее обновление master spec зависит от `Spec update mode`:
   - `branch-diff` — проверь, что документы master spec уже обновлены в ветке;
   - `manual-change` — явно обнови нужные документы master spec или используй `openspec-apply-change` как ручной gateway.
8. Запусти `openspec-archive-change`, чтобы перенести change в `openspec/changes/archive/YYYY-MM-DD-<name>/`.

```text
teach -> init-master-spec -> propose -> review/approve -> design? -> implement -> verify/apply? -> archive-change
                       \                                      /
                        -------- explore (read-only) --------
```

## Layout

```text
openspec/
  <service-name>/
    _sdd/
      manifest.yaml
      navigation.md
      coverage.md
      stale-files.md
    workflow/
    integrations/
    api/
    data/
    security/
    ...
  changes/
    <change-name>/
      change.md
      design.md
      tasks.md
      .research/
      .research-notes.md
    archive/
      YYYY-MM-DD-<change-name>/
```

Директория `openspec/changes/` является системной. Имена сервисов `changes`, `archive`, `_sdd`, `_system` зарезервированы.

## Статусы change.md

| Статус | Кто ставит | Когда |
|---|---|---|
| На согласовании | `openspec-propose` | Change создан, идет ревью в PR |
| Согласовано | Аналитик вручную | PR с `change.md` вмержен, change одобрен |
| В реализации | `openspec-implement` | Первый запуск implement, идет кодинг по `tasks.md` |
| Реализовано | Пользователь / verify / apply | Код и master spec обновлены выбранным mode |
| Архивировано | `openspec-archive-change` | Change перемещен в архив |

Обратные переходы выполняются только вручную.

## Правила чтения master spec

1. Не читать всю папку рекурсивно, если есть manifest.
2. Сначала читать `openspec/<service>/_sdd/navigation.md`.
3. Затем читать `openspec/<service>/_sdd/manifest.yaml`.
4. Для конкретного change выбирать документы по `tags`, `entities`, `integrations`, `endpoints`, `events`, `related_files`, `depends_on`, `read_priority`.
5. Если `stale-files.md` непустой, предупреждать пользователя перед созданием артефактов.
6. Если manifest отсутствует, следующий шаг — `openspec-init-master-spec`.

## Разработка bundle

```text
skills/
├── openspec-teach/SKILL.md
├── openspec-init-master-spec/SKILL.md
├── openspec-explore/SKILL.md
├── openspec-propose/SKILL.md
├── openspec-design/SKILL.md
├── openspec-implement/SKILL.md
├── openspec-apply-change/SKILL.md
├── openspec-archive-change/SKILL.md
└── openspec-new-spec/SKILL.md
```

## Roadmap

- [ ] Branch-diff verify для проверки, что master-spec documents уже обновлены в ветке
- [ ] Git WorkTree для проектирования нескольких фичей одновременно
- [ ] Поддержка MCP для PlantUML, Mermaid и Draw.io
- [ ] Локальные overrides для skills и references
