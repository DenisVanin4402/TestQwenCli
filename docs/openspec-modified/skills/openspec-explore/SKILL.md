---
name: openspec-explore
description: Structured research кодовой базы под OpenSpec. Запускает параллельных read-only субагентов по специализированным ролям (api, business-logic, data, integrations, config, observability, security, nfr для target=spec; feature-scope, dependencies, cross-cutting для target=change), агрегирует YAML и сохраняет в `openspec/**/.research/`. Используй напрямую когда нужно разовое картирование сервиса или зоны изменения. Дополнительно работает как prompt-library для `openspec-propose`.
license: MIT
compatibility: Требуется доступ к bash/git и встроенный tool запуска read-only субагента (имя и subagent_type — по таблице § 3.1 references/invocation-contract.md). Опционально Serena/LSP/embedding MCP-серверы — сильно повышают качество исследования.
metadata:
  author: openspec-distillate
  version: "4.0"
---

Structured research кодовой базы — **не модифицирует исходный код проекта**; сохраняет собранные метаданные и сводку в `openspec/**/.research/`. Картирует сервис или зону изменения силами параллельных специализированных субагентов, агрегирует их вывод и возвращает его вызывающему скилу. Conversational-режим в этой версии **не поддерживается** — для размытого обсуждения используй обычный чат без скила.

**Две роли скила:**

1. **Standalone** — пользователь зовёт напрямую («картируй сервис X», «research codebase»). Тогда лид этого скила сам принимает параметры, запускает субагентов и возвращает сводку пользователю.
2. **Prompt-library для `openspec-propose`** — этот skill не вызывает `openspec-explore` как субагента. Он читает `references/research-roles.md` и `references/invocation-contract.md` (или `references/research-orchestration.md`) из каталога `openspec-explore` и оркестрирует субагентов сам. Контракт вызова — в `references/invocation-contract.md`.

**Bundle-пути.** `references/X` — относительно директории этого скила. Если harness даёт строку `Skill directory: <abs>` в обёртке активации — используй её. Иначе один раз `Glob("**/openspec-skills/skills/openspec-explore/references/<file>")` и возьми первый результат. Пусто → спроси пользователя путь установки, не ищи вручную.

**Bundled**: справочники `references/research-orchestration.md` (классификация, оси, группировка, агрегация, fallback, таблица «раздел → поля» в § 6.1), `references/research-roles.md` (11 промпт-шаблонов ролей с YAML-контрактами), `references/invocation-contract.md` (правила вызова для лидов: пути, fallback, эвристики путей по стекам, валидация YAML, дедуп), `references/code-analysis-priority.md`, `references/openspec-awareness.md`.

---

## Вход

Параметры, которые должны быть известны ДО запуска исследования:

- **target**: `spec` (картируем сервис для folder-based master specification) или `change` (картируем зону изменения для change.md).
- **service**: имя сервиса (kebab-case) и/или корневые пути, которые относятся к сервису.
- **anchor** (только для `target=change`): пути или символы, попадающие в скоуп изменения.
- **thoroughness** (опционально): `quick` (default) / `medium`.

Если параметры не переданы — запроси у пользователя (или у вызывающего скила). Без трёх ключевых (`target`, `service`, + `anchor` для change) не запускайся.

---

## Шаги

1. **Осведомлённость об OpenSpec.** Быстро проверь active changes и наличие master-spec folder с `_sdd/manifest.yaml`. Подробности — [`references/openspec-awareness.md`](references/openspec-awareness.md).

2. **Классифицируй размер.** Быстрые bash-команды подсчёта файлов и модулей → Малый / Средний / Крупный. Таблица и правила — [`references/research-orchestration.md` § 2](references/research-orchestration.md).

3. **Выбери роли и группировку.** По `target` и размеру подбери набор субагентов из каталога:
   - `target=spec` → роли `api`, `business-logic`, `data`, `integrations`, `config`, `observability`, `security`, `nfr`.
   - `target=change` → роли `feature-scope`, `dependencies`, `cross-cutting`.

   Группировка по числу агентов — [`references/research-orchestration.md` § 3](references/research-orchestration.md). Полные промпт-шаблоны ролей — [`references/research-roles.md`](references/research-roles.md).

4. **Адаптируй границы.** Для каждой выбранной группы подставь реальные пути проекта в плейсхолдеры `<dir/**>` из шаблона роли. Таблица эвристик по стекам (Spring / Go / FastAPI / Django / Express / .NET / Rails) — [`references/invocation-contract.md` § 5](references/invocation-contract.md). Пути между группами не должны пересекаться.

5. **Создай каталог для результатов (до запуска субагентов).**

   ```bash
   # target=spec
   mkdir -p openspec/<service>/.research
   # target=change
   mkdir -p openspec/changes/<name>/.research
   ```

