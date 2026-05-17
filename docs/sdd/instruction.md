# Инструкция по SDD-фреймворку проекта

Эта инструкция объясняет идею локального SDD-фреймворка, порядок работы с ним, роли агентов, примеры артефактов и варианты развития. Она дополняет `docs/sdd/workflow.md`, `docs/sdd/gates.md`, `docs/sdd/context-management.md`, `CONVENTIONS.md`, `AGENTS.md` и `QWEN.md`.

## 1. Идея фреймворка

Фреймворк нужен, чтобы разработка с участием AI-агентов не зависела от памяти чата и не превращалась в набор разовых промптов. Основная идея: важные решения, требования, проверки и состояние задачи должны быть записаны в репозитории как обычные версионируемые файлы.

Вместо подхода:

```text
попросили агента -> агент написал код -> человек пытается понять, что произошло
```

используется подход:

```text
зафиксировали спецификацию -> спланировали -> нарезали задачи -> проверили тестами -> реализовали -> провели review -> обновили знания
```

Минимальная цепочка:

```text
specification -> clarification -> plan -> tasks -> tests/contracts -> implementation -> verification -> spec/memory update
```

Фреймворк решает несколько практических проблем:

- агент не додумывает требования, а работает от `REQ-*` и `AC-*`;
- разработчик видит, почему выбран именно такой технический путь;
- reviewer проверяет код не "на вкус", а против спецификации и критериев приемки;
- handoff между агентами или людьми содержит факты, а не пересказ чата;
- повторяемые процедуры оформляются как commands и skills;
- архитектурные решения отделены от текущих задач через ADR.

## 2. Основные принципы

### 2.1 Спецификация важнее чата

Если чат, память инструмента, README и `docs/specs/<id>-<slug>/spec.md` расходятся, для текущей задачи главным источником является спецификация. Если спецификация устарела, ее нужно обновить через gate, а не молча писать код по догадке.

### 2.2 Каждое существенное изменение имеет source of truth

Для новой задачи создается каталог:

```text
docs/specs/<id>-<slug>/
```

Пример:

```text
docs/specs/0002-health-endpoint/
```

Минимальный набор файлов:

```text
spec.md
plan.md
tasks.md
test-plan.md
task-state.md
work-log.md
handoff.md
```

Для более сложной задачи добавляются:

```text
requirements.md
research.md
data-model.md
quickstart.md
contracts/
```

### 2.3 Агент получает context packet, а не весь репозиторий

Агенту передается короткий структурированный пакет: цель, requirement IDs, acceptance criteria, ограничения, релевантные файлы и команды проверки. Это снижает риск, что агент будет опираться на нерелевантный контекст.

### 2.4 Gates останавливают работу до ошибки в коде

Gate - это проверочная точка. Если gate не пройден, работа не продолжается в следующую фазу.

Ключевые gates:

- Spec gate: требования и acceptance criteria понятны.
- Plan gate: технический подход, контракты, тесты и риски записаны.
- Task gate: задачи малы, проверяемы и имеют ownership.
- Implementation gate: код, тесты и `work-log.md` обновлены.
- Review gate: spec, code и tests согласованы.

### 2.5 Reviewer и security reviewer read-only

Роли review не должны исправлять собственные findings в том же проходе. Их задача - независимо найти дефекты, gaps и риски.

## 3. Структура фреймворка

```text
AGENTS.md
QWEN.md
CONVENTIONS.md

docs/
  sdd/
    instruction.md
    constitution.md
    workflow.md
    gates.md
    context-management.md
  specs/
    _template/
    0001-sdd-bootstrap/
  adr/
    _template.md
    0001-record-architecture-decisions.md

.qwen/
  commands/
    sdd/
      specify.md
      clarify.md
      plan.md
      tasks.md
      implement.md
      review.md
  skills/
    sdd-spec-review/
    sdd-plan/
    sdd-task-slice/
    sdd-test-gap/
    sdd-review/
  agents/
    orchestrator.md
    spec-writer.md
    planner.md
    builder.md
    test-engineer.md
    reviewer.md
    security-reviewer.md
```

Назначение слоев:

| Слой | Назначение |
|---|---|
| `AGENTS.md` | Переносимые правила для coding agents |
| `QWEN.md` | Qwen-specific память проекта |
| `CONVENTIONS.md` | Соглашения, команды и Definition of Ready/Done |
| `docs/sdd/` | Governance, gates, workflow и контекстная политика |
| `docs/specs/` | Source of truth для задач |
| `docs/adr/` | Архитектурные решения |
| `.qwen/commands/` | Slash entrypoints для workflow |
| `.qwen/skills/` | Повторяемые процедуры |
| `.qwen/agents/` | Описания ролей и прав агентов |

## 4. Как пользоваться вручную

### Шаг 1. Создать каталог задачи

Выберите ID и slug. ID монотонный, slug короткий и понятный.

Пример:

```text
docs/specs/0002-health-endpoint/
```

Скопируйте шаблон:

```text
docs/specs/_template/* -> docs/specs/0002-health-endpoint/
```

### Шаг 2. Заполнить `spec.md`

Нужно описать, что именно должно измениться и как это проверить. Не пишите технический план в `spec.md`, если он не является требованием.

Пример:

```md
# Feature: Health Endpoint

## Problem

Нужен простой HTTP endpoint для проверки, что приложение поднялось и отвечает.

## Users

- Разработчик
- Локальный smoke-check

## Goals

- Добавить `GET /health`.
- Вернуть стабильный текстовый статус.

## Non-goals

- Не добавлять Spring Boot Actuator.
- Не добавлять authentication.

## Requirements

| ID | Требование |
|---|---|
| REQ-1 | Система должна отвечать `200 OK` на `GET /health`. |
| REQ-2 | Ответ должен быть текстом `OK`. |

## Acceptance Criteria

| ID | Requirement | Критерий |
|---|---|---|
| AC-1 | REQ-1 | MockMvc-запрос `GET /health` возвращает статус `200`. |
| AC-2 | REQ-2 | Тело ответа равно `OK`. |
```

### Шаг 3. Пройти Spec gate

Проверьте:

- есть `REQ-*`;
- есть `AC-*`;
- нет критических `[NEEDS CLARIFICATION]`;
- non-goals ограничивают scope;
- NFR не забыты.

Если есть вопросы, не переходите к плану. Запишите их в `Open Questions`.

### Шаг 4. Заполнить `plan.md`

План отвечает на вопрос "как реализуем".

Пример:

````md
# Plan

## Affected Files

- `src/main/java/com/example/testqwencli/HomeController.java` - добавить endpoint.
- `src/test/java/com/example/testqwencli/TestQwenCliApplicationTests.java` - добавить MockMvc-проверку.

## Public Contracts

- Добавляется `GET /health`, response body `OK`.

## ADR

- ADR не требуется: изменение локальное, зависимостей и архитектурных границ не меняет.

## Verification Commands

```bash
mvn test
```
````

### Шаг 5. Заполнить `tasks.md`

Задачи должны быть маленькими и проверяемыми.

Пример:

```md
| ID | Requirement | Ownership scope | Depends on | Verification | Status |
|---|---|---|---|---|---|
| TASK-1 | REQ-1, REQ-2 | `HomeController.java` | - | `mvn test` | todo |
| TASK-2 | REQ-1, REQ-2 | `TestQwenCliApplicationTests.java` | TASK-1 | `mvn test` | todo |
```

### Шаг 6. Подготовить `test-plan.md`

Пример:

```md
## Traceability Matrix

| Req | Acceptance criteria | Tests | Code | Status |
|---|---|---|---|---|
| REQ-1 | AC-1 | `healthEndpointReturnsOk` | `HomeController.java` | planned |
| REQ-2 | AC-2 | `healthEndpointReturnsOk` | `HomeController.java` | planned |
```

### Шаг 7. Реализовать bounded task

Перед реализацией сформируйте context packet.

Пример:

```md
# Context Packet

## Goal

Реализовать TASK-1 для REQ-1, REQ-2: endpoint `GET /health`.

## Source of truth

- Spec: `docs/specs/0002-health-endpoint/spec.md`
- Plan: `docs/specs/0002-health-endpoint/plan.md`
- Tasks: `docs/specs/0002-health-endpoint/tasks.md`

## Acceptance criteria

- AC-1: `GET /health` возвращает `200 OK`.
- AC-2: тело ответа равно `OK`.

## Constraints

- Не добавлять зависимости.
- Не подключать Actuator.
- Не менять существующий `GET /`.

## Relevant files

- `src/main/java/com/example/testqwencli/HomeController.java`
- `src/test/java/com/example/testqwencli/TestQwenCliApplicationTests.java`

## Verification commands

- `mvn test`
```

