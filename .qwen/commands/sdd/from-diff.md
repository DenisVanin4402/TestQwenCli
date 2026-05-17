---
description: Сгенерировать delta-spec из git diff MR аналитика (branch или commit compare).
---

# /sdd:from-diff

Сгенерировать delta-spec — спецификацию изменений — на основе git diff двух веток или коммитов.
Альтернативный entrypoint SDD-процесса: вместо «user request → spec с нуля» используется
«MR-изменения → delta-spec (only delta)».

## Входные данные

Пользователь указывает ветки или коммиты для сравнения: {{args}}

Примеры:
- `main feature-analyst-2024` — сравнение веток
- `abc123def feature-xyz` — сравнение коммитов
- `release/v1.2 hotfix/login` — release и hotfix ветки

## Что читать

1. `docs/sdd/constitution.md` — проверить конституционные ограничения
2. `docs/sdd/workflow.md` — понять фазу и entrypoint «from diff»
3. `docs/specs/_template/delta-spec.md` — шаблон delta-spec
4. `docs/specs/` — существующие спеки, проверить дубликаты и затронутые
5. `docs/adr/` — существующие архитектурные решения
6. `AGENTS.md` — правила для работы с delta-spec

## Процедура

### Шаг 1: Запуск diff-extractor

Извлечь параметры из {{args}}:
- `ref1` — первая ветка/коммит (базовая)
- `ref2` — вторая ветка/коммит (с изменениями)
- `target-dir` — опционально (директория новой спеки)

Запустить скрипт:

```bash
# Linux/macOS
bash scripts/sdd-diff-extract.sh <ref1> <ref2> [<target-dir>]

# Windows
scripts\sdd-diff-extract.bat <ref1> <ref2> [<target-dir>]
```

Скрипт создаёт:
- `diff-summary.md` — структурированное резюме изменений с классификацией
- `impact-map.md` — карта прямого и косвенного влияния

### Шаг 2: Чтение результатов анализа

Прочитать и проанализировать:
- `diff-summary.md` — какие файлы изменены, классификация (контракты/требования/код/конфиги), статистика
- `impact-map.md` — затронутые компоненты, косвенное влияние, связанные спеки и ADR
- Изменённые файлы напрямую — прочитать содержимое для понимания контекста изменений

### Шаг 3: Определение ID спеки

Определить следующий доступный ID: `000N-slug` (где slug — краткое описание изменений).
Если `target-dir` был указан — использовать его.

### Шаг 4: Генерация delta-spec

Создать `docs/specs/000N-slug/delta-spec.md` на основе `delta-spec.md` шаблона:

- Заполнить метаданные (источник MR, ветки, дата)
- Перенести таблицу «Источник изменений» из diff-summary.md
- Сформулировать «Проблема» на основе анализа изменённых .md файлов
- Извлечь НОВЫЕ требования из diff — REQ-NEW-* с понятными описаниями
- Извлечь НОВЫЕ acceptance criteria — AC-NEW-*
- Описать ИЗМЕНЁННЫЕ NFR (если затронуты)
- Перечислить затронутые существующие требования (из impact-map)
- Заполнить матрицу трассировки (delta)
- Заполнить «Связанные документы» и «Impact Map summary»
- Выявить [NEEDS CLARIFICATION] вопросы

### Шаг 5: Генерация plan.md

Создать `docs/specs/000N-slug/plan.md`:
- Затронутые компоненты (из diff-summary)
- Подход к реализации delta-изменений
- Риски, выявленные из impact-map
- Ссылка на delta-spec как source of truth

### Шаг 6: Генерация tasks.md

Создать `docs/specs/000N-slug/tasks.md`:
- Разбить delta-spec на проверяемые задачи
- Каждая задача ссылается на REQ-NEW-*
- Граф зависимостей задач

### Шаг 7: Создание/обновление task-state.md

Создать `docs/specs/000N-slug/task-state.md`:
- Текущая фаза: «Specify — delta-spec сгенерирован»
- Прогресс: все задачи из tasks.md как pending
- Список созданных файлов

## Разрешено изменять

- `docs/specs/000N-slug/delta-spec.md` — создание delta-spec
- `docs/specs/000N-slug/plan.md` — создание плана
- `docs/specs/000N-slug/tasks.md` — создание задач
- `docs/specs/000N-slug/task-state.md` — создание состояния
- `diff-summary.md` — создание, если нет
- `impact-map.md` — создание, если нет

## ЗАПРЕЩЕНО изменять

- Существующий код в `src/`
- Существующие спеки (только чтение)
- `docs/sdd/constitution.md`
- `pom.xml`
- Секреты, токены, пароли

## Gate

Остановиться после создания delta-spec, plan.md, tasks.md, task-state.md.
Сообщить пользователю:

- Сгенерированные файлы (delta-spec.md, plan.md, tasks.md, task-state.md)
- Количество REQ-NEW-* и AC-NEW-* элементов
- Затронутые существующие требования
- Сколько `[NEEDS CLARIFICATION]` вопросов требует ответа
- Предложить следующий шаг:
  - `/sdd:clarify` — уточнить неоднозначности
  - Продолжить pipeline: `plan → tasks → tests → impl → review`

## Важные правила

- Delta-spec содержит ТОЛЬКО изменения, не полную документацию проекта
- При слиянии в основной spec REQ-NEW-* номера заменяются на сквозные REQ-N
- Delta-spec является source of truth для реализации delta-изменений
- Если diff пустой — сообщить пользователю и не создавать файлы
