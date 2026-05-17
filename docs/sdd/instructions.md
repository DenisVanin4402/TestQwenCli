# Инструкция по SDD Фреймворку

**Дата:** 2026-05-17
**Версия:** 1.0
**Статус:** Действующая

---

## Часть 1: Идея фреймворка

### 1.1 Что это такое

SDD (Specification-Driven Development) — это процесс разработки, при котором **спецификация является первичным источником истины**, а не история чата, не комментарии в коде, не устные договорённости.

Проблема, которую решает фреймворк:

> Когда AI-агенты (Qwen, Claude, Copilot) пишут код по запросу в чате, контекст теряется между сессиями. Через неделю никто (включая агента) не помнит:
> - **Почему** было принято то или иное решение?
> - **Что** именно должно делать это требование?
> - **Как** проверить, что реализация соответствует замыслу?

SDD отвечает на эти вопросы через версионируемые Markdown-артефакты в репозитории.

### 1.2 Минимальная формула

```
specification -> plan -> tasks -> tests -> implementation -> verification -> update
```

Каждый этап производит Markdown-файл, который питает следующий этап.

### 1.3 Ключевые принципы

| Принцип | Что означает | Пример |
|---------|-------------|--------|
| Spec — source of truth | Если код, тесты и spec расходятся — spec побеждает | `Cannot implement behavior outside spec` |
| Context packet | Агент получает только релевантные файлы, не весь репозиторий | Spec + plan + 3 relevant source files |
| Human gate | Ключевые решения требует подтверждения человека | Spec approved → plan → tasks → implement |
| Test-first | Тесты пишутся ДО кода для критических путей | AC-1 → expected failing test → implement → test passes |
| Traceability | Каждый REQ → AC → Tests → Code — можно проверить | Матрица трассировки в spec.md |
| Constitution | Непереговорные правила, которые агент не может обойти | `No secrets in artifacts` |

---

## Часть 2: Как пользоваться

### 2.1 Начало новой задачи

**Шаг 1: Определить, к какой фазе относится задача**

| Фаза | Когда использовать | Вход | Выход |
|------|-------------------|------|-------|
| Specify | Есть желание реализовать что-то, но нет spec | User request | `spec.md` |
| Clarify | Spec создан, есть вопросы `[NEEDS CLARIFICATION]` | `spec.md` с вопросами | Ответы, обновлённая spec |
| Plan | Spec утверждена, нужно выбрать технический путь | Утверждённая spec | `plan.md` |
| Tasks | Plan готов, нужно разбить на шаги | Plan + spec | `tasks.md` с графом |
| Implement | Задача из tasks.md готова к выполнению | Task packet | Код + тесты |
| Review | Реализация готова, нужна верификация | Diff + spec | Review report |

**Шаг 2: Создать спецификацию**

Вручную:

```bash
xcopy /E /I docs\specs\_template docs\specs\0002-<имя-фичи>\
```

Через Qwen CLI команду:

```
/sdd:specify Хочу сделать эндпоинт для управления задачами с CRUD операциями
```

Команда автоматически:
- определит следующий номер спеки (`0002-...`)
- скопирует шаблон
- заполнит секции из описания
- создаст REQ и AC IDs
- сообщит сколько `[NEEDS CLARIFICATION]` вопросов требует ответа

**Шаг 3: Пройти через фазы**

```
/sdd:specify   → создаёт спецификацию
/sdd:clarify   → отвечает на вопросы
/sdd:plan      → создаёт технический план
/sdd:tasks     → разбивает на проверяемые задачи
/sdd:implement → реализует задачу за задачей
/sdd:review    → проверяет соответствие spec
```

### 2.2 Формат спецификации — примеры

**Пример 1: Простой health endpoint**

```markdown
# Спецификация: Health Check

## Проблема
Мониторинг не имеет простого способа проверить, работает ли приложение.

## Требования
- REQ-1: GET /health возвращает статус приложения
- REQ-2: Response содержит JSON с полями status и timestamp

## Acceptance Criteria
- AC-1: GET /health → 200 {"status": "UP", "timestamp": "..."}
- AC-2: Response header Content-Type = application/json

## NFR
- Performance: Response < 50ms
- Security: Endpoint доступен без авторизации
```

**Пример 2: CRUD для задач**

