---
name: openspec-teach
description: Учебник по OpenSpec workflow — объясняет какие skills существуют, когда каждый запускается, какие статусы у change-запросов, как читать folder-based master specification и как маппить запросы пользователя на конкретный skill. Используй только когда пользователь явно спрашивает "как работает openspec", "какой skill использовать", "объясни workflow", "научи пользоваться", или когда первая сессия в проекте с директорией openspec/ и непонятно с чего начинать.
license: MIT
compatibility: Требуется `openspec/` layout в проекте.
metadata:
  author: openspec-distillate
  version: "4.0"
---

Прочитай этот документ целиком. Он описывает актуальный OpenSpec workflow: folder-based master specification, skills, статусы, правила чтения и mapping запросов пользователя.

После прочтения ничего создавать и записывать не нужно. Задача skill — насытить контекст знанием workflow и подсказать следующий логичный шаг.

---

## Структура

- `openspec/<service-name>/` — master specification сервиса, source of truth.
- `openspec/<service-name>/_sdd/manifest.yaml` — машинный индекс документов.
- `openspec/<service-name>/_sdd/navigation.md` — карта чтения для человека и LLM.
- `openspec/<service-name>/_sdd/coverage.md` — матрица покрытия документацией.
- `openspec/<service-name>/_sdd/stale-files.md` — stale/warning отчет, если manifest требует обновления.
- `openspec/changes/<name>/` — активные change-запросы:
  - `change.md` — предложение системного аналитика;
  - `design.md` — технический проект;
  - `tasks.md` — план задач на реализацию.
- `openspec/changes/archive/YYYY-MM-DD-<name>/` — завершенные change-запросы.

`openspec/changes/` — системная директория. Имена сервисов `changes`, `archive`, `_sdd`, `_system` зарезервированы.

Старый single-file layout не является обязательным и не используется как канонический.

## Skills

| Skill | Назначение |
|---|---|
| `openspec-teach` | Этот учебник |
| `openspec-init-master-spec` | Создать или обновить `_sdd/manifest.yaml`, `navigation.md`, `coverage.md`, `stale-files.md` для `openspec/<service>/` |
| `openspec-explore` | Structured research кодовой базы: read-only субагенты и YAML-агрегация |
| `openspec-propose` | Создать `change.md` на основе master-spec folder и manifest |
| `openspec-design` | Создать `design.md` и `tasks.md` из согласованного change |
| `openspec-implement` | Выполнить `tasks.md` с обязательной верификацией |
| `openspec-apply-change` | Manual-apply/verify gateway; автоматический merge произвольных folder docs не является основным путем |
| `openspec-archive-change` | Переместить change в `archive/YYYY-MM-DD-<name>/` |
| `openspec-new-spec` | Deprecated, не использовать в активном workflow |

## Жизненный цикл

1. **Подключение документации сервиса** — пользователь помещает документы в `openspec/<service>/`.
2. **Инициализация master spec** — `openspec-init-master-spec` создает `_sdd` navigation layer.
3. **Предложение изменения** — `openspec-propose` создает `change.md` со статусом `На согласовании`.
4. **Ревью и согласование** — PR merge, статус вручную меняется на `Согласовано`.
5. **Технический проект** — `openspec-design` создает `design.md` и `tasks.md` для сложных CR.
6. **Реализация** — `openspec-implement` выполняет задачи, ставит `В реализации`, запускает сборку/тесты/линтер.
7. **Проверка или обновление master spec** — по `Spec update mode`:
   - `branch-diff`: проверить, что документы master spec уже обновлены;
   - `manual-change`: явно обновить нужные документы, при необходимости через `openspec-apply-change` как ручной gateway.
8. **Архивация** — `openspec-archive-change` переносит change в архив.

## Статусы change.md

| Статус | Кто ставит | Когда |
|---|---|---|
| На согласовании | `openspec-propose` | Change создан, идет ревью в PR |
| Согласовано | Аналитик вручную | PR с `change.md` вмержен, change одобрен |
| В реализации | `openspec-implement` | Первый запуск implement, идет кодинг по `tasks.md` |
| Реализовано | Пользователь / verify / apply | Код и master spec обновлены выбранным mode |
| Архивировано | `openspec-archive-change` | Change перемещен в архив |

