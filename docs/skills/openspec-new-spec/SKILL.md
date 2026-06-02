---
name: openspec-new-spec
description: Создать полную спецификацию сервиса по шаблону service-spec.md — двухэтапное интервью (ЧТО → КАК) с параллельным read-only исследованием кода через 8 специализированных ролей (api, business-logic, data, integrations, config, observability, security, nfr), затем генерация документа. Используй когда пользователь говорит "задокументируй сервис", "сделай спеку для X", "опиши сервис", "нужна спецификация сервиса", "новый service-spec", "описание сервиса с нуля", "новая спека для сервиса", или когда в openspec/specs/ нет файла для обсуждаемого сервиса. НЕ используй для правки существующей спеки — это `openspec-propose`.
license: MIT
compatibility: Требуется openspec/ layout в проекте, доступ к bash/git, AskUserQuestion tool, встроенный tool запуска read-only субагента (имя и subagent_type — по таблице § 3.1 openspec-explore/references/invocation-contract.md). Опционально Serena/LSP/embedding MCP-серверы для анализа кода.
metadata:
  author: openspec-distillate
  version: "4.0"
---

Создать полную спецификацию сервиса — нового проектируемого или уже существующего в коде, но ещё **не описанного в `openspec/specs/`**. Двухэтапное интервью (ЧТО → КАК), затем генерация документа и ревью. Для правки уже существующей спеки используй `openspec-propose`.

**Input**: название сервиса и описание — либо только название, если пользователь хочет описать интерактивно.

**Bundle-пути.**

- Собственные `templates/X` и `references/X` — относительно директории этого скила.
- Внешние файлы из `openspec-explore` — **сосед по директории**, резолв через `<Skill dir>/../openspec-explore/references/<file>`.
- Если harness даёт строку `Skill directory: <abs>` в обёртке активации — используй её как корень для обоих. Например: `Read("<Skill dir>/templates/service-spec.md")`, `Read("<Skill dir>/../openspec-explore/references/invocation-contract.md")`.
- Если строки нет — один раз `Glob("**/openspec-skills/skills/openspec-new-spec/<file>")` и `Glob("**/openspec-skills/skills/openspec-explore/references/<file>")`, бери первый результат.
- Пусто → спроси пользователя путь установки, не ищи вручную по `~`/cwd.

**Bundled materials** (относительно этого SKILL.md):

- `templates/service-spec.md` — шаблон спецификации.
- `references/interview-playbook.md` — сценарий 2 этапов интервью.
- `references/section-guide.md` — форматы разделов, уровни обязательности, чеклист ревью.
- `references/guardrails.md` — роль документа, что запрещено/допустимо.
- `references/common-requirements.md` — логи, коды ошибок, RFC 7807, HTTP-статусы.
- `references/analytics-rules.md` — правила аналитического стиля.
- `references/terms-and-abbreviations.md` — термины и сокращения.

**Внешние references (скил `openspec-explore`, target-specific чтение):**

- `openspec-explore/references/invocation-contract.md` — **канонический контракт вызова субагентов** (tool, subagent_type, алгоритм обнаружения, параллель, fallback, валидация YAML, персистентность). Читай целиком перед шагом 2.
- `openspec-explore/references/research-roles.md` — **промпты ролей** для `target=spec` (`api`, `business-logic`, `data`, `integrations`, `config`, `observability`, `security`, `nfr`). Копируй **дословно** в промпты субагентов.
- `openspec-explore/references/research-orchestration.md` § 3.1 — **группировка ролей** по размеру проекта для `target=spec`.
- `openspec-explore/references/code-analysis-priority.md` — Serena → LSP → embeddings → MCP → Grep/Glob.

**Scope исследования кода.** Лид **не читает код напрямую**. Оркестрирует субагентов по контракту из `openspec-explore/references/invocation-contract.md`. Скил `openspec-explore` как отдельный субагент **не запускается** — этот скил переиспользует его references.

