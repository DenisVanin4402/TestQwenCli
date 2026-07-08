# Сценарии и формулировки запросов

Документ можно использовать как шпаргалку для аналитиков, разработчиков и владельца продукта. Формулировки ниже - это сообщения AI-агенту, а не shell-команды.

В примерах `docs-hub/...` означает путь в общем наборе репозиториев. Если агент работает из корня репозитория `docs-hub`, используйте относительные пути без префикса `docs-hub/`.

## 1. Аналитик инициализирует документацию сервиса

Когда использовать: документы сервиса уже перенесены из Confluence в `docs-hub`, но `_sdd` еще нет или он устарел.

Формулировка:

```text
Инициализируй master spec для сервиса invest-dkz.
Документы лежат в docs-hub/invest-dkz.
Нужно создать или обновить manifest, navigation, coverage и stale-files.
Особое внимание: workflow, integrations, model, dictionaries, openapi, frontend и настройки.
```

Ожидаемый результат:

```text
docs-hub/openspec/invest-dkz/_sdd/manifest.yaml
docs-hub/openspec/invest-dkz/_sdd/navigation.md
docs-hub/openspec/invest-dkz/_sdd/coverage.md
docs-hub/openspec/invest-dkz/_sdd/stale-files.md
```

Что проверяет аналитик:

- ключевые документы сервиса попали в manifest;
- главные workflow и интеграции имеют `read_priority=high`;
- в coverage честно указаны gaps;
- удаленные или спорные документы не скрыты.

## 2. Аналитик исследует область через explore

Когда использовать: аналитик пока не хочет менять документы или создавать change, а хочет разобраться в текущем поведении, найти источники и gaps.

Формулировка:

```text
Работаем по OpenSpec explore.

Сервис: invest-dkz.
Область: обработка входящего УЭСИД по НСиР и дальнейшее расследование невыясненной суммы.

Нужно:
- найти релевантные workflow-документы;
- найти модели данных и справочники;
- найти интеграции и OpenAPI/контракты;
- найти фронтовые документы, если они влияют на сценарий;
- сопоставить с кодом, если code repo доступен;
- показать gaps, противоречия и вопросы аналитику.

Режим read-only: ничего не редактируй и не создавай change.md без отдельного подтверждения.
```

Ожидаемый результат:

- карта документов и кода по области;
- список ключевых сущностей, workflow, интеграций;
- gaps и open questions;
- рекомендация следующего шага: `propose`, ручная правка docs branch, refresh `_sdd` или отдельное исследование кода.

Вариант для короткого запроса:

```text
Исследуй область NSIR-245 в master spec invest-dkz через OpenSpec explore.
Найди контекст и вопросы, но не создавай change.
```

## 3. Аналитик меняет несколько документов под Jira

Когда использовать: привычный сценарий Confluence-цветов переносится в Git.

Шаги:

1. Создать ветку `analysis/NSIR-123-short-name`.
2. Изменить Markdown/контракты в `docs-hub`.
3. В commit message указать Jira key.
4. Открыть PR или передать ref разработчику.
5. Попросить агента создать change из diff.

Формулировка для агента:

```text
Создай change из diff master specification.

service=invest-dkz
base_ref=release/2026-07
analyst_ref=analysis/NSIR-123-short-name
change_name=nsir-123-short-name
diff_mode=three-dot
approval=draft

Важно:
- не делай checkout веток;
- анализируй все added/modified/deleted/renamed файлы;
- используй manifest и связанные context files;
- change.md должен описывать смысл изменения, а не просто patch.
```

Ожидаемый результат:

```text
docs-hub/openspec/changes/nsir-123-short-name/change.md
docs-hub/openspec/changes/nsir-123-short-name/.spec-diff/
```

## 4. Аналитик работает через propose

Когда использовать: аналитик не менял документы вручную, но хочет оформить change через интервью с агентом. Это нормальный режим для новых, спорных или плохо очерченных требований.

Формулировка:

```text
Работаем по OpenSpec propose для сервиса invest-dkz.
Нужно оформить change по задаче NSIR-123.

Суть:
<описать бизнес-изменение обычным языком>

Попроси меня уточнить недостающие детали.
Используй master-spec документы сервиса и не создавай технический план реализации в change.md.
Spec update mode пока manual-change.
```

После создания `change.md` аналитик:

- ревьюит смысл;
- отвечает на open questions;
- вручную обновляет документы master specification или делает отдельную docs ветку.

## 5. Разработчик готовит технический проект

Когда использовать: `change.md` согласован.

Формулировка:

```text
Подготовь design.md и tasks.md для change nsir-123-short-name.

Учитывай:
- сервисную архитектуру НСиР;
- код лежит в отдельном репозитории сервиса;
- есть Docker/OpenShift конфигурация;
- нужно обновить автотесты;
- команда без отдельного тестировщика, поэтому добавь сценарий приемки аналитика;
- если Spec update mode = branch-diff, финальный apply-change не делаем, нужен verify source.
```

Что проверить в `design.md`:

- названы code repos и модули;
- описаны изменения БД, интеграций, конфигурации, OpenShift, если они есть;
- нет выдуманных библиотек и технологий;
- есть риски и rollback.

Что проверить в `tasks.md`:

- задачи мелкие;
- есть тесты;
- есть проверка документов;
- есть приемочный сценарий.

## 6. Разработчик реализует tasks

Формулировка:

```text
Реализуй change nsir-123-short-name по tasks.md.
Перед изменениями прочитай change.md и design.md.
Выполняй задачи последовательно, отмечай выполненные чекбоксы.
После реализации запусти релевантные тесты и явно напиши, что не удалось проверить.
```

