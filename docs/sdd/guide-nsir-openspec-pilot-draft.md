# Гайд SDD/OpenSpec для пилота НСиР

> Статус: черновик для обсуждения.
>
> Основа: гайд ПСиВРД по пилоту OpenSpec, адаптированный под обновленный folder-based framework, `docs-hub`, сервисную архитектуру НСиР и работу через изменения документации до разработки.

## 1. Зачем НСиР запускает пилот

Цель пилота - не заменить аналитику или инженерное ревью новым формальным процессом. Цель - сохранить сильную сторону работы НСиР: аналитик сначала описывает изменение в документации, а разработчик реализует его по согласованному описанию. SDD/OpenSpec должен сделать этот процесс быстрее, проверяемее и удобнее для работы с AI-агентами.

Для НСиР важно проверить, что подход работает в реальном контуре:

- несколько основных сервисов, например `invest-dkz`, `invest-pay` и связанные модули;
- много интеграций, сквозных бизнес-процессов и общих библиотек;
- сервисная архитектура с Docker и OpenShift;
- отдельные репозитории с кодом сервисов и отдельный репозиторий `docs-hub` с документацией;
- уже существующая сильная аналитическая документация по сервисам, бизнес-процессам, интеграциям, доменной модели, справочникам, workflow и фронту.

Хороший результат пилота:

- аналитики продолжают работать через изменение документации, но получают git-историю, PR и OpenSpec `change.md`;
- разработчик получает не набор разрозненных страниц, а связанный change с источниками, влиянием на сервисы, рисками, критериями приемки и техническим планом;
- агент помогает искать затронутые документы и код, но не подменяет ревью аналитика, разработчика и QA;
- документация в `docs-hub` становится ближе к source of truth и не отстает от кода после реализации.

## 2. Фокус гайда

Команда знает свой аналитический процесс, поэтому гайд не пересказывает его. Фокус только на том, как выполнять пилот по обновленному OpenSpec:

- как подключить существующую документацию сервиса как folder-based master specification;
- как агент читает master specification через `_sdd/manifest.yaml` и `_sdd/navigation.md`;
- как создать `change.md` в режиме `propose`, когда требование формируется в диалоге;
- как создать `change.md` в режиме `change-from-diff`, когда аналитик уже изменил документы master specification в своей ветке;
- как перейти от `change.md` к `design.md`, `tasks.md`, реализации и проверке документации;
- как отличать содержательные документы master specification от служебного слоя `_sdd`.

Главное отличие от старого OpenSpec: master specification больше не один огромный `spec.md`. Для НСиР master specification - это набор директорий и файлов по сервису или сквозной области.

## 3. Словарь пилота

| Термин | Как понимаем в НСиР |
|---|---|
| SDD | Specification-Driven Development: сначала согласованное описание изменения, затем design, tasks, код и тесты. |
| OpenSpec | Рабочий процесс и набор агентских skills для ведения master spec, changes, design, tasks и archive. |
| `docs-hub` | Отдельный git-проект с документацией по сервисам НСиР, сквозным процессам, фронту, интеграциям и общим материалам. |
| Master specification | Актуальная документация сервиса или сквозной области. В пилоте это folder-based структура, а не один файл. |
| Master-spec documents | Содержательные документы требований: workflow, integrations, model, dictionaries, dbml, openapi, front, settings и другие файлы, которые описывают систему. Это не `_sdd`. |
| `_sdd` | Служебный слой SDD-навигации. Он помогает агенту читать master spec, но сам по себе не является бизнес-требованием. |
| `_sdd/manifest.yaml` | Машинный индекс документов master spec. Нужен агенту, чтобы выбирать релевантные файлы без чтения всего дерева. |
| `_sdd/navigation.md` | Человеко-читаемая карта: что читать первым, где workflow, модели, интеграции, API, фронт и риски. |
| `_sdd/coverage.md` | Карта покрытия: что описано полно, частично или отсутствует. |
| `change.md` | Аналитическое описание изменения: что меняется, зачем, где источники, какие критерии приемки. Не является техническим планом. |
| `design.md` | Технический проект реализации по согласованному `change.md`. |
| `tasks.md` | Исполняемый план для разработки и верификации. |
| `propose` | Режим, когда агент через интервью и чтение master spec помогает создать `change.md`. |
| `explore` | Read-only исследование кода или зоны изменения. Ничего не правит, сохраняет findings в `.research/`. |
| `change-from-diff` | Режим, когда агент создает `change.md` из git diff между базовой веткой и веткой аналитика. |
| `manual-change` | Документы master spec обновляются явно человеком или через осторожный manual gateway. |
| `branch-diff` | Документы master spec уже изменены в ветке аналитика; после реализации нужно verify, а не автоматический merge. |