```markdown
# Спецификация: Управление задачами

## Проблема
Пользователи не могут создавать и управлять задачами через API.

## Требования
- REQ-1: Создание задачи с заголовком и описанием
- REQ-2: Получение списка задач с фильтрацией по статусу
- REQ-3: Обновление статуса задачи (TODO → IN_PROGRESS → DONE)
- REQ-4: Удаление задачи

## Acceptance Criteria
- AC-1: POST /tasks с валидным телом → 201 + Location header
- AC-2: POST /tasks с пустым title → 400 + error message
- AC-3: GET /tasks → 200 + JSON array
- AC-4: GET /tasks?status=DONE → отфильтрованный список
- AC-5: PATCH /tasks/{id}/status → 200 + обновлённый статус
- AC-6: PATCH /tasks/{id} с несуществующим ID → 404
- AC-7: DELETE /tasks/{id} → 204

## NFR
- Security: JWT token required для всех операций
- Performance: GET /tasks < 200ms для 1000 задач
- Observability: Логировать создание и удаление задач
```

**Пример 3: EARS-формулировки для требований**

```
Ubiquitous (всегда):
The system shall persist every created task with a unique identifier.

Event-driven (по событию):
When a task status changes to DONE, the system shall record the completion timestamp.

State-driven (по состоянию):
While a task is in DELETED state, the system shall reject any update attempts.

Optional feature (условно):
Where soft-delete is enabled, the system shall mark tasks as deleted instead of removing them.

Unwanted behavior (нежелательное):
If the request body is missing the title field, the system shall reject with 400.
```

### 2.3 Контекстный пакет

Перед каждой задачей агент получает структурированный context packet — не весь репозиторий, а только релевантные файлы:

```markdown
# Context Packet

## Цель
Реализовать REQ-1: Создание задачи

## Текущая фаза
Implementation

## Source of truth
- Spec: docs/specs/0002-task-management/spec.md#req-1
- Plan: docs/specs/0002-task-management/plan.md
- ADR: docs/adr/0002-use-h2-database.md

## Acceptance criteria
- AC-1: POST /tasks → 201
- AC-2: POST /tasks invalid body → 400

## Ограничения
- Не менять существующие контроллеры
- Сохранить backward compatibility GET /

## Релевантные файлы
- src/main/java/com/example/testqwencli/HomeController.java

## Текущее состояние
- Сделано: Spec, plan, tasks утверждены
- Не сделано: Код не написан
- Блокеры: None

## Команды верификации
- mvn compile
- mvn test

## Требуемый результат
- Изменённые файлы
- Тесты запущены и прошли
- Spec compliance notes
- Residual risks
```

### 2.4 Пример полного цикла

Добавляем эндпоинт `GET /health` шаг за шагом:

```
# === 1. SPECIFY ===
/sdd:specify Добавить health check эндпоинт GET /health для мониторинга

Агент создаёт docs/specs/0002-health-check/spec.md:
  REQ-1: GET /health → 200 {"status": "UP"}
  AC-1: Response status 200, body содержит status

# === 2. CLARIFY (если нужно) ===
/sdd:clarify

Агент проверяет spec.md:
  [NEEDS CLARIFICATION] Должен ли health endpoint требовать авторизацию?
  [NEEDS CLARIFICATION] Какие дополнительные статусы кроме UP?
  
Пользователь отвечает. Агент обновляет spec.md.

# === 3. PLAN ===
/sdd:plan

Агент создаёт docs/specs/0002-health-check/plan.md:
  Создать HealthController.java с методом health()
  Затронутые файлы: 1 новый файл контроллера
  Альтернатива: добавить в существующий HomeController
  Выбрано: отдельный контроллер для разделения ответственности

# === 4. TASKS ===
/sdd:tasks

Агент создаёт docs/specs/0002-health-check/tasks.md:
  TSK-1: Создать HealthController.java (REQ-1)
  TSK-2: Написать тест для health endpoint (REQ-1)
  Граф зависимостей: TSK-1 -> TSK-2 (или параллельно)

# === 5. IMPLEMENT ===
/sdd:implement TSK-1

Агент выполняет:
  1. Читает spec.md, plan.md, tasks.md
  2. Пишет тест ДО кода (test-first):
     - HealthControllerTest.java: GET /health → expected 200
     - mvn test → FAIL (expected, контроллера нет)
  3. Создаёт HealthController.java с @GetMapping("/health")
  4. mvn test → PASS
  5. Обновляет task-state.md и work-log.md

/sdd:implement TSK-2

Агент:
  1. Добавляет доп. тесты (invalid paths, content-type)
  2. mvn test → all pass
  3. Обновляет task-state.md

# === 6. REVIEW ===
/sdd:review

Агент проверяет:
  - REQ-1 покрыт тестом → pass
  - AC-1 выполняется → pass
  - mvn test → 4 tests, 0 failures (нет регрессий)
  - Review report: all pass

# === DONE ===
Спека завершена. Матрица трассировки обновлена.
```