6. **Запусти субагентов параллельно.**

   **Tool и `subagent_type`** — имя и форма зависят от harness'а и регистрочувствительны. **Полная таблица возможных имён, алгоритм обнаружения (регекс по списку доступных инструментов), fallback при «unknown subagent type»** — в [`references/invocation-contract.md § 3.1–§ 4`](references/invocation-contract.md). Если ни один подходящий tool не найден — переключись на sequential fallback-sweep из [`references/research-orchestration.md § 8`](references/research-orchestration.md), пропусти параллельный запуск.

   **Параметры вызова** (одинаковы для всех harness'ов):
   - `subagent_type` — read-only форма из § 3.1.
   - `description` — 3–5 слов (`"Research api+business-logic+data"`).
   - `prompt` — текст роли из [`references/research-roles.md`](references/research-roles.md) **дословно**, с подставленными путями.
   - `task_id` — **не использовать** (one-shot research).

   **Параллелизм** — несколько вызовов инструмента **в одном ответе, в одном блоке tool-use**. Последовательные вызовы в разных сообщениях = не параллель, переделай.

   Псевдокод (`<TOOL>`/`<ST>` — из § 3.1; реальный вызов через native tool-call API harness'а):

   ```
   # target=spec, Малый/Средний — 2 группы
   <TOOL>(subagent_type="<ST>", description="Research api+business-logic+data", prompt="<роли api + business-logic + data, пути подставлены>")
   <TOOL>(subagent_type="<ST>", description="Research integrations+config+obs+security+nfr", prompt="<роли integrations + config + observability + security + nfr, пути>")

   # target=change — 2 группы
   <TOOL>(subagent_type="<ST>", description="Research feature-scope+cross-cutting", prompt="<роли feature-scope + cross-cutting, пути и anchor>")
   <TOOL>(subagent_type="<ST>", description="Research dependencies", prompt="<роль dependencies, пути>")
   ```

   Промпты ролей берутся **дословно** из `references/research-roles.md` — «Цель», «Границы», «Формат вывода» не переформулируй, иначе ломается агрегация.

7. **Собери YAML-выводы.** После возврата всех субагентов — пройдись по каждой роли, склей поля в сводные коллекции, сними дубли по ключам (endpoints: method+path, entities: name, params: name, metrics: name). Инструкции — [`references/research-orchestration.md` § 6](references/research-orchestration.md).

8. **Проверь полноту**. Для каждой области целевого артефакта (master-spec folder или change.md) — есть ли хотя бы одна запись? Область без данных — это gap, фиксируется в общий список gaps.

9. **Запиши результат и верни ссылки.** Персистентность — **обязательно**, не по ситуации:
   - `target=spec`: пиши в `openspec/<service>/.research/<role>.yaml` (по ролям) + `openspec/<service>/.research/_aggregate.yaml` + `openspec/<service>/.research-notes.md`.
   - `target=change`: пиши в `openspec/changes/<name>/.research/<role>.yaml` + `.research/_aggregate.yaml` + `.research-notes.md`.

   Формат `.research-notes.md` — в `references/invocation-contract.md § 8.4`. Каталог `.research/` уже создан на шаге 5.

   Верни вызывающему **короткую сводку + путь к `.research-notes.md`**, не сырой YAML. Вызов от пользователя → покажи ту же сводку и предложи `openspec-init-master-spec` / `openspec-propose` (см. `references/openspec-awareness.md`).

---

## Выход

Два артефакта (на диске) + сводка (в ответе):

1. `<research-dir>/.research/_aggregate.yaml` — машинный агрегат со всеми коллекциями (`endpoints`, `rules`, `entities`, `integrations`, `params`, `log_events`, `metrics`, `auth_mechanisms`, `callers`, `breaking_risk`, `migration_plan`, `acceptance_hints`, …) + общий `gaps[]`.
2. `<research-dir>/.research-notes.md` — человекочитаемая сводка (что нашли, статистика, список gaps, ссылки на файлы по ролям).

Формат ответа вызывающему (скилу или пользователю):

- Путь к `.research-notes.md` и `.research/_aggregate.yaml`.
- Краткая сводка (N endpoints, M entities, K интеграций, P config params).
- Список `gaps` как приоритизированные вопросы для Этапа 2 интервью.

**Не вставляй сырой YAML в ответ вызывающему** — данные уже на диске. Переполнение контекста недопустимо.

---

## Ограничения

- **Не трогает исходный код проекта.** Не создаёт и не редактирует OpenSpec-артефакты (`change.md`, `design.md`, `tasks.md`, master-spec documents). Единственные записи — `.research/*.yaml` и `.research-notes.md` внутри `openspec/**/`. Фиксация артефактов — только через соответствующий skill (`openspec-init-master-spec`, `openspec-propose`, `openspec-design`).
- **Без conversational-режима.** Если пользователь пришёл с размытой идеей без `target`/`service` — задай уточняющие вопросы и направь в подходящий skill (`openspec-propose` или `openspec-init-master-spec`). Не пытайся «просто подумать вслух» в рамках этого skill.
- **Рекурсия субагентов запрещена.** Субагент не имеет права звать другого.
- **Границы субагентов не пересекаются.** Если зоны пересекаются — укрупни задачи, уменьши число агентов.
- **Лид не читает сырые файлы после запуска субагентов.** Работай с YAML-сводками. Точечный Grep/Read допустим только для уточнения конкретной gap.
- **Fallback если tool запуска субагента недоступен.** Снижай класс размера на ступень и делай sequential sweep по каталогу ролей. Детали — [`references/research-orchestration.md` § 8](references/research-orchestration.md).