## 4. Рекомендуемый layout для `docs-hub`

В пилоте нужно зафиксировать один layout и использовать его одинаково в гайде, skills и примерах. Черновая рекомендация:

```text
docs-hub/
  openspec/
    changes/
      <change-name>/
        change.md
        design.md
        tasks.md
        .research/
        .spec-diff/
      archive/
        YYYY-MM-DD-<change-name>/
    <service-name>/
      _sdd/
        manifest.yaml
        navigation.md
        coverage.md
        stale-files.md

  invest-dkz/
    workflow/
    integrations/
    model/
    dictionaries/
    dbml/
    openapi/
    settings/

  invest-pay/
    workflow/
    integrations/
    model/
    openapi/

  common/
    cross-processes/
    front/
    dictionaries/
    architecture/
```

Пояснение к layout:

- содержательная документация сервиса может лежать в привычной структуре `docs-hub/<service>/...`;
- SDD-навигация и OpenSpec-служебные файлы лежат в `docs-hub/openspec/<service>/_sdd/`;
- master-spec documents - это файлы `docs-hub/<service>/...`, а не файлы из `_sdd`;
- `manifest.yaml` обязан ссылаться на реальные Markdown/OpenAPI/DBML/diagram-файлы, которые составляют master specification;
- `openspec/changes/` хранит не документацию сервиса, а рабочие changes;
- `openspec/changes/archive/` хранит завершенные changes вместе с `change.md`, `design.md`, `tasks.md`, `.research/` и `.spec-diff/`.

Решить на пилоте: нужно ли физически переносить содержательные документы внутрь `docs-hub/openspec/<service>/...` или оставить их в `docs-hub/<service>/...`, а в `manifest.yaml` хранить ссылки на эти пути. Для НСиР второй вариант выглядит ближе к сервисной структуре документации, но его нужно стабильно поддержать в skills.

## 5. Источник истины

На старте пилота source of truth фиксируется только для выбранных пилотных областей:

- `docs-hub` становится рабочим источником для пилотных областей после переноса, ревью и инициализации `_sdd`;
- кодовые репозитории остаются источником фактической реализации;
- если документация и код расходятся, агент фиксирует gap или open question, а не выбирает удобную версию молча.

Целевое состояние:

- для пилотных сервисов и процессов source of truth по требованиям находится в `docs-hub`;
- каждое новое изменение требований проходит через `openspec/changes/<change>/change.md`;
- изменения документации проходят PR в `docs-hub`;
- изменения кода проходят PR в сервисном репозитории;
- Jira связывает оба PR и change.

## 6. Инициализация master spec для сервиса

Перед тем как агент сможет качественно работать с документацией сервиса, нужно создать навигационный слой `_sdd`.

Команда для агента в естественной форме:

```text
инициализируй master spec для invest-dkz в docs-hub
```

Ожидаемый результат:

```text
docs-hub/openspec/invest-dkz/_sdd/
  manifest.yaml
  navigation.md
  coverage.md
  stale-files.md
```

Что делает `openspec-init-master-spec`:

1. Собирает список документов сервиса.
2. Классифицирует их как `workflow`, `integration`, `data`, `api`, `dictionary`, `front`, `security`, `nfr`, `schema`, `diagram` или `other`.
3. Извлекает теги, сущности, интеграции, endpoints, события и связи.
4. Создает `manifest.yaml` для машинной навигации.
5. Создает `navigation.md` для человека и агента.
6. Создает `coverage.md`, чтобы честно показать пробелы.
7. Создает `stale-files.md`, если есть устаревшие ссылки, новые файлы без классификации или сомнения.

Правило пилота: если `manifest.yaml` отсутствует или явно устарел, сначала делаем refresh `_sdd`, а уже потом создаем `change.md`.