**AskUserQuestion fallback**: если инструмент недоступен в клиенте — задай тот же вопрос обычным текстом и дождись ответа пользователя. Не угадывай.

---

## Steps

### 1. Этап 1 интервью — ЧТО за сервис

Без привязки к коду: потребители, функции, контекст, SLA. Не создавай спеку после одного вопроса. Детали и вопросы — `references/interview-playbook.md` (этап 1).

Из описания выведи kebab-case имя сервиса.

### 2. Изучи код — параллельные read-only субагенты

**Лид НЕ читает исходный код проекта напрямую** (ни Read, ни Grep, ни Glob, ни Serena по `src/**`, `app/**` и т. д.).

**Что разрешено** лиду до запуска субагентов:
- Чтение OpenSpec-артефактов (`openspec/specs/**`, `openspec/changes/**`) — включая шаблон и существующие спеки для ориентира.
- Чтение собственных bundle-файлов (`templates/`, `references/`) и внешних explore-references (`<Skill dir>/../openspec-explore/references/*`).
- Быстрые bash-команды подсчёта файлов (`find … | wc -l`) без чтения содержимого — для классификации размера.

**После возврата субагентов** — точечный Read/Grep только для разрешения конкретной `gap` (≤3 файлов за раз).

**2.1. Прочитай шаблон и образцы.** `templates/service-spec.md` + 1–2 существующие спеки из `openspec/specs/` для ориентира формата.

**2.2. Прочитай канонический контракт.** Целиком: `openspec-explore/references/invocation-contract.md`. Там: обнаружение tool-а и `subagent_type` (§ 3.1), fallback-цепочка (§ 4), адаптация путей под стек (§ 5), валидация YAML (§ 6), дедупликация (§ 7), персистентность (§ 8). Дополнительно § 3 в `openspec-explore/references/research-orchestration.md` — группировка ролей для `target=spec` по размеру проекта.

**2.3. Собери параметры.**

- `target=spec`
- `service=<kebab-case>` (из Этапа 1)
- `roots` — корневые пути кода (спроси пользователя; для монорепо — путь модуля; для одного корня — `.`). Без `roots` не запускайся.
- `thoroughness=quick` (дефолт); `medium` только если пользователь явно сказал «подробнее» и проект крупный.

**2.4. Создай каталог.**

```bash
mkdir -p openspec/specs/<service>/.research
```

**2.5. Классифицируй размер и группу.** По § 2 в `research-orchestration.md`: <50 файлов = Малый (2 агента); 50–500 = Средний (2–3); >500 = Крупный (3–6). Группы ролей — § 3.1 того же файла.

**2.6. Прочитай промпты ролей.** Из `openspec-explore/references/research-roles.md` — секции `api`, `business-logic`, `data`, `integrations`, `config`, `observability`, `security`, `nfr`. **Копируй дословно**, не переформулируй «Цель / Границы / Формат вывода». Подставь реальные пути проекта в плейсхолдеры `<controllers/**>` и т.д. по таблице эвристик § 5 `invocation-contract.md`.

**2.7. Запусти субагентов — один ответ, один блок tool-use, несколько вызовов.**

Имя tool-а запуска субагента и `subagent_type` зависят от harness'а — **обнаружь по алгоритму § 3.1 `invocation-contract.md`** (регекс по списку доступных инструментов) и возьми значения из таблицы там же.

Параметры каждого вызова:
- `subagent_type` — read-only форма из таблицы § 3.1. При ошибке «unknown subagent type» — general-форма + блок «Дополнительные запреты» из § 4.
- `description` — 3–5 слов (`"Research api+business-logic+data"`).
- `prompt` — склеенные промпты ролей группы **дословно** с подставленными путями.
- `task_id` — **не использовать**.

