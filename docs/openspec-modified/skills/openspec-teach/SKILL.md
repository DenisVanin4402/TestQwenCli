---
name: openspec-teach
description: Учебник по OpenSpec workflow — объясняет какие skills существуют, когда каждый запускается, какие статусы у change-запросов, как читать folder-based master specification и как маппить запросы пользователя на конкретный skill. Используй только когда пользователь явно спрашивает "как работает openspec", "какой skill использовать", "объясни workflow", "научи пользоваться", или когда первая сессия в проекте с директорией openspec/ и непонятно с чего начинать.
license: MIT
compatibility: Требуется `openspec/` layout в проекте.
metadata:
  author: openspec-distillate
  version: "7.2"
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
  - `tasks.md` — план задач на реализацию;
  - `.spec-diff/` — metadata и artifacts для change, созданного из diff веток.
  - `.spec-copy/` — manifest с Git-историей/checksum/согласованием, единственный content и генерируемый review; при apply появляется application-state.json с ходом и конечным результатом. Ссылки на review находятся в change, там же описываются конфликты (schema v3).
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
| `openspec-change-from-diff` | Создать `change.md` из git diff двух локальных refs по `openspec/<service>/` |
| `openspec-design` | Создать `design.md` и `tasks.md` из согласованного change |
| `openspec-implement` | Выполнить `tasks.md` с обязательной верификацией |
| `openspec-apply-change` | Строгий AI apply с сохранением параллельных правок и смысловой проверкой конфликтов; legacy verify/manual |
| `openspec-archive-change` | Переместить change в `archive/YYYY-MM-DD-<name>/` |
| `openspec-new-spec` | Deprecated, не использовать в активном workflow |

## Жизненный цикл

1. **Подключение документации сервиса** — пользователь помещает документы в `openspec/<service>/`.
2. **Инициализация master spec** — `openspec-init-master-spec` создает `_sdd` navigation layer.
3. **Предложение изменения** — `openspec-propose` создает `change.md` со статусом `На согласовании`.
   Если аналитик уже изменил master-spec documents в отдельной ветке/ref, `openspec-change-from-diff` создает `change.md` из diff.
4. **Ревью и согласование** — аналитик проверяет change и связанные копии с `-`/`+`, затем ставит `Согласовано`.
5. **Технический проект** — `openspec-design` создает `design.md` и `tasks.md` по профилю lite/full.
6. **Реализация** — `openspec-implement` выполняет задачи, ставит `В реализации`, запускает сборку/тесты/линтер.
7. **Проверка или обновление master spec** — по `Spec update mode`:
   - `document-copies`, `Apply policy: ai-strict`: ИИ сопоставляет base/approved из Git с актуальным master, обновляет content, генерирует review и проверяет его актуальность перед записью; затем check --result и refresh `_sdd`. Конфликт блокирует весь набор;
   - `branch-diff`: проверить, что документы master spec уже находятся в `Analyst ref`;
   - `manual-change`: явно обновить нужные документы, при необходимости через `openspec-apply-change` как ручной gateway.
8. **Архивация** — `openspec-archive-change` переносит change в архив.

Legacy branch-diff lifecycle:

```text
init-master-spec -> change-from-diff -> design -> implement -> verify/archive
```

Новые change из Markdown diff также используют document-copies и фактический apply в целевой master. Источник base для three-dot — merge-base. Legacy verify только ветки аналитика не означает, что текущий master уже обновлён.

## Размер задачи

`Change profile: lite` подходит для известных локальных правок одного или нескольких объектов: внутреннего enum, условия, значения справочника. Короткие шаблоны не требуют 16 пустых разделов и обязательного structured research. Breaking-контракты, миграции и архитектурные изменения требуют full; количество файлов само по себе не определяет профиль. [Правила выбора](../openspec-propose/references/change-profiles.md).

Профиль не ослабляет ревью: CH-ID связан с content и генерируемым review. [Протокол v3](../openspec-apply-change/references/document-copies.md) сохраняет первоначальное согласование в Git, а единственный review показывает текущий итог для записи. После содержательной правки согласование прежней версии не переносится автоматически. ИИ проверяет смысл, скрипт генерирует представление и сверяет байты.

В manifest хранится `change_sha256` — checksum change.md при подготовке content. Skills, работающие с конкретным change, запускают обычный check: несовпадение означает, что content требует актуализации. После обновления content checksum сохраняется вместе с рендером через --save-change-hash. Смена статуса и CRLF/LF change её не меняют.

Если teach объясняет конкретный существующий document-copies change, также выполни check и сообщи о несовпадении checksum. Для общего объяснения workflow без выбранного change проверка файлов не требуется.

## Статусы change.md

| Статус | Кто ставит | Когда |
|---|---|---|
| На согласовании | `openspec-propose` / `openspec-change-from-diff` | Change создан, идет ревью в PR |
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
| `openspec-change-from-diff` | "создай change из diff веток", "сгенерируй change.md по ветке аналитика", "получи change по base branch и analyst branch", "change from git diff", "ветка аналитика уже изменила master spec" |
| `openspec-design` | "техпроект", "дизайн", "распиши задачи", "подготовь tasks", "план реализации" |
| `openspec-implement` | "реализуй", "начни задачи", "пиши код по плану", "продолжи реализацию" |
| `openspec-apply-change` | "применить change к master spec", "проверить обновление спеки", "manual apply", "внести change в документы" |
| `openspec-archive-change` | "заархивируй change", "перенеси в archive", "закрой change" |
| `openspec-explore` | "картируй сервис", "structured research", "research codebase", "собери карту зоны изменения" |
| `openspec-teach` | "как работает openspec", "научи пользоваться", "объясни workflow", "какой skill использовать" |

Правила mapping:

- Есть `openspec/<service>/`, но нет `_sdd/manifest.yaml` — `openspec-init-master-spec`.
- Нужно изменить существующий сервис — `openspec-propose`.
- Нужно получить change из изменений master-spec в analyst branch — `openspec-change-from-diff`.
- Есть согласованный `change.md`, но нет `tasks.md` — `openspec-design`.
- Есть `tasks.md`, пора кодить — `openspec-implement`.
- Реализация завершена — смотри `Spec update mode`, затем document-copies apply/refresh либо legacy verify/manual, затем archive.
- Новый сервис больше не означает создание одного большого файла: сначала folder-based docs, затем init manifest.
- Для branch-diff refs должны быть локальными; агент не делает `git fetch`, `git pull` и checkout base/analyst веток.

## Следующий шаг

Подскажи пользователю подходящий skill:

- нет `_sdd/manifest.yaml` для сервиса → `openspec-init-master-spec`;
- нужно оформить изменение → `openspec-propose`;
- аналитик уже изменил master spec в ветке → `openspec-change-from-diff`;
- нужно получить разовую карту сервиса или зоны изменения → `openspec-explore`;
- есть согласованный change, пора проектировать → `openspec-design`;
- готов `tasks.md`, пора реализовывать → `openspec-implement`;
- реализация завершена, нужно проверить/применить master spec update → `openspec-apply-change` или branch-diff verify по mode;
- change завершен → `openspec-archive-change`.
