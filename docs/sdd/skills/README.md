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
| `openspec-change-from-diff` | Создание `change.md` из git diff двух локальных refs по `openspec/<service>/` |
| `openspec-design` | Создание технического проекта (`design.md`) и плана задач (`tasks.md`) на основе согласованного `change.md` |
| `openspec-implement` | Реализация `tasks.md` с последовательным выполнением задач и обязательной верификацией |
| `openspec-apply-change` | Осторожный manual-apply/verify интерфейс; автоматический single-file merge больше не является основным путем |
| `openspec-archive-change` | Архивирование завершенного изменения |
| `openspec-new-spec` | Deprecated: старый single-file workflow не используется |

## Жизненный цикл

0. Если агент впервые работает с OpenSpec в проекте или пользователь спрашивает про процесс — запусти `openspec-teach`.
1. Помести документацию сервиса в `openspec/<service-name>/`.
2. Запусти `openspec-init-master-spec`: он создаст `_sdd/manifest.yaml`, `_sdd/navigation.md`, `_sdd/coverage.md` и при необходимости `_sdd/stale-files.md`.
3. Для изменения требований запусти:
   - `openspec-propose`, если требования формируются сейчас через интервью и чтение master spec;
   - `openspec-change-from-diff`, если аналитик уже изменил `openspec/<service>/` в отдельной локальной ветке/ref.
4. После PR-ревью аналитик вручную ставит статус `Согласовано`.
5. Для сложного CR запусти `openspec-design`: он создаст `design.md` и `tasks.md`.
6. Запусти `openspec-implement`: он выполнит задачи из `tasks.md` и прогонит сборку/тесты/линтер перед завершением.
7. Дальнейшее обновление master spec зависит от `Spec update mode`:
   - `branch-diff` — проверь, что документы master spec уже находятся в `Analyst ref`;
   - `manual-change` — явно обнови нужные документы master spec или используй `openspec-apply-change` как ручной gateway.
8. Запусти `openspec-archive-change`, чтобы перенести change в `openspec/changes/archive/YYYY-MM-DD-<name>/`.

```text
teach -> init-master-spec -> propose/change-from-diff -> review/approve -> design? -> implement -> verify/apply? -> archive-change
                       \                                      /
                        -------- explore (read-only) --------
```

Branch-diff lifecycle:

```text
init-master-spec -> change-from-diff -> design -> implement -> verify/archive
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
      .spec-diff/
      .research/
      .research-notes.md
    archive/
      YYYY-MM-DD-<change-name>/
```

Директория `openspec/changes/` является системной. Имена сервисов `changes`, `archive`, `_sdd`, `_system` зарезервированы.

## Статусы change.md