### Шаг 8. Обновить `work-log.md`

После проверок запишите evidence.

Пример:

```md
## 2026-05-17

- Реализован `GET /health` для REQ-1, REQ-2.
- Добавлен MockMvc-тест `healthEndpointReturnsOk`.

## Verification Evidence

| Команда | Результат | Заметки |
|---|---|---|
| `mvn test` | passed | 7 tests, 0 failures |
```

### Шаг 9. Review

Reviewer читает только релевантные specs, diff, tests и ADR. Он не исправляет код в этом же режиме.

Формат finding:

```md
## Findings

### High: REQ-2 не покрыт тестом

- Requirement: REQ-2
- Acceptance criteria: AC-2
- File: `src/test/java/.../TestQwenCliApplicationTests.java`
- Problem: тест проверяет только статус, но не тело ответа `OK`.
- Missing check: `andExpect(content().string("OK"))`
```

## 5. Как пользоваться через Qwen commands

Если работа ведется через Qwen CLI, используйте project commands:

```text
/sdd:specify <описание задачи>
/sdd:clarify <id задачи>
/sdd:plan <id задачи>
/sdd:tasks <id задачи>
/sdd:implement <task id>
/sdd:review <id задачи или diff>
```

### `/sdd:specify`

Используйте для создания или обновления `spec.md`.

Пример:

```text
/sdd:specify добавить GET /health для локального smoke-check
```

Ожидаемый результат:

- каталог `docs/specs/0002-health-endpoint/`;
- требования `REQ-*`;
- acceptance criteria `AC-*`;
- open questions или переход к `/sdd:plan`.

### `/sdd:clarify`

Используйте, если в spec есть критические вопросы.

Пример:

```text
/sdd:clarify 0002-health-endpoint
```

Типовые вопросы:

- endpoint должен возвращать text/plain или JSON?
- нужен ли compatibility с существующими endpoints?
- должен ли endpoint раскрывать version/build info?

### `/sdd:plan`

Используйте после Spec gate.

Пример:

```text
/sdd:plan 0002-health-endpoint
```

Ожидаемый результат:

- `plan.md`;
- affected files;
- contracts;
- test strategy;
- ADR required/not required.

### `/sdd:tasks`

Используйте, когда план принят.

Пример:

```text
/sdd:tasks 0002-health-endpoint
```

Ожидаемый результат:

- `tasks.md` с task graph;
- `task-state.md`;
- начальный `handoff.md`, если нужна передача.

### `/sdd:implement`

Используйте для одной bounded task, а не для всей задачи целиком.

Пример:

```text
/sdd:implement 0002-health-endpoint TASK-1
```

Ожидаемый результат:

- измененные файлы только в ownership scope;
- тесты;
- обновленный `work-log.md`;
- команды проверки.

### `/sdd:review`

Используйте после реализации.

Пример:

```text
/sdd:review 0002-health-endpoint
```

Ожидаемый результат:

- findings first;
- test gaps;
- residual risks;
- подтверждение, что spec/code/tests согласованы.

## 6. Как выбирать роли агентов

| Ситуация | Роль |
|---|---|
| Нужно удержать фазу, gates и context packet | `sdd-orchestrator` |
| Нужно сформулировать требования | `sdd-spec-writer` |
| Нужно выбрать технический путь | `sdd-planner` |
| Нужно реализовать ограниченную задачу | `sdd-builder` |
| Нужно закрыть тестовые gaps | `sdd-test-engineer` |
| Нужно проверить diff против спеки | `sdd-reviewer` |
| Нужно проверить security/privacy | `sdd-security-reviewer` |

### Пример разделения ownership

Для задачи `0002-health-endpoint` можно разделить так:

```text
Builder:
  Owns:
    - src/main/java/com/example/testqwencli/HomeController.java
  Must not change:
    - pom.xml
    - application.properties

Test engineer:
  Owns:
    - src/test/java/com/example/testqwencli/TestQwenCliApplicationTests.java

Reviewer:
  Read-only:
    - docs/specs/0002-health-endpoint/**
    - changed files
```

