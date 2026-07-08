# SDD/OpenSpec: folder-based master spec и change из ветки аналитика

> Черновая основа презентации до раздела `Детализация сценариев`.
>
> Источники: `docs/sdd/SDD_DETAILS.md`, `docs/sdd/plan_1_step.md`, `docs/sdd/plan_2_step.md`, `docs/sdd/tasks_1_step.md`, `docs/sdd/tasks_2_step.md`.

## 1. Контекст

SDD/OpenSpec нужен не для того, чтобы агент писал код по смутному запросу. Цель подхода - сделать путь от требования до реализации воспроизводимым:

```text
документация -> change -> review -> design -> tasks -> code -> verify docs -> archive
```

В старой модели source of truth часто представлялся как один большой Markdown-файл. Для больших сервисов и слабых моделей это плохо масштабируется:

| Проблема | Что происходит на практике | Что меняем |
|---|---|---|
| Один огромный spec | Документ становится монструозным, его трудно читать и обновлять | Source of truth становится папкой документов |
| Уже есть документация | Команды ведут workflow, integrations, API, DBML, схемы и фронтовые материалы в разных местах | Framework подключает существующую структуру |
| Агент читает слишком много | Без навигации он вынужден перечитывать дерево или угадывать нужные файлы | Добавляется `_sdd/manifest.yaml` и `_sdd/navigation.md` |
| Аналитик уже изменил документы | Разработчику нужен `change.md`, а не повторное интервью | Появляется `change-from-diff` по локальным git refs |

## 2. Целевая идея

Master specification - это не один файл, а папка:

```text
openspec/<service-name>/
```

Внутри лежит привычная для команды структура:

```text
openspec/<service-name>/
  workflow/
  integrations/
  api/
  data/
  security/
  diagrams/
  ...
```

Служебный слой SDD находится рядом с документацией, но не заменяет ее:

```text
openspec/<service-name>/_sdd/
  manifest.yaml
  navigation.md
  coverage.md
  stale-files.md
```

Ключевая мысль для аудитории: `_sdd` - это индекс и карта чтения для человека и агента. Смысл требований находится в master-spec documents.

## 3. Принципы framework

1. Source of truth по требованиям лежит в master-spec folder.
2. `change.md` описывает, что и зачем меняется, но не превращается в технический план.
3. `design.md` отвечает на вопрос, как реализовать изменение.
4. `tasks.md` превращает design в исполняемые шаги и проверки.
5. Агент сначала читает `_sdd/navigation.md`, затем `_sdd/manifest.yaml`, затем только релевантные документы.
6. Противоречия и пробелы фиксируются как gaps или open questions, а не скрываются.
7. Финальный merge в один spec-файл больше не является обязательным этапом.
8. Для branch-diff workflow документы master spec уже находятся в ветке аналитика; после реализации нужна verify-проверка источника.

## 4. Карта артефактов

| Артефакт | Роль | Кто ведет | Что должно быть понятно |
|---|---|---|---|
| `openspec/<service>/` | Master specification | Аналитик / команда | Текущее поведение сервиса и границы требований |
| `_sdd/manifest.yaml` | Машинный индекс | `openspec-init-master-spec` | Какие файлы есть, что в них, как они связаны |
| `_sdd/navigation.md` | Карта чтения | `openspec-init-master-spec` | Что читать первым и где искать области знаний |
| `_sdd/coverage.md` | Покрытие документацией | `openspec-init-master-spec` | Что описано полно, частично или не описано |
| `_sdd/stale-files.md` | Диагностика устаревания | `openspec-init-master-spec` | Какие файлы требуют refresh или ручной проверки |
| `openspec/changes/<change>/change.md` | Аналитический инкремент | `openspec-propose` / `openspec-change-from-diff` | Что меняется, зачем, какие источники и критерии приемки |
| `design.md` | Технический проект | Разработчик / агент | Как изменение ложится в код, контракты, данные и инфраструктуру |
| `tasks.md` | Исполняемый план | Разработчик / агент | Что сделать и чем подтвердить готовность |
| `.spec-diff/` | След генерации из веток | `openspec-change-from-diff` | Какие refs сравнивались, какие файлы изменились, какой контекст выбран |
| `.research/` | Structured research | `openspec-explore` | Read-only findings по коду или зоне изменения |
| `archive/` | История завершенных changes | `openspec-archive-change` | Завершенный change вместе с design, tasks, research и diff metadata |

## 5. Жизненный цикл

Базовый путь, когда требование формируется сейчас:

```text
teach
  -> init-master-spec
  -> propose
  -> review / approve
  -> design?
  -> implement
  -> manual update / verify
  -> archive-change
```

Путь, когда аналитик уже изменил master-spec documents в отдельной ветке:

```text
teach
  -> init-master-spec
  -> change-from-diff
  -> review / approve
  -> design
  -> implement
  -> verify source
  -> archive-change
```

`explore` остается read-only контуром и может подключаться до `propose`, перед `design` или во время ревью, если нужно понять кодовую базу или зону влияния.

## 6. Два рабочих режима изменения требований

### Manual-change

Используется, когда требование формируется через интервью, обсуждение, Jira или текущую работу команды.

```text
идея / Jira / интервью
  -> openspec-propose
  -> change.md
  -> review
  -> design.md
  -> tasks.md
  -> code
  -> обновить master-spec documents
  -> refresh _sdd
```

Главное: агент не должен угадывать всю систему. Он выбирает документы через manifest, уточняет gaps и создает `change.md` как аналитический delta-документ.

### Branch-diff

Используется, когда аналитик уже знает, какие документы поменять, и делает это в своей ветке.