| Статус | Кто ставит | Когда |
|---|---|---|
| На согласовании | `openspec-propose` / `openspec-change-from-diff` | Change создан, идет ревью в PR |
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
├── openspec-change-from-diff/SKILL.md
├── openspec-design/SKILL.md
├── openspec-implement/SKILL.md
├── openspec-apply-change/SKILL.md
├── openspec-archive-change/SKILL.md
└── openspec-new-spec/SKILL.md
```

## Roadmap

- [x] Branch-diff generation для `change.md` из двух локальных refs
- [ ] Улучшение автоматизированного branch-diff verify после реализации
- [ ] Git WorkTree для проектирования нескольких фичей одновременно
- [ ] Поддержка MCP для PlantUML, Mermaid и Draw.io
- [ ] Локальные overrides для skills и references

## Обучающие примеры

В примерах ниже "команда" означает фразу в чате агенту. Отдельный OpenSpec CLI не нужен: агент выбирает нужный skill и работает с файлами в репозитории.

### Стандартный workflow

1. Подготовить master specification:
   - Команда: `инициализируй master spec для external-gateway`
   - Естественная фраза: `я положил документацию сервиса в openspec/external-gateway, подключи ее как мастер спецификацию`
   - Ожидаемый шаг: `openspec-init-master-spec` создает или обновляет `_sdd/manifest.yaml`, `navigation.md`, `coverage.md`, `stale-files.md`.

2. Оформить изменение требований:
   - Команда: `создай change add-callback-retry-policy для external-gateway`
   - Естественная фраза: `хочу поменять retry policy для callback delivery, оформи change-request`
   - Ожидаемый шаг: `openspec-propose` читает `_sdd/navigation.md`, `_sdd/manifest.yaml`, выбирает релевантные документы и создает `openspec/changes/<name>/change.md`.

2a. Оформить изменение из ветки аналитика:
   - Команда: `создай change из diff веток service=external-gateway base_ref=release/2026-06 analyst_ref=analysis/add-callback-retry change_name=add-callback-retry-policy`
   - Естественная фраза: `аналитик уже поменял master spec в ветке analysis/add-callback-retry, сгенерируй change.md по diff`
   - Ожидаемый шаг: `openspec-change-from-diff` проверяет локальные refs, выполняет `git diff <base_ref>...<analyst_ref> -- openspec/<service>/`, создает `change.md` и `.spec-diff/` без checkout веток.

3. Согласовать change:
   - Команда: `change add-callback-retry-policy согласован`
   - Естественная фраза: `PR с change.md замержен, можно готовить реализацию`
   - Ожидаемый шаг: аналитик вручную меняет статус в `change.md` на `Согласовано`.

4. Подготовить технический проект:
   - Команда: `подготовь design и tasks для add-callback-retry-policy`
   - Естественная фраза: `распиши технический план реализации и задачи по этому change`
   - Ожидаемый шаг: `openspec-design` создает `design.md` и `tasks.md`, используя `change.md`, manifest и источники master specification.

5. Реализовать задачи:
   - Команда: `реализуй tasks для add-callback-retry-policy`
   - Естественная фраза: `пора кодить этот change, выполняй задачи по плану`
   - Ожидаемый шаг: `openspec-implement` выполняет рабочие чекбоксы в `tasks.md`, обновляет статус на `В реализации`, запускает сборку/тесты/линтер.

6. Проверить или применить изменения master spec:
   - Команда: `проверь master spec update для add-callback-retry-policy`
   - Естественная фраза: `реализация готова, проверь что документация master spec обновлена`
   - Ожидаемый шаг: по `Spec update mode` выполняется branch-diff verify или manual-change через `openspec-apply-change`.

7. Архивировать change:
   - Команда: `архивируй change add-callback-retry-policy`
   - Естественная фраза: `change завершен, перенеси его в архив`
   - Ожидаемый шаг: `openspec-archive-change` переносит каталог в `openspec/changes/archive/YYYY-MM-DD-<name>/`.

### Быстрые команды и фразы

| Что нужно сделать | Командная формулировка | Естественная формулировка | Skill |
|---|---|---|---|
| Понять workflow | `объясни openspec workflow` | `как тут правильно работать со спеками?` | `openspec-teach` |
| Подключить папку документов | `инициализируй master spec service=external-gateway` | `собери manifest и navigation для openspec/external-gateway` | `openspec-init-master-spec` |
| Обновить manifest после правок | `refresh master spec external-gateway` | `я поменял документы, обнови _sdd` | `openspec-init-master-spec` |
| Исследовать сервис read-only | `research codebase target=spec service=external-gateway` | `картируй сервис external-gateway без правок` | `openspec-explore` |
| Исследовать зону изменения | `research target=change service=external-gateway anchor=callback delivery` | `собери карту зоны изменения вокруг callback delivery` | `openspec-explore` |
| Создать change | `propose add-callback-retry-policy` | `нужно добавить retry policy для callback delivery, оформи CR` | `openspec-propose` |
| Создать change из diff веток | `change-from-diff service=external-gateway base_ref=release/2026-06 analyst_ref=analysis/add-callback-retry change_name=add-callback-retry-policy` | `ветка аналитика уже изменила master spec, получи change по base branch и analyst branch` | `openspec-change-from-diff` |
| Подготовить design/tasks | `design add-callback-retry-policy` | `спроектируй реализацию и разбей на задачи` | `openspec-design` |
| Продолжить реализацию | `implement add-callback-retry-policy` | `продолжи реализацию с первой невыполненной задачи` | `openspec-implement` |
| Проверить обновление документации | `apply-change add-callback-retry-policy` | `проверь, что change отражен в master spec documents` | `openspec-apply-change` |
| Закрыть change | `archive add-callback-retry-policy` | `заархивируй завершенный change` | `openspec-archive-change` |

### Типовые ветвления

- Если `openspec/<service>/_sdd/manifest.yaml` отсутствует, следующий шаг всегда `openspec-init-master-spec`.
- Если `stale-files.md` непустой, сначала сделай refresh или явно подтверди продолжение с риском stale manifest.
- Для `openspec-change-from-diff` refs должны существовать локально; skill не делает `git fetch`, `git pull` и checkout base/analyst веток.
- Если change еще `На согласовании`, нельзя запускать реализацию; нужно завершить ревью и поставить `Согласовано`.
- Если `tasks.md` отсутствует, перед реализацией запусти `openspec-design`.
- Если `Spec update mode = branch-diff`, после реализации проверяется source-of-truth в `Analyst ref` и diff metadata.
- Если `Spec update mode = manual-change`, после реализации нужно явно обновить выбранные документы master spec и затем сделать refresh `_sdd`.