Важно: `_sdd` не заменяет master specification. При генерации `change.md` агент использует `_sdd` как индекс, но смысл изменения берет из содержательных документов сервиса: `workflow`, `integrations`, `model`, `dictionaries`, `dbml`, `openapi`, `front`, `settings` и других документов master spec.

## 7. Роли в процессе

| Роль | Ответственность в пилоте |
|---|---|
| Бизнес-аналитик | Формулирует цель, бизнес-смысл, процесс, правила, критерии приемки, согласует `change.md`. |
| Системный аналитик | Ведет структуру master spec, интеграции, модели, контракты, workflow, consistency review. |
| Backend-разработчик | Проверяет реализуемость, создает или ревьюит `design.md` и `tasks.md`, реализует код в сервисах. |
| Frontend-разработчик | Проверяет влияние на UI, формы, сценарии пользователя и фронтовую документацию. |
| QA | Проверяет критерии приемки, тестовые сценарии, регрессионные риски и DoD. |
| DevOps / Platform | Участвует, если change затрагивает Docker, OpenShift, Helm, конфигурацию, секреты, ресурсы, мониторинг. |
| Tech lead | Следит за границами change, архитектурными решениями, рисками сквозных изменений и качеством пилота. |

AI-агент не является владельцем решения. Он ускоряет поиск, структурирование, генерацию черновиков и проверку связности.

## 8. Схема режимов OpenSpec

В пилоте есть четыре рабочих режима. Они не конкурируют, а используются на разных этапах.

```text
Подготовка сервиса:
  init-master-spec
    -> _sdd/manifest.yaml
    -> _sdd/navigation.md
    -> _sdd/coverage.md

Требование еще не оформлено в документах:
  propose
    -> change.md
    -> review
    -> design.md
    -> tasks.md
    -> implement
    -> manual update / verify
    -> archive

Аналитик уже изменил master-spec documents в ветке:
  change-from-diff(base_ref, analyst_ref)
    -> .spec-diff/
    -> change.md
    -> review / approve
    -> design.md
    -> tasks.md
    -> implement
    -> verify analyst_ref
    -> archive

Нужно понять код или зону влияния:
  explore
    -> .research/
    -> research-notes.md
    -> вход для propose / design / ревью
```

`_sdd` участвует во всех режимах как навигационный слой. Но diff требований строится по master-spec documents, а не по `_sdd`.

## 9. Flow A. Инициализация и refresh master spec

Этот flow готовит документацию сервиса к работе с агентом.

```text
docs-hub/<service>/
  workflow/
  integrations/
  model/
  dictionaries/
  dbml/
  openapi/
  settings/
        |
        | openspec-init-master-spec
        v
docs-hub/openspec/<service>/_sdd/
  manifest.yaml
  navigation.md
  coverage.md
  stale-files.md
```

Шаги:

1. Выбрать сервис или сквозную область.
2. Убедиться, что master-spec documents лежат в согласованной структуре.
3. Запустить `openspec-init-master-spec`.
4. Проверить, что `manifest.yaml` ссылается на содержательные документы, а не описывает только `_sdd`.
5. Проверить `navigation.md`: агенту должно быть понятно, что читать первым.
6. Проверить `coverage.md`: пробелы не скрываются.
7. При изменениях в документах запускать refresh `_sdd`.

DoD: есть актуальный `_sdd`, но source of truth остается в master-spec documents.

## 10. Flow B. `propose`: change через интервью и чтение master spec

Этот режим используется, когда требование еще не внесено в документы отдельной веткой.

```text
Идея / Jira / устное требование
        |
        | openspec-propose
        v
чтение _sdd/navigation.md
чтение _sdd/manifest.yaml
выбор master-spec documents
точечный explore при необходимости
        |
        v
openspec/changes/<change>/change.md
        |
        v
review -> статус "Согласовано" -> design -> tasks -> implement
        |
        v
manual-change: обновить master-spec documents и refresh _sdd
```

Шаги:

1. Аналитик задает цель и скоуп изменения.
2. Агент уточняет мотивацию, пользователей, ограничения, миграцию, обратную совместимость и критерии приемки.
3. Агент читает `_sdd` только как карту.
4. Агент выбирает и читает master-spec documents по `tags`, `entities`, `integrations`, `endpoints`, `events`, `related_files`, `depends_on`, `read_priority`.
5. При необходимости запускается read-only `explore`.
6. Агент создает `change.md` со статусом `На согласовании`.
7. После ревью человек переводит статус в `Согласовано`.
8. Дальше создаются `design.md` и `tasks.md`.