---

## Часть 3: Структура репозитория

```
repo/
├── AGENTS.md                         # Cross-tool инструкции для AI-агентов
├── CONVENTIONS.md                    # Соглашения проекта, команды, стиль
├── QWEN.md                           # Qwen CLI project memory
│
├── docs/
│   ├── sdd/                          # SDD governance (правила игры)
│   │   ├── constitution.md           # Непереговорные принципы
│   │   ├── workflow.md               # Описание фаз и процесса
│   │   ├── gates.md                  # Definition of Ready/Done
│   │   └── context-management.md     # Политика контекста
│   │
│   ├── specs/
│   │   ├── _template/                # Шаблон для копирования
│   │   │   ├── spec.md               # ← ОБЯЗАТЕЛЬНО заполнять
│   │   │   ├── requirements.md
│   │   │   ├── plan.md
│   │   │   ├── tasks.md
│   │   │   ├── test-plan.md
│   │   │   ├── task-state.md
│   │   │   ├── work-log.md
│   │   │   ├── handoff.md
│   │   │   └── contracts/README.md
│   │   │
│   │   └── 0001-sdd-bootstrap/       # Пример завершённой спеки
│   │
│   └── adr/                          # Architectural Decision Records
│       ├── _template.md
│       └── 0001-record-architecture-decisions.md
│
├── scripts/
│   ├── sdd-lint.sh                   # Spec lint (Linux/Mac)
│   └── sdd-lint.bat                  # Spec lint (Windows)
│
├── .qwen/
│   ├── commands/sdd/                 # Команды: /sdd:specify, /sdd:plan...
│   ├── skills/                       # Навыки: spec-review, test-gap...
│   └── agents/                       # Роли: planner, implementer...
```

---

## Часть 4: Артефакты — подробно

### 4.1 spec.md — Спецификация

**Обязательный** файл. Source of truth для всей задачи.

**Обязательные секции:**

| Секция | Зачем | Пример |
|--------|-------|--------|
| Проблема | Понимание «зачем» | «Пользователи не могут X без Y» |
| Требования REQ-* | ЧТО делать | `REQ-1: Система должна...` |
| Acceptance Criteria AC-* | Как проверить | `AC-1: POST /tasks → 201` |
| Матрица трассировки | Traceability | `REQ-1 → AC-1,AC-2 → Tests → Code → Status` |

**Необязательные, но рекомендуемые:**

| Секция | Зачем |
|--------|-------|
| User Stories | Продуктовое описание для понимания ценности |
| NFR | Кросс-cutting требования (security, perf, etc.) |
| Edge Cases | Граничные сценарии, не очевидные из REQ |
| Open questions | `[NEEDS CLARIFICATION]` — что неясно |

**Правило:** Spec.md — это **ЧТО**, а не **КАК**. Не описывайте классы, методы, библиотеки в spec.md.

### 4.2 plan.md — Технический план

**КАК** сделать то, что описано в spec.

**Обязательные секции:**

```markdown
## Затронутые компоненты
| Компонент | Изменение | Обоснование |
| src/.../HealthController.java | Создан | REQ-1: health endpoint |

## Альтернативы
### Альтернатива 1: Добавить в HomeController
- Плюсы: меньше файлов
- Минусы: смешивает ответственности

## Риски
- Нет рисков
```

**Правило:** План должен быть проверяемым. «Сделать лучше» — не план. «Создать HealthController.java с @GetMapping» — план.

### 4.3 tasks.md — Граф задач

Минимальные проверяемые единицы работы.

**Формат задачи:**