Если два агента пишут в один файл, это уже не независимая параллельная работа. Нужно сузить задачи или выполнить их последовательно.

## 7. Когда можно обойтись без полной спеки

Полный SDD-пакет нужен для существенных изменений: новые endpoints, изменения контрактов, новая бизнес-логика, зависимости, архитектурные решения, security-sensitive work.

Можно работать упрощенно, если:

- изменение механическое и локальное;
- пользователь явно просит quick fix;
- публичное поведение не меняется;
- нет новых рисков безопасности;
- проверка очевидна.

Даже в таком режиме нужно в финальном ответе указать:

- что изменено;
- какая проверка запущена;
- какие риски остались.

Пример упрощенного режима:

```text
Задача: исправить опечатку в README.
Spec: не требуется, direct docs change.
Verification: visual review.
```

## 8. Когда нужен ADR

ADR нужен не для каждой задачи, а для значимых решений.

Создавайте ADR, если:

- меняется публичный API;
- добавляется production-зависимость;
- меняется storage, data model или migration strategy;
- появляется новая security boundary;
- решение сложно откатить;
- есть несколько реальных альтернатив с trade-offs.

Пример ADR-кандидата:

```text
Задача: добавить health endpoint.
ADR: не нужен, изменение локальное.

Задача: подключить Spring Boot Actuator.
ADR: нужен, появляется новая зависимость, endpoints и security considerations.
```

## 9. Варианты улучшения фреймворка

Ниже перечислены практические варианты развития. Их не нужно внедрять все сразу.

### 9.1 Легкий spec lint

Идея: добавить автоматическую проверку Markdown-артефактов.

Что проверять:

- наличие обязательных файлов;
- наличие секций `Problem`, `Goals`, `Requirements`, `Acceptance Criteria`, `NFR`;
- наличие `REQ-*` и `AC-*`;
- отсутствие критических `[NEEDS CLARIFICATION]`;
- задачи в `tasks.md` ссылаются на `REQ-*`;
- `test-plan.md` содержит traceability matrix.

Варианты реализации:

| Вариант | Плюсы | Минусы |
|---|---|---|
| JUnit-тест на структуру | Уже работает через `mvn test` | Неудобно парсить Markdown глубоко |
| Maven plugin/exec script | Можно расширять | Появляется скрипт и правила поддержки |
| GitHub Action | Проверка до merge | Нужна CI-инфраструктура |

Практический следующий шаг:

```text
расширить SddArtifactTests так, чтобы он проверял секции и ID в bootstrap spec
```

### 9.2 Traceability checker

Идея: автоматически проверять цепочку:

```text
REQ -> AC -> TEST -> TASK -> evidence
```

Пример ошибки:

```text
REQ-2 есть в spec.md, но нет ни одного AC.
AC-3 есть в test-plan.md, но не связан с test case.
TASK-4 есть в tasks.md, но не ссылается на requirement ID.
```

Минимальная реализация может быть JUnit-тестом, который читает Markdown как текст и ищет ID по regex.

### 9.3 Context packet compiler

Идея: генерировать context packet автоматически из `spec.md`, `plan.md`, `tasks.md` и `task-state.md`.

Пример команды будущего:

```text
scripts/sdd-context 0002-health-endpoint TASK-1
```

Ожидаемый вывод:

```md
# Context Packet

## Goal
...

## Source of truth
...

## Acceptance criteria
...

## Relevant files
...
```

Польза:

- меньше ручной работы;
- меньше риска забыть ограничения;
- проще передавать задачу агенту.

### 9.4 Contract-first слой

Для REST API можно добавить `contracts/openapi.yaml` в каждую задачу, которая меняет публичный HTTP-контракт.

Пример структуры:

```text
docs/specs/0003-user-api/
  contracts/
    openapi.yaml
```

Правило:

```text
если public API меняется, contract обновляется до реализации
```

### 9.5 CI gate

Когда SDD-процесс стабилизируется, проверки можно вынести в CI.

Минимальный набор:

```text
mvn test
spec lint
traceability check
secret scan
```

Важно: CI не должен заменять human gates для требований, архитектурных решений и acceptance.

### 9.6 Security checklist

Можно добавить отдельный шаблон:

```text
docs/specs/_template/security-review.md
```

Секции:

- secrets;
- authn/authz;
- input validation;
- injection;
- logging privacy;
- dangerous operations;
- dependency risk;
- accepted residual risk.

### 9.7 Multi-agent orchestration

Расширять multi-agent работу стоит только после того, как базовый файловый workflow доказал полезность.

Безопасная модель:

```text
orchestrator -> builder with ownership -> read-only reviewer -> builder fixes findings
```

Рискованная модель:

```text
несколько builder-агентов одновременно меняют один модуль без ownership
```

Правило улучшения:

```text
новая роль агента добавляется только если у нее есть отдельный вход, отдельный выход и понятные permissions
```

### 9.8 Memory promotion и decay

Не каждое наблюдение нужно записывать в `AGENTS.md`.

Модель:

| Факт | Где хранить |
|---|---|
| Одноразовый результат команды | `work-log.md` |
| Состояние текущей задачи | `task-state.md` |
| Архитектурный выбор | `docs/adr/` |
| Повторяемая процедура | `.qwen/skills/` |
| Стабильное правило проекта | `AGENTS.md`, `QWEN.md`, `CONVENTIONS.md` |

Decay: если правило устарело, его нужно удалить или заменить, а не оставлять рядом с новым.

### 9.9 Пилотная задача

Следующий полезный шаг - провести одну небольшую реальную задачу через полный pipeline.

Кандидат:

```text
0002-health-endpoint
```

Почему подходит:

- маленький scope;
- есть endpoint и тест;
- не нужна новая зависимость;
- легко проверить traceability;
- можно отработать `/sdd:specify -> ... -> /sdd:review`.

## 10. Антипаттерны

| Антипаттерн | Почему плохо | Что делать вместо |
|---|---|---|
| Читать весь репозиторий для каждой задачи | Шум и потеря фокуса | Context packet и targeted search |
| Писать код до acceptance criteria | Нечего проверять | Spec gate перед plan/implementation |
| Self-review builder'ом | Высокий риск пропустить свои дефекты | Read-only reviewer |
| ADR на каждую мелочь | Decision log становится шумом | ADR только для значимых решений |
| Один огромный memory-файл | Важное теряется | Слои: specs, ADR, skills, logs |
| Handoff как пересказ чата | Непроверяемо | Handoff как контракт с facts |
| Скрытые gaps в тестах | Ложное чувство готовности | Explicit gaps в `test-plan.md` |
| Multi-agent без ownership | Конфликты и дублирование | Task graph с write scopes |

## 11. Чеклист для новой задачи

Перед реализацией:

- [ ] Создан `docs/specs/<id>-<slug>/`.
- [ ] Есть `REQ-*`.
- [ ] Есть `AC-*`.
- [ ] Критические вопросы закрыты.
- [ ] `plan.md` описывает affected files.
- [ ] Изменения публичных контрактов отражены в contracts или явно отсутствуют.
- [ ] ADR created/not required записано.
- [ ] `tasks.md` содержит ownership scope.
- [ ] `test-plan.md` содержит traceability matrix.

После реализации:

- [ ] Тесты добавлены или обновлены.
- [ ] `mvn test` или релевантная проверка запущена.
- [ ] `work-log.md` содержит evidence.
- [ ] `task-state.md` обновлен.
- [ ] `handoff.md` заполнен, если есть незавершенная работа.
- [ ] Review findings исправлены или приняты явно.

## 12. Минимальный пример полного цикла

Запрос:

```text
добавить GET /health, который возвращает OK
```

Pipeline:

```text
/sdd:specify добавить GET /health, который возвращает OK
/sdd:plan 0002-health-endpoint
/sdd:tasks 0002-health-endpoint
/sdd:implement 0002-health-endpoint TASK-1
/sdd:review 0002-health-endpoint
```

Ожидаемые изменения:

```text
docs/specs/0002-health-endpoint/spec.md
docs/specs/0002-health-endpoint/plan.md
docs/specs/0002-health-endpoint/tasks.md
docs/specs/0002-health-endpoint/test-plan.md
docs/specs/0002-health-endpoint/work-log.md
src/main/java/com/example/testqwencli/HomeController.java
src/test/java/com/example/testqwencli/TestQwenCliApplicationTests.java
```