Критическое правило: `change.md` описывает "что и зачем", а не технический план реализации.

## 11. Flow C. `change-from-diff`: change из двух веток master spec

Это основной режим для сценария, где аналитик уже внес изменения в нужные файлы master specification.

### 11.1. Что сравниваем

Сравниваются две локальные git-ссылки:

```text
base_ref     = релизная / базовая ветка документации
analyst_ref  = ветка аналитика с изменениями по фиче
```

Diff строится по содержательным master-spec documents:

```text
docs-hub/<service>/workflow/**
docs-hub/<service>/integrations/**
docs-hub/<service>/model/**
docs-hub/<service>/dictionaries/**
docs-hub/<service>/dbml/**
docs-hub/<service>/openapi/**
docs-hub/<service>/settings/**
```

Если команда запускается из корня git-репозитория `docs-hub`, pathspec будет без префикса `docs-hub/`:

```text
<service>/workflow/**
<service>/integrations/**
<service>/model/**
...
```

Не путать с:

```text
docs-hub/openspec/<service>/_sdd/**
```

`_sdd` можно читать для выбора контекста и проверки актуальности manifest, но изменения `_sdd` сами по себе не являются содержательным требованием. Если в ветке аналитика изменился только `_sdd`, `change.md` по требованиям создавать не нужно: сначала надо понять, какие master-spec documents реально изменились.

### 11.2. Схема веток

```text
release/2026-06
  docs-hub/invest-dkz/model/UdkzProductType.md
  docs-hub/invest-dkz/workflow/14.../index.md
  docs-hub/invest-dkz/dbml/invest_dkz.dbml
          |
          | branch
          v
analytics/NSIR-123/rename-product-type
  docs-hub/invest-dkz/model/UdkzProductType.md        MODIFIED
  docs-hub/invest-dkz/workflow/14.../index.md         MODIFIED
  docs-hub/invest-dkz/dbml/invest_dkz.dbml            MODIFIED
  docs-hub/openspec/invest-dkz/_sdd/manifest.yaml     metadata / index
```

Агент должен интерпретировать первые три файла как изменение master specification. `manifest.yaml` используется как индекс и может требовать refresh, но не должен стать единственным источником смысла.

### 11.3. Команда агенту

```text
создай change из diff:
service=invest-dkz
base_ref=release/2026-06
analyst_ref=analytics/NSIR-123/rename-product-type
change_name=rename-product-type
```

### 11.4. Что делает агент

1. Проверяет локальные refs:

```bash
git rev-parse --verify release/2026-06
git rev-parse --verify analytics/NSIR-123/rename-product-type
```

2. Определяет merge-base для default three-dot diff:

```bash
git merge-base release/2026-06 analytics/NSIR-123/rename-product-type
```

3. Получает список измененных master-spec documents:

```bash
git diff --name-status --find-renames release/2026-06...analytics/NSIR-123/rename-product-type -- invest-dkz/
```

4. Получает статистику и patch:

```bash
git diff --stat release/2026-06...analytics/NSIR-123/rename-product-type -- invest-dkz/
git diff --find-renames --find-copies release/2026-06...analytics/NSIR-123/rename-product-type -- invest-dkz/
```

5. Читает old/new содержимое точечно через `git show`, без checkout веток:

```bash
git show release/2026-06:invest-dkz/model/UdkzProductType.md
git show analytics/NSIR-123/rename-product-type:invest-dkz/model/UdkzProductType.md
```

6. Читает `_sdd/manifest.yaml` из `analyst_ref`, чтобы выбрать связанные документы:

```bash
git show analytics/NSIR-123/rename-product-type:openspec/invest-dkz/_sdd/manifest.yaml
```

7. Создает diff artifacts:

```text
openspec/changes/rename-product-type/.spec-diff/
  refs.txt
  name-status.txt
  stat.txt
  patch.diff
  changed-files.yaml
  context-files.yaml
```

8. Создает:

```text
openspec/changes/rename-product-type/change.md
```