```markdown
### TSK-1: Создать HealthController

REQ: REQ-1
Описание: Создать REST контроллер с GET /health
Вход: spec.md, plan.md
Выход: HealthController.java с методом health()
Зависимости: None
Оценка сложности: S
```

**Граф зависимостей:**

```
TSK-1 -> TSK-2 -> TSK-3
        ↳ TSK-4 (параллельно с TSK-2)
```

**Правила нарезки:**
- Одна задача = одно изменение, которое можно верифицировать
- Не слишком мелко (каждый TSK > 5 минут работы)
- Не слишком крупно (один понятный выход)
- Явные зависимости между задачами

### 4.4 task-state.md — Текущий прогресс

Снимок состояния. Обновлять после каждого значимого изменения.

```markdown
## Текущая фаза
Implementation

## Прогресс
| Задача | Статус | Примечания |
| TSK-1 | done | HealthController.java создан |
| TSK-2 | in_progress | Пишу тесты |

## Что сделано
- HealthController.java с методом health()

## Что не сделано
- Тесты для edge cases

## Запущенные команды
- mvn compile → passed
- mvn test → 1 test, 0 failures
```

### 4.5 work-log.md — Компактный лог

```
2026-05-17 14:30 — Implement — Создан HealthController.java — компиляция pass
2026-05-17 14:35 — Implement — Написан HealthControllerTest.java — 1 test pass
2026-05-17 14:40 — Verify — mvn test → 3 tests, 0 failures, 0 errors
```

### 4.6 handoff.md — Контракт для следующего агента

Не пересказ «я почти закончил», а чёткий контракт:

```markdown
## Следующая роль
Reviewer

## Сделано
- HealthController.java создан
- Базовые тесты написаны

## Не сделано
- Тесты для error cases

## Изменённые файлы
- src/.../HealthController.java (создан)
- tests/.../HealthControllerTest.java (создан)

## НЕЛЬЗЯ менять
- HomeController.java — не part of this task

## Требуемый результат
- Review report с findings
```

---

## Часть 5: Принятые решения и обоснования

### 5.1 Markdown, а не JSON/YAML

**Решение:** Все артефакты в Markdown.

**Почему:**
- Читается человеком без инструментов
- Все AI-агенты нативно работают с Markdown
- Версионируется в git с понятным diff
- Можно вложить в context packet без парсера

**Рассмотренная альтернатива:** JSON Schema / YAML spec
- Плюсы: автоматическая валидация, структурированные данные
- Минусы: менее читаемо, требует парсера, не все агенты хорошо обрабатывают

**Когда reconsider:** Если нужна автоматическая генерация из spec → code.

### 5.2 Директория на спеку, а не один файл

**Решение:** `docs/specs/000N-slug/` — директория с множеством файлов.

**Почему:**
- Разделение: spec ≠ plan ≠ tasks ≠ tests
- Каждый файл читается/обновляется независимо
- Context packet включает только нужные файлы
- Spec.md остаётся компактным и читаемым

**Рассмотренная альтернатива:** Один `spec.md` со всеми секциями
- Плюсы: проще найти, один файл
- Минусы: разрастается до 500+ строк, context packet = всё или ничего

### 5.3 Context packet вместо всего репозитория

**Решение:** Агент получает только релевантные файлы.

**Почему:**
- Контекстное окно ограничено
- «Lost in the Middle»: качество длинного контекста падает к середине
- Больше контекста = больше шума = хуже качество ответов

**Бюджет контекста:**

| Бюджет | Содержимое |
|--------|------------|
| 20-30% | Spec, AC, task-state |
| 20-30% | Релевантные файлы кода |
| 10-20% | ADR, project rules |
| 10-20% | Retrieved docs |
| 10-15% | Tool outputs |
| Reserve | Место для новых tool results |

### 5.4 Test-first для критических AC

**Решение:** Тесты пишутся ДО кода для критических acceptance criteria.

**Почему:**
- Определяет чёткий критерий «сделано»
- Предотвращает over-engineering
- Expected failing test — доказательство, что тест что-то проверяет
- AC гарантированно покрыт

**Исключение:** Unit-тесты для helper-методов можно писать после.

### 5.5 Матрица трассировки в spec

**Решение:** Таблица REQ → AC → Tests → Code → Status.