Если код в другом репозитории, формулировка:

```text
В этом code repo нужно реализовать часть change nsir-123-short-name.
Источник требований: <ссылка или путь на change.md в docs-hub>.
Источник технического плана: <ссылка или путь на design.md/tasks.md>.
Сначала прочитай документы, затем найди затронутые места в коде и выполни только задачи этого репозитория.
```

## 7. Владелец продукта ревьюит change

Формулировка:

```text
Проверь change.md как владелец продукта.
Сфокусируйся на:
- понятна ли бизнес-цель;
- не расширен ли scope;
- достаточно ли критериев приемки;
- есть ли открытые вопросы, которые блокируют разработку;
- можно ли согласовать change.
Не ревьюй техническую реализацию.
```

Ожидаемый output:

```text
Решение: согласовать / вернуть на доработку.
Комментарии:
1. ...
2. ...
```

## 8. Tech lead ревьюит design

Формулировка:

```text
Проверь design.md и tasks.md как tech lead.
Сфокусируйся на:
- затронутых сервисах и code repos;
- миграциях и совместимости;
- интеграциях;
- Docker/OpenShift;
- наблюдаемости;
- тестовом покрытии;
- рисках внедрения и rollback.
```

## 9. Проверка branch-diff source после реализации

Когда использовать: код готов, master-spec документы уже были изменены в ветке аналитика.

Формулировка:

```text
Проверь master-spec source для change nsir-123-short-name.

Spec update mode должен быть branch-diff.
Нужно:
- проверить base_ref и analyst_ref;
- повторить diff summary по master-spec root;
- сравнить с .spec-diff/changed-files.yaml;
- убедиться, что требования из change.md отражены в analyst_ref;
- проверить, нужен ли refresh _sdd;
- если все корректно, перевести change в Реализовано.
```

## 10. Архивация change

Формулировка:

```text
Архивируй change nsir-123-short-name.
Перед архивом проверь статус change.md и наличие незавершенных tasks.
Если есть .spec-diff, сохрани ее вместе с change в archive.
```

## 11. Пример сквозной задачи

### Исходная Jira

```text
NSIR-245: изменить обработку невыясненной суммы при получении уточняющего ответа из внешней системы.
```

### Документы, которые меняет аналитик

```text
docs-hub/invest-dkz/workflow/12.WF_Сохранение_и_анализ_входящего_УРД_УЭСИД_ДЗ/index.md
docs-hub/invest-dkz/integrations/USS/Регистрация_инцидента_в_УСС_ДКЗ/index.md
docs-hub/invest-dkz/model/UesidDz.md
docs-hub/common/cross-service-processes/nsir-investigation-flow.md
```

### Запрос на change

```text
Создай change из diff master specification.
service=invest-dkz
base_ref=release/2026-07
analyst_ref=analysis/NSIR-245-uesid-investigation
change_name=nsir-245-uesid-investigation
diff_mode=three-dot
```

### Запрос на design

```text
Подготовь design.md и tasks.md для nsir-245-uesid-investigation.
Особенно проверь влияние на workflow, модель UesidDz, интеграцию USS, обработчики статусов и автотесты.
```

### Запрос на реализацию

```text
Реализуй задачи по nsir-245-uesid-investigation в репозитории invest-dkz.
Не меняй master-spec документы в code repo.
После кода запусти релевантные тесты и подготовь summary для PR.
```

## 12. Быстрые формулировки

| Задача | Формулировка |
|---|---|
| Объяснить процесс | `Объясни OpenSpec workflow для НСиР и подскажи следующий шаг по этой задаче.` |
| Обновить `_sdd` | `Я изменил документы сервиса invest-dkz, обнови manifest/navigation/coverage.` |
| Исследовать область | `Работаем по OpenSpec explore: исследуй область обработки входящего УЭСИД в invest-dkz, ничего не меняй, подготовь карту контекста и вопросы.` |
| Найти связанные документы | `По задаче NSIR-123 найди в master spec invest-dkz релевантные workflow, модели, интеграции и frontend-документы.` |
| Создать change через интервью | `Работаем по OpenSpec propose для invest-dkz: нужно оформить change по NSIR-123, сначала задай вопросы по скоупу.` |
| Создать change из ветки | `Создай change из diff base_ref=... analyst_ref=... service=... change_name=...` |
| Проверить change | `Проверь change.md на полноту, скрытые gaps и готовность к согласованию.` |
| Создать design/tasks | `Создай design.md и tasks.md по согласованному change ...` |
| Проверить tasks | `Проверь tasks.md: хватает ли тестов, приемки аналитика и verify master-spec source.` |
| Реализовать | `Реализуй первую невыполненную задачу из tasks.md.` |
| Подготовить PR summary | `Составь summary для PR: что изменено, какие тесты запущены, какие документы и change связаны.` |

## 13. Формулировки, которых лучше избегать

Плохо:

```text
Сделай задачу NSIR-123.
```

Почему плохо: нет сервиса, источников, режима работы, scope и критерия завершения.

Лучше:

```text
Работаем по SDD/OpenSpec.
Нужно создать change из diff ветки аналитика для сервиса invest-dkz:
base_ref=release/2026-07
analyst_ref=analysis/NSIR-123-short-name
change_name=nsir-123-short-name
После создания change не переходи к design, остановись на ревью.
```

Плохо:

```text
Обнови документацию и код как считаешь нужным.
```

Лучше:

```text
Сначала проверь, какие master-spec documents затронуты.
Если изменение выходит за scope change.md, зафиксируй open question и остановись.
Код менять только после согласования change и создания tasks.md.
```