Псевдокод (Малый/Средний — 2 группы; `<TOOL>`/`<ST>` — из § 3.1; реальный вызов через native tool-call API harness'а):

```
<TOOL>(subagent_type="<ST>", description="Research api+business-logic+data", prompt="<api + business-logic + data дословно, пути подставлены>")
<TOOL>(subagent_type="<ST>", description="Research integrations+config+obs+sec+nfr", prompt="<integrations + config + observability + security + nfr дословно>")
```

Последовательные вызовы в разных сообщениях = НЕ параллель, переделай.

Fallback: tool запуска субагента не найден → последовательный sweep по ролям (§ 4 шаг 3 `invocation-contract.md`), запиши в `.research/<role>.yaml` как если бы это сделал субагент.

**2.8. Запиши результаты, валидация, агрегация.**

1. Сохрани каждый возврат субагента в `.research/<role>.yaml` **до** валидации (§ 8.2 вариант A).
2. Валидируй YAML по § 6 (парсится, есть `summary`/`key_files` + доменные коллекции, summary ≤ 200 слов). Невалидно → одна попытка retry-промптом из § 6.
3. Склей коллекции и дедупни по ключам § 7.
4. Запиши `.research/_aggregate.yaml` + `.research-notes.md` (шаблон § 8.4).
5. Собранный `gaps[]` — вопросы для Этапа 2 интервью.

Не выходи со скила, пока 2.7 → 2.8 не выполнены целиком.

### 3. Этап 2 интервью — КАК устроен сервис

Теперь с агрегатом на руках: эндпоинты, бизнес-логика, интеграции, данные, авторизация, конфигурация. Привязывай вопросы к находкам из `.research-notes.md`. Подведи итог ВСЕХ данных и получи явное подтверждение перед переходом дальше. Категории вопросов и правила перехода — `references/interview-playbook.md` (этап 2).

### 4. Запиши спецификацию

Каталог `openspec/specs/<service>/` уже создан на шаге 2.4 (через `mkdir -p .../.research`). Запиши финальный документ в `openspec/specs/<service>/<service>.md`. Удали ВСЕ HTML-комментарии из шаблона, заполни все разделы реальным контентом. Большие файлы — по частям. Форматы блоков, уровни обязательности, ведение больших файлов — `references/section-guide.md`. Роль документа, что запрещено/допустимо — `references/guardrails.md`.

### 5. Ревью

Если доступен tool запуска субагента (имя и `subagent_type` — по таблице § 3.1 `openspec-explore/references/invocation-contract.md`) — запусти отдельный субагент с чеклистом. Иначе проведи ревью самостоятельно. Чеклист ревью и промпт субагента — `references/section-guide.md`. Исправь найденные проблемы и сообщи пользователю.

### 6. Очистка исследовательских артефактов

После успешной записи спеки (шаг 4) и ревью (шаг 5) — удали временные файлы исследования. Они нужны только до момента генерации документа; для будущих changes `openspec-propose` запустит `openspec-explore` заново со свежим состоянием кода.

**Через AskUserQuestion** спроси: удалить `.research/` и `.research-notes.md` или сохранить (если пользователь хочет ручной аудит собранных данных). Дефолт — удалить.

При подтверждении:

```bash
rm -rf openspec/specs/<service>/.research
rm -f openspec/specs/<service>/.research-notes.md
```

Если ревью (шаг 5) выявило проблемы, которые требуют повторного обращения к исследованию — **не чисти**, сначала исправь спеку, потом вернись к шагу 6.

---

## Output

Название сервиса, путь к файлу, список заполненных разделов, ключевые интеграции, результат ревью, статус очистки `.research/`, подсказка про `openspec-propose` для последующих изменений. Формат — в `references/section-guide.md`.

---

## Guardrails

Спецификация — документ системного аналитика, не разработчика. Код приложения запрещён; допустимы SQL DDL/DML, JSON-schema, Protobuf, Avro. Не создавай файл, пока остаются неясности. Полный список правил — `references/guardrails.md`.