**Почему:**
- Визуально видно что покрыто
- Reviewer проверяет по матрице
- Spec lint проверяет наличие REQ/AC IDs
- Предотвращает «реализовал 5 из 8 требований»

### 5.6 Разделение команд и навыков

**Решение:** Commands = `/sdd:specify` (действие сейчас), Skills = `sdd-review` (процедура по необходимости).

**Почему:**
- Разные жизненные циклы
- Commands вызываются явно, skills подгружаются по контексту
- Skills могут иметь scripts/templates, commands — нет

### 5.7 Роли агентов с разными permissions

**Решение:** planner, implementer, reviewer, security-reviewer.

**Почему:**
- Изоляция контекста: implementer не думает об архитектуре
- Разные permissions: reviewer read-only
- Чистые контракты через handoff.md

---

## Часть 6: Варианты для улучшения

### 6.1 RAG для больших документов

**Сейчас:** Все документы читаются целиком.

**Проблема:** Когда `docs/` > 100 файлов, context packet не помещается.

**Улучшение:** RAG (Retrieval-Augmented Generation):

```
User: "Какие requirements были для payment-модуля?"
→ RAG ищет в docs/specs/payment-*/
→ Возвращает релевантные чанки
→ Чанки добавляются в context packet
```

**Как реализовать:**
1. Индексировать все `.md` файлы в vector store
2. Перед каждой фазой — query к RAG
3. RAG возвращает релевантные чанки

**Когда нужно:** Когда docs > 100 файлов или каждый doc > 500 строк.

### 6.2 CI-интеграция

**Сейчас:** `sdd-lint.*` запускается вручную.

**Улучшение:** CI-пайплайн:

```yaml
# Пример GitHub Actions
jobs:
  sdd-checks:
    steps:
      - name: SDD Spec Lint
        run: bash scripts/sdd-lint.sh

      - name: Verify Traceability
        run: python scripts/verify-traceability.py

      - name: Check Spec Drift
        run: python scripts/check-spec-drift.py
```

**Когда нужно:** Когда > 2 человека/агента работают параллельно.

### 6.3 Автоматический Drift Detection

**Сейчас:** Spec drift проверяется вручную через `/sdd:review`.

**Улучшение:** Скрипт автоматически находит несоответствия:

```python
# Псевокод
for spec in specs:
    for req in spec.requirements:
        if not has_test_for(req):
            report("REQ без теста:", req.id)
        if not has_code_for(req):
            report("REQ без кода:", req.id)
```

### 6.4 Memory Review / Decay

**Сейчас:** AGENTS.md и QWEN.md растут без чистки.

**Улучшение:** Раз в N итераций:
1. Прочитать AGENTS.md
2. Удалить stale правила
3. Обновить команды если изменились
4. Архивировать неактуальное

### 6.5 Subagent Orchestration

**Сейчас:** Роли агентов — документация, нет автоматического делегирования.

**Улучшение:** Orchestrator автоматически:

```
Orchestrator:
  1. Запускает Planner → plan.md
  2. Запускает Implementer → код + тесты
  3. Запускает Reviewer → review report
  4. Запускает Security Reviewer → security findings
  5. Собирает отчёты → human gate
```

**Риск:** Больше complexity. Не добавлять до Phase 5.

### 6.6 Contract Testing

**Сейчас:** `contracts/` содержит только README.md.

**Улучшение:** Реальные OpenAPI-контракты:

```yaml
# contracts/openapi.yaml
paths:
  /tasks:
    post:
      summary: Создание задачи
      responses:
        '201':
          description: Задача создана
```

Затем:
- OpenAPI diff при spec update
- Contract tests генерируются из openapi.yaml
- Breaking changes требуют ADR

**Когда нужно:** Когда есть multiple API consumers.

### 6.7 Gherkin для всех AC

**Сейчас:** Gherkin рекомендуется для критических AC.

**Улучшение:** Обязательный Gherkin для всех AC:

```gherkin
Feature: Task Management

  Scenario: Создание задачи с валидными данными
    Given система запущена
    When POST /tasks с {"title": "Test"}
    Then response status = 201
    And response body содержит "id"
    And Location header содержит "/tasks/"
```

**Когда нужно:** Когда acceptance tests исполняются автоматически.

---

## Часть 7: Troubleshooting

### 7.1 Spec разрастается

**Симптом:** spec.md > 200 строк, сложно читать.