### 11.5. Что попадает в `change.md`

`change.md` должен содержать не механический patch, а аналитическое описание изменения:

- `Spec update mode: branch-diff`;
- `Base ref`, `Analyst ref`, `Diff mode`, `Merge base`;
- changed files: added / modified / deleted / renamed;
- context files, выбранные через manifest;
- какие требования добавлены, изменены или удалены;
- влияние на workflow, модели, интеграции, DBML/OpenAPI, фронт, конфигурацию;
- критерии приемки;
- gaps и вопросы, если мотивация или последствия не выводятся надежно.

### 11.6. Что запрещено в этом flow

- Делать `git fetch`, `git pull` или checkout base/analyst веток автоматически.
- Сравнивать только `_sdd` и называть это diff master specification.
- Создавать `change.md`, если изменены только служебные `_sdd` файлы и нет изменений master-spec documents.
- Копировать patch в `change.md` вместо формулировки требований.
- Автоматически применять документы из analyst branch в текущую ветку разработки.

### 11.7. Дальнейшие шаги

```text
change.md
  -> review / approve
  -> design.md
  -> tasks.md
  -> code PR в репозитории сервиса
  -> verify analyst_ref как source of truth по документации
  -> status "Реализовано"
  -> archive
```

Финальный `apply-change` по умолчанию не нужен. Документы master specification уже находятся в `analyst_ref`; после реализации нужно проверить, что они соответствуют реализованному поведению и `change.md`.

## 12. Как Jira связывается с `docs-hub`, change и кодом

Для пилота нужно одно простое соглашение:

- одна Jira-задача соответствует одному логическому OpenSpec change;
- у Jira-задачи может быть один PR в `docs-hub` и один или несколько PR в кодовых репозиториях;
- название ветки и change должно включать номер задачи или быть явно связано с ним;
- `change.md` должен указывать инициатор и ссылку на Jira;
- PR в коде должен ссылаться на `change.md`.

Пример:

```text
Jira: NSIR-123
docs-hub branch: analytics/NSIR-123/rename-product-type
OpenSpec change: rename-product-type
code branch: feature/NSIR-123/rename-product-type
docs PR: NSIR-123 rename product type docs
code PR: NSIR-123 rename product type implementation
```

Что именно поменялось по задаче, показывают:

- diff ветки `docs-hub` по master-spec documents;
- `.spec-diff/changed-files.yaml`;
- разделы `## 0. Источники master specification` и `## 0. Источники изменения` в `change.md`;
- ссылки в Jira и PR.

## 13. Что должно быть в `change.md`

`change.md` отвечает на вопрос "что и зачем меняем". Он не должен быть техническим планом реализации.

Минимально важные части:

- статус: `На согласовании`, `Согласовано`, `В реализации`, `Реализовано`, `Архивировано`;
- дата, автор, Jira;
- master specification и manifest;
- `Spec update mode`: `manual-change` или `branch-diff`;
- источники master specification;
- цель изменения и бизнес-мотивация;
- затронутые сервисы, интеграции, модели, workflow, фронт;
- изменения бизнес-логики, FSM, моделей данных, интеграций, ошибок, безопасности, миграций, конфигурации;
- критерии приемки;
- gaps и open questions.

Правило для всех разделов: если раздел не затрагивается, пишем `Нет изменений.`, а не оставляем пустой шаблон.

## 14. Когда нужен `design.md` и `tasks.md`

`design.md` и `tasks.md` нужны почти всегда, если изменение затрагивает:

- больше одного сервиса;
- БД, Liquibase, DBML или миграции;
- OpenAPI, контракты или интеграции;
- бизнес workflow;
- фронт и backend одновременно;
- Docker, OpenShift, Helm, конфигурацию, мониторинг;
- безопасность, роли, аудит или персональные данные;
- обратную совместимость.

Для маленького текстового или справочного изменения команда может решить обойтись без отдельного `design.md`, но это должно быть осознанное исключение.

`design.md` отвечает на вопрос "как реализуем". `tasks.md` превращает design в последовательные чекбоксы, которые можно выполнять и проверять.

## 15. Definition of Ready

Change можно передавать в разработку, если выполнены условия:

- master spec по сервису инициализирован, есть `_sdd/manifest.yaml` и `_sdd/navigation.md`;
- `change.md` создан и лежит в `openspec/changes/<change>/`;
- в `change.md` указан Jira-инициатор;
- указаны master-spec sources и причина чтения каждого источника;
- выбран `Spec update mode`;
- для `branch-diff` есть измененные master-spec documents, а не только `_sdd`;
- gaps и open questions явно видны;
- критерии приемки описаны поведенчески;
- влияние на 5 основных сервисов, интеграции, фронт, БД, конфигурацию и OpenShift либо подтверждено, либо явно исключено;
- change прошел ревью и статус установлен в `Согласовано`;
- для сложного изменения есть `design.md` и `tasks.md`.

## 16. Definition of Done

Change можно закрывать, если выполнены условия:

- все задачи из `tasks.md` выполнены или исключения явно согласованы;
- кодовые PR прошли ревью;
- релевантные тесты, сборка и линтер выполнены или зафиксирована причина невозможности;
- документы master spec обновлены или подтверждены в `Analyst ref`;
- для `manual-change` выполнен refresh `_sdd`;
- для `branch-diff` проверено, что документы в ветке аналитика соответствуют `change.md` и `.spec-diff/`;
- Jira содержит ссылки на docs PR, code PR и OpenSpec change;
- статус `change.md` переведен в `Реализовано`;
- change перенесен в `openspec/changes/archive/YYYY-MM-DD-<change>/`.

## 17. Правила работы с AI-агентами

1. Read-only first: сначала чтение `navigation.md`, `manifest.yaml`, выбранных документов и кода, потом предложения.
2. Агент не читает весь `docs-hub` рекурсивно, если есть manifest.
3. Агент не скрывает противоречия: все gaps попадают в вопросы или coverage.
4. Агент не делает destructive actions без явного подтверждения.
5. Агент не работает с production-секретами, доступами, реальными персональными данными и production-like окружениями без отдельного согласования.
6. Агент не делает `git fetch`, `git pull` и checkout аналитических веток в `change-from-diff` автоматически.
7. Агент не должен превращать `change.md` в техническую реализацию.
8. Разработчик не должен реализовывать change в статусе `На согласовании`.
9. Если реализация изменила design, нужно обновить `design.md`, `tasks.md` или зафиксировать расхождение в PR.
10. Человек принимает решение о статусах `Согласовано`, `Реализовано` и о спорных требованиях.

## 18. Где AI пока не используем без отдельного согласования

На первом этапе пилота не даем агенту самостоятельно менять:

- production-конфигурацию;
- секреты, доступы, роли и права;
- OpenShift manifests для критичных контуров;
- миграции с риском потери данных;
- массовые изменения во всех сервисах без `design.md`;
- документы с персональными или чувствительными данными;
- юридически значимые формулировки без ревью ответственного владельца.

## 19. Быстрые фразы для команды

| Что нужно | Фраза агенту | Ожидаемый режим |
|---|---|---|
| Понять процесс | `объясни openspec workflow для docs-hub НСиР` | `openspec-teach` |
| Подключить сервис | `инициализируй master spec для invest-dkz` | `openspec-init-master-spec` |
| Обновить индекс после правок | `я поменял документы invest-dkz, обнови _sdd` | `openspec-init-master-spec refresh` |
| Исследовать сервис | `сделай explore сервиса invest-dkz без правок` | `openspec-explore target=spec` |
| Исследовать зону изменения | `сделай explore зоны изменения вокруг UdkzProductType` | `openspec-explore target=change` |
| Создать change через интервью | `создай change для изменения процесса импорта ДЗ` | `openspec-propose` |
| Создать change из ветки аналитика | `создай change из diff по master-spec documents: service=invest-dkz base_ref=release/2026-06 analyst_ref=analytics/NSIR-123/rename-product-type change_name=rename-product-type` | `openspec-change-from-diff` |
| Подготовить реализацию | `подготовь design и tasks для rename-product-type` | `openspec-design` |
| Реализовать задачи | `реализуй tasks для rename-product-type` | `openspec-implement` |
| Проверить master spec | `проверь master spec update для rename-product-type` | `openspec-apply-change` |
| Закрыть change | `архивируй change rename-product-type` | `openspec-archive-change` |

## 20. Метрики пилота