Ожидаемая проверка:

```bash
mvn test
```

Ожидаемый итог review:

```text
Findings: none
Requirements covered: REQ-1, REQ-2
Tests run: mvn test
Residual risks: endpoint intentionally minimal, no Actuator metadata
```

## 13. Критерии зрелости фреймворка

Фреймворк работает хорошо, если:

- новая задача стартует из шаблона без долгих обсуждений структуры;
- агент может выполнить bounded task по context packet;
- reviewer находит несоответствия spec/code/tests без чтения всего репозитория;
- `work-log.md` позволяет восстановить, что было проверено;
- ADR содержит только значимые решения;
- команды и skills используются повторно, а не переписываются в каждом чате.

Фреймворк требует улучшения, если:

- большую часть времени занимает заполнение шаблонов;
- tasks не помогают разделять работу;
- specs часто устаревают после кода;
- review findings не связаны с requirement IDs;
- `AGENTS.md` снова превращается в огромную свалку правил.

## 14. Diff-driven режим

`diff-driven` режим используется, когда системный аналитик уже внес изменения в проектную документацию, OpenAPI или DBML в отдельном merge request. В этом случае первая SDD-спецификация строится не с нуля, а синтезируется из business requirements, analyst diff и минимального source context.

### 14.1 Когда использовать

Используйте `diff-driven` режим, если вход выглядит так:

```text
business requirements
+ analyst MR with Markdown/OpenAPI/DBML changes
+ release branch
= need synthesized change-spec
```

Не используйте этот режим для маленьких direct code changes, где нет аналитического diff.

### 14.2 Pipeline

```text
/sdd:intake-diff
  -> /sdd:impact-map
  -> /sdd:synthesize-spec
  -> /sdd:clarify
  -> /sdd:plan
  -> /sdd:tasks
  -> /sdd:implement
  -> /sdd:review
```

### 14.3 Артефакты

```text
docs/specs/<id>-<slug>/
  intake.md
  diff-map.md
  impact-map.md
  source-context.md
  contracts/
    openapi-diff.md
    dbml-diff.md
```

`intake.md` фиксирует вход: business requirements, MR, base branch и analyst branch.

`diff-map.md` показывает, какие файлы изменены и какие semantic blocks затронуты.

`impact-map.md` отделяет direct impacts от indirect impacts.

`source-context.md` содержит краткие summaries и source references, а не полные копии документов.

`contracts/openapi-diff.md` и `contracts/dbml-diff.md` отдельно фиксируют изменения контрактов и модели данных.

### 14.4 Пример

Вход:

```text
Base: release/2026.05
Analyst branch: feature/sa-payment-limits
Business requirement: добавить дневные лимиты платежей
```

`diff-map.md`:

```md
| File | Type | Change kind | Direct impact |
|---|---|---|---|
| `docs/payments/limits.md` | markdown | modified | Payment limits rules |
| `docs/api/openapi.yaml` | openapi | modified | `POST /payments` request schema |
| `docs/db/payment.dbml` | dbml | modified | `payment_limits` table |
```

`impact-map.md`:

```md
| Area | Source change | Requirement candidate |
|---|---|---|
| Payment validation | `docs/payments/limits.md` | REQ-1 |
| Payment API | `openapi.yaml#/paths/~1payments/post` | REQ-2 |
| Payment data model | `payment.dbml:payment_limits` | REQ-3 |
```

Синтезированные требования:

```md
| ID | Requirement | Source |
|---|---|---|
| REQ-1 | При создании платежа система должна проверять дневной лимит клиента. | `docs/payments/limits.md` |
| REQ-2 | `POST /payments` должен принимать `limitProfileId`. | `openapi.yaml#/paths/~1payments/post/requestBody` |
| REQ-3 | Лимитные профили должны храниться в таблице `payment_limits`. | `payment.dbml:payment_limits` |
```

### 14.5 Правила качества

- Каждый `REQ-*` должен иметь source reference.
- Каждый `AC-*` должен ссылаться на `REQ-*`.
- OpenAPI changes не смешиваются с DBML changes.
- Synthesized spec не должна включать поведение, которого нет в business requirements или analyst diff.
- Если Markdown, OpenAPI и DBML противоречат друг другу, конфликт фиксируется как open question.