**Решение:**
- Детали → `requirements.md`
- Технические детали → `plan.md`
- Spec.md = problem + REQ + AC + NFR + матрица

### 7.2 Нет тестов для REQ

**Симптом:** В матрице Tests = «missing».

**Решение:**
1. `/sdd:implement` для соответствующего TSK
2. Или явно задокументировать gap с обоснованием

### 7.3 Контекстное окно заполняется

**Симптом:** Агент «забывает» spec, задаёт вопросы на уже отвечённые.

**Решение:**
1. Обновить `task-state.md` и `work-log.md`
2. Запустить `/clear` для сброса чата
3. Пересоздать context packet из файлов
4. Если нужно — запустить subagent

### 7.4 Конфликт: код делает X, spec говорит Y

**Симптом:** Reviewer нашёл mismatch.

**Решение:**
1. Остановить implementation
2. Определить: код неверен или spec?
3. Код неверен → исправить, добавить тест
4. Spec неверен → обновить spec через human gate
5. Никогда не «подгонять» spec под код без gate

### 7.5 AGENTS.md слишком большой

**Симптом:** > 300 строк, агент игнорирует часть инструкций.

**Решение:**
1. Stable правила → AGENTS.md (макс 10-15 правил)
2. Специфичные правила → scoped rule или skill
3. Удалить deprecated

---

## Часть 8: Быстрые справочники

### 8.1 Checklist начала новой спеки

- [ ] Скопировать `docs/specs/_template/` в `docs/specs/000N-slug/`
- [ ] Заполнить problem, goals, users, non-goals
- [ ] Написать REQ-* с уникальными ID
- [ ] Написать AC-* с уникальными ID
- [ ] Добавить NFR секции
- [ ] Заполнить матрицу трассировки
- [ ] Запустить spec lint: `scripts/sdd-lint.bat`

### 8.2 Доступные команды

| Команда | Фаза | Что делает |
|---------|------|------------|
| `/sdd:specify` | Specify | Создаёт спецификацию |
| `/sdd:clarify` | Clarify | Уточняет неоднозначности |
| `/sdd:plan` | Plan | Технический план из spec |
| `/sdd:tasks` | Task | Граф задач |
| `/sdd:implement` | Implement | Реализация с тестами |
| `/sdd:review` | Review | Верификация против spec |

### 8.3 Доступные навыки

| Навык | Когда | Что делает |
|-------|-------|------------|
| `sdd-spec-review` | Перед implement | Проверяет spec completeness |
| `sdd-plan` | На фазе Plan | Создаёт plan.md из spec |
| `sdd-task-slice` | После plan | Разбивает plan на задачи |
| `sdd-test-gap` | После tests | Находит непокрытые AC |
| `sdd-review` | После implement | Ревью реализации |

### 8.4 Правила для AI-агентов

1. **Spec — source of truth.** Не реализуем поведение вне spec.
2. **Test-first.** Тесты ДО кода для критических AC.
3. **Минимальные изменения.** Не делать extra за пределами задачи.
4. **Конституция.** Нарушил → стоп, сообщи, жди разрешения.
5. **Нет секретов.** Никогда не пиши секреты в код/логи/артефакты.
6. **Отчёт.** Завершил задачу → обновить task-state + work-log.
7. **Gate.** Не пропускай гейт без явного разрешения пользователя.
8. **Context packet.** Получи релевантные файлы до начала работы.

---

## Часть 9: Constitution — непереговорные правила

Из `docs/sdd/constitution.md` — правила, которые **нельзя обойти**:

1. **Безопасность и приватность** — часть спецификации, не постфактум.
2. **Нельзя менять публичные контракты без обновления** артефактов и согласования.
3. **Никаких новых зависимостей без обоснования.**
4. **Нельзя реализовывать при `[NEEDS CLARIFICATION]`** для критических путей.
5. **Тесты и доказательства при каждой задаче.**
6. **Source of truth побеждает чат** — артефакты важнее истории переписки.
7. **Никаких секретов в артефактах.**
8. **Агенты работают с минимальными правами.**
9. **Reviewer по умолчанию read-only.**

При нарушении любого правила:
1. Агент останавливает выполнение
2. Фиксирует нарушение в task-state.md и work-log.md
3. Сообщает пользователю и ждёт явного разрешения