После каждой пилотной задачи фиксируем короткий отчет:

| Метрика | Что смотрим |
|---|---|
| Время подготовки требований | Сколько заняло от идеи до согласованного `change.md`. |
| Время старта разработки | Насколько быстрее разработчик понял скоуп и начал работу. |
| Количество уточнений | Сколько вопросов возникло после передачи в разработку. |
| Документационные gaps | Сколько пробелов обнаружил агент или ревью. |
| Доля найденных затронутых файлов | Насколько полно агент нашел документы и код. |
| Качество критериев приемки | Смог ли QA использовать критерии без дополнительного устного контекста. |
| Актуальность документации | Осталась ли master spec актуальной после реализации. |
| Польза AI | Что агент реально ускорил, а где мешал или ошибался. |

Минимальный формат отчета:

```text
Jira:
Change:
Сервисы:
Flow: propose | branch-diff
Что помогло:
Что не сработало:
Gaps:
Решение на следующий пилот:
```

## 21. Предлагаемый план пилота

### Этап 1. Подготовка

1. Выбрать 1-2 сервиса для первого прохода, например `invest-dkz` и один связанный сервис.
2. Подготовить документацию выбранной области в `docs-hub`.
3. Зафиксировать layout.
4. Инициализировать `_sdd`.
5. Провести короткую сессию с аналитиками, разработчиками и QA по flow.

### Этап 2. Первая задача через `propose`

1. Взять небольшую, но реальную задачу.
2. Создать `change.md` через интервью с агентом.
3. Провести ревью `change.md`.
4. Создать `design.md` и `tasks.md`.
5. Реализовать и закрыть.
6. Заполнить отчет пилота.

### Этап 3. Вторая задача через `change-from-diff`

1. Аналитик меняет документы в ветке `docs-hub`.
2. Агент генерирует `change.md` из diff.
3. Команда проверяет, насколько полно diff по master-spec documents превращается в `change.md`.
4. Разработчик проходит design, tasks, implement.
5. Проверяется, что master spec source находится в analyst ref.

### Этап 4. Сквозная задача

1. Выбрать изменение, которое затрагивает несколько сервисов, интеграцию или фронт.
2. Проверить, как manifest и `explore` помогают не потерять связи.
3. Оценить качество `design.md` и `tasks.md` для multi-service изменения.

### Этап 5. Ретро и решение о масштабировании

1. Сравнить оба flow.
2. Решить, какой layout `docs-hub` закрепляем.
3. Уточнить шаблоны `change.md`, `design.md`, `tasks.md`.
4. Настроить правила веток, PR и Jira-ссылок.
5. Подготовить финальный HTML-гайд и короткую памятку для команды.

## 22. Вопросы для обсуждения перед финальным HTML

1. Какой layout закрепляем для `docs-hub`: документы внутри `openspec/<service>/` или текущая структура `<service>/...` плюс `_sdd` в `openspec/<service>/_sdd/`?
2. Какие 5 основных сервисов перечисляем в гайде явно?
3. Какая ветка является базовой для `docs-hub`: `master`, `main`, релизная ветка или отдельная baseline-ветка?
4. Как называем аналитические ветки: `analytics/NSIR-123/...` или другой формат?
5. Кто ставит статус `Согласовано` в `change.md`?
6. Нужно ли для всех pilot changes требовать `design.md` и `tasks.md`, или разрешаем исключения?
7. Где хранить ссылку на `change.md` в Jira и PR?
8. Берем в пилот только новые изменения или также активные незавершенные задачи?
9. Какие сервисы и процессы не берем в первый пилот из-за риска?
10. Кто владелец `docs-hub` и кто ревьюит `_sdd/manifest.yaml`, `navigation.md`, `coverage.md`?

## 23. Короткая формула для команды

SDD/OpenSpec в НСиР - это не "агент сам кодит по смутному запросу". Это дисциплина:

```text
документация -> change -> review -> design -> tasks -> code -> verify docs -> archive
```

А для привычного аналитического процесса:

```text
ветка аналитика в docs-hub -> git diff по master-spec documents -> change.md -> разработка -> verify -> archive
```

Человек отвечает за смысл, приоритет и согласование. Агент помогает быстрее собрать контекст, найти связи, оформить артефакты и не потерять требования.