Обратные переходы — только вручную.

## Правила чтения master spec

1. Если manifest существует, не начинай с рекурсивного чтения всей папки.
2. Сначала читай `openspec/<service>/_sdd/navigation.md`.
3. Затем читай `openspec/<service>/_sdd/manifest.yaml`.
4. Для change выбирай документы по `tags`, `entities`, `integrations`, `endpoints`, `events`, `related_files`, `depends_on`, `read_priority=high`.
5. Если `_sdd/manifest.yaml` отсутствует, следующий шаг — `openspec-init-master-spec`.
6. Если `stale-files.md` непустой, предупреди пользователя перед генерацией `change.md`, `design.md` или `tasks.md`.
7. Если документация противоречива, фиксируй open question.

## Резолв путей bundle

Skills OpenSpec ссылаются на собственные `templates/*` и `references/*` рядом со своим `SKILL.md`. Пути в `SKILL.md` всегда относительные к директории своего skill.

Если harness передал `Skill directory: <abs>` — используй его как корень. Если нет — один раз найди каталог skill через доступные инструменты поиска. Если каталог не найден, спроси пользователя путь установки.

## Правила для агента

- Не редактируй master specification docs напрямую без явного запроса; изменение требований оформляется через `change.md` и ревью.
- Не считай старый single-file layout обязательным.
- Не вызывай deprecated `openspec-new-spec` для нового сервиса.
- Если есть master-spec folder без `_sdd/manifest.yaml`, запускай `openspec-init-master-spec`.
- Не копируй примеры из шаблонов как реальные данные.
- Для размытого обсуждения используй обычный чат без `openspec-explore`.
- `openspec-explore` standalone запускается только с четкими параметрами (`target`, `service`, `anchor` для change).

## Mapping запросов пользователя

| Skill | Триггер-формулировки |
|---|---|
| `openspec-init-master-spec` | "инициализируй master spec", "подключи папку как мастер спецификацию", "собери manifest", "обнови navigation/manifest", "refresh master spec" |
| `openspec-propose` | "добавим", "поменяем", "изменим", "уберем", "сделай change", "оформим CR", "обнови требования" |
| `openspec-design` | "техпроект", "дизайн", "распиши задачи", "подготовь tasks", "план реализации" |
| `openspec-implement` | "реализуй", "начни задачи", "пиши код по плану", "продолжи реализацию" |
| `openspec-apply-change` | "применить change к master spec", "проверить обновление спеки", "manual apply", "внести change в документы" |
| `openspec-archive-change` | "заархивируй change", "перенеси в archive", "закрой change" |
| `openspec-explore` | "картируй сервис", "structured research", "research codebase", "собери карту зоны изменения" |
| `openspec-teach` | "как работает openspec", "научи пользоваться", "объясни workflow", "какой skill использовать" |

Правила mapping:

- Есть `openspec/<service>/`, но нет `_sdd/manifest.yaml` — `openspec-init-master-spec`.
- Нужно изменить существующий сервис — `openspec-propose`.
- Есть согласованный `change.md`, но нет `tasks.md` — `openspec-design`.
- Есть `tasks.md`, пора кодить — `openspec-implement`.
- Реализация завершена — смотри `Spec update mode`, затем verify/manual apply и archive.
- Новый сервис больше не означает создание одного большого файла: сначала folder-based docs, затем init manifest.

## Следующий шаг

Подскажи пользователю подходящий skill:

- нет `_sdd/manifest.yaml` для сервиса → `openspec-init-master-spec`;
- нужно оформить изменение → `openspec-propose`;
- нужно получить разовую карту сервиса или зоны изменения → `openspec-explore`;
- есть согласованный change, пора проектировать → `openspec-design`;
- готов `tasks.md`, пора реализовывать → `openspec-implement`;
- реализация завершена, нужно проверить/применить master spec update → `openspec-apply-change` или branch-diff verify по mode;
- change завершен → `openspec-archive-change`.