```text
base_ref
  \
   analyst_ref с измененными master-spec documents
      |
      | git diff по openspec/<service>/
      v
change.md + .spec-diff/
  -> design.md
  -> tasks.md
  -> code
  -> verify analyst_ref
```

Главное: `change.md` создается не как копия patch, а как осмысленное описание требований с учетом связанных документов.

## 7. Как агент читает master specification

Если `_sdd/manifest.yaml` существует, агент не начинает с рекурсивного чтения всей папки.

Порядок чтения:

1. Прочитать `_sdd/navigation.md`.
2. Прочитать `_sdd/manifest.yaml`.
3. Проверить `_sdd/stale-files.md`.
4. Выбрать документы по `tags`, `entities`, `integrations`, `endpoints`, `events`, `related_files`, `depends_on`, `read_priority`.
5. Прочитать только выбранные документы и high-priority материалы.
6. Если manifest устарел или отсутствует, предложить `openspec-init-master-spec`.

Это делает workflow пригодным для больших папок документации и снижает риск того, что агент пропустит важные связи.

## 8. Branch-diff: правила безопасности

Diff строится средствами git по локальным refs. Framework не скачивает ветки и не переключает рабочее дерево.

Разрешено:

```bash
git rev-parse --verify <base_ref>
git rev-parse --verify <analyst_ref>
git merge-base <base_ref> <analyst_ref>
git diff --name-status --find-renames <base_ref>...<analyst_ref> -- openspec/<service>/
git diff --stat <base_ref>...<analyst_ref> -- openspec/<service>/
git diff --find-renames --find-copies <base_ref>...<analyst_ref> -- openspec/<service>/
git show <ref>:<path>
```

Запрещено выполнять автоматически:

```bash
git fetch
git pull
git checkout <base_ref>
git checkout <analyst_ref>
```

Default diff mode - three-dot:

```bash
git diff <base_ref>...<analyst_ref> -- openspec/<service>/
```

Two-dot разрешается только как явный override пользователя.

## 9. Что получает разработчик

После `propose` или `change-from-diff` разработчик получает не набор разрозненных страниц, а traceable change:

- статус и режим обновления спецификации;
- master-spec root и manifest;
- источники master specification;
- для branch-diff - base ref, analyst ref, diff mode, merge base, diff command;
- список changed files и context files;
- описание добавленных, измененных и удаленных требований;
- влияние на workflow, API, интеграции, данные, ошибки, безопасность, конфигурацию и фронт;
- критерии приемки;
- gaps, open questions и ограничения анализа.

## 10. Роли

| Роль | Ответственность |
|---|---|
| Бизнес-аналитик | Смысл изменения, бизнес-цель, процесс, правила, критерии приемки |
| Системный аналитик | Master specification, интеграции, модели, контракты, связность документации |
| Разработчик | Реализуемость, design, tasks, код, техническая верификация |
| QA | Проверка критериев приемки, регрессии, тестовые сценарии |
| Tech lead | Границы change, архитектурные риски, качество design |
| AI-агент | Поиск контекста, структурирование, черновики, проверка связности |

AI-агент не владеет решением. Человек отвечает за смысл, приоритеты и статусы `Согласовано` / `Реализовано`.

## 11. Критерии готовности к разработке

Change можно передавать в разработку, если:

- master spec инициализирован, есть `_sdd/manifest.yaml` и `_sdd/navigation.md`;
- `change.md` лежит в `openspec/changes/<change>/`;
- указаны источники master specification и причина их использования;
- выбран `Spec update mode`;
- для `branch-diff` изменены содержательные master-spec documents, а не только `_sdd`;
- gaps и open questions явно видны;
- критерии приемки описаны поведенчески;
- change прошел ревью и получил статус `Согласовано`;
- для сложного изменения есть `design.md` и `tasks.md`.

## 12. Критерии закрытия change

Change можно закрывать, если:

- задачи из `tasks.md` выполнены или исключения явно согласованы;
- кодовые PR прошли review;
- релевантные тесты, сборка и линтер выполнены или зафиксирована причина невозможности;
- документы master spec обновлены или подтверждены в `Analyst ref`;
- для `manual-change` выполнен refresh `_sdd`;
- для `branch-diff` проверено соответствие `change.md`, `.spec-diff/` и документов в analyst ref;
- статус `change.md` переведен в `Реализовано`;
- change перенесен в `openspec/changes/archive/YYYY-MM-DD-<change>/`.

## 13. Риски и контрмеры

| Риск | Контрмера |
|---|---|
| Manifest устарел | Hash, refresh и `stale-files.md` |
| Документация противоречива | Gaps и open questions вместо молчаливого выбора версии |
| Папка слишком большая | Manifest, read priority, targeted reads, structured research |
| Изменены бинарные файлы | Учитывать artifact, искать текстовый контекст рядом, снижать confidence |
| Ветка аналитика изменилась после генерации | Повторить diff summary и сравнить с `.spec-diff/changed-files.yaml` |
| Пользователь хочет применить docs из analyst ref | Делать explicit apply только после clean status и явного подтверждения |

## 14. Главный тезис

Обновленный SDD/OpenSpec сохраняет дисциплину спецификации, но убирает требование вести один большой master Markdown. Команда продолжает работать с привычной документацией, а framework добавляет:

- навигацию для агента;
- traceable change;
- безопасный branch-diff workflow;
- design и tasks для реализации;
- verify вместо неявного автоматического merge.

## Детализация сценариев

> С этого места начинается следующий блок презентации. Его стоит раскрывать отдельными сценариями:
>
> - инициализация master spec;
> - создание `change.md` через `propose`;
> - создание `change.md` через `change-from-diff`;
> - переход к `design.md` и `tasks.md`;
> - реализация и verify master spec;
> - архивирование change.
