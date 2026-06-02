# Оркестрация исследования

Инструкция для лид-агента: как провести structured research кодовой базы под целевой артефакт (service-spec.md или change.md), чтобы собрать достаточно данных для заполнения всех разделов и не спалить окно контекста.

Правила написаны так, чтобы их могла применять слабая модель (Qwen Coder Next 9B, окно 200к): числа заданы явно, промпты — в [research-roles.md](research-roles.md) как готовые шаблоны, рекурсия субагентов запрещена.

---

## 1. Вход

Лид-агент (`openspec-new-spec` или `openspec-propose`) передаёт:
- **target**: `spec` или `change`
- **service / scope**: имя сервиса или зона изменения (набор каталогов / пакетов)
- **anchor files** (для change): путь целевой спеки, затронутые файлы, если уже известны

Без этих трёх параметров исследование не запускается — сначала уточняем у пользователя.

---

## 2. Классификация размера

Быстрые команды (стек под проект):

```bash
find . -type f \( -name "*.java" -o -name "*.kt" -o -name "*.py" -o -name "*.go" -o -name "*.ts" -o -name "*.js" \) \
  | grep -v -E "(node_modules|build|dist|target|\.git)" | wc -l
ls -1 src 2>/dev/null || ls -1
```

| Уровень | Критерий | Кол-во субагентов |
|---------|----------|-------------------|
| **Малый** | <50 файлов кода ИЛИ 1 модуль/пакет | **2** параллельных субагента (минимум — для разделения «API + BL + data» и «integrations + config + obs + security + nfr») |
| **Средний** | 50–500 файлов ИЛИ 2–10 модулей | **2–3** параллельных субагента |
| **Крупный** | >500 файлов ИЛИ multi-module / multi-repo | **3–6** параллельных субагентов + фаза агрегации |

> **Субагенты обязательны всегда, когда tool запуска субагента доступен.** Вариант «0 агентов — читаю сам» допустим **только** в fallback-режиме (см. § 8: tool запуска субагента физически недоступен). Причина — лид не должен тянуть исходный код в своё окно: ломается дедупликация, растёт цена, рассинхрон с YAML-контрактом.

Границы нестрогие: 200 файлов без внутренних границ = Средний (2 агента); 80 файлов с 5 bounded-context = Средний.

---

## 3. Оси декомпозиции

Выбор оси зависит от `target`:

### 3.1. `target=spec` — ось «по слоям»

Каталог ролей для новой/существующей спеки сервиса. Полный список — [research-roles.md § «Роли для spec»](research-roles.md).

| Роль | Целевые разделы service-spec.md |
|------|---------------------------------|
| `api` | 2.1, 2.4, 2.9, 2.10, 2.11 |
| `business-logic` | 2.1, 2.2, 2.3, 3 |
| `data` | 2.6 |
| `integrations` | 2.5 |
| `config` | 2.7 |
| `observability` | 2.8, 4.4, 4.5 |
| `security` | 4.1 |
| `nfr` | 4.2, 4.3 |

**Группировка по размеру проекта**:

| Размер | Группы (каждая = один субагент) |
|--------|---------------------------------|
| Малый (2 агента) | [api + business-logic + data], [integrations + config + observability + security + nfr] |
| Средний (2 агента) | [api + business-logic + data], [integrations + config + observability + security + nfr] |
| Средний (3 агента) | [api + business-logic], [data + integrations], [config + observability + security + nfr] |
| Крупный (4 агента) | [api + business-logic], [data + integrations], [config + observability], [security + nfr] |
| Крупный (5 агентов) | [api + business-logic], [data], [integrations], [config + observability], [security + nfr] |
| Крупный (6 агентов) | [api], [business-logic], [data], [integrations], [config + observability], [security + nfr] |

### 3.2. `target=change` — ось «по зоне изменения»

Каталог ролей для change.md. Полный список — [research-roles.md § «Роли для change»](research-roles.md). Нумерация разделов — по `templates/change.md` (16 разделов).

| Роль | Целевые разделы change.md |
|------|---------------------------|
| `feature-scope` | 2 (бизнес-логика), 6 (модели данных), 7 (интеграции), 10 (валидация), 16 (критерии приёмки по текущему поведению) |
| `dependencies` | 1 (затронутые компоненты), 7 (MODIFIED/REMOVED), 12 (миграция: шаги/совместимость/откат), 16 (критерии приёмки по breaking-влиянию) |
| `cross-cutting` | 8 (ошибки), 9 (хедеры), 11 (безопасность), 13 (логи), 14 (метрики), 15 (конфиг), 16 (критерии приёмки по cross-cutting) |

**Группировка по размеру**:

| Размер | Группы |
|--------|--------|
| Малый (2) | [feature-scope + cross-cutting], [dependencies] |
| Средний (2) | [feature-scope + cross-cutting], [dependencies] |
| Крупный (3) | [feature-scope], [dependencies], [cross-cutting] |

---

## 4. Запуск

1. **Выбери роли** по `target` и размеру (таблица выше).
2. **Для каждой группы** — один субагент. Промпт берётся из [research-roles.md](research-roles.md) по имени роли. Если группа — несколько ролей, склей их YAML-контракты в один промпт с пометкой `## Роли`.
3. **Запуск в одном батче** tool-вызовов — несколько вызовов встроенного tool'а запуска субагента из harness'а в одном ответном сообщении. Имя tool-а и `subagent_type` **зависят от harness'а и регистрочувствительны** — таблица известных имён и алгоритм обнаружения (регекс по доступным инструментам) в [invocation-contract.md § 3.1](invocation-contract.md). Параметры: `description=<короткое>`, `prompt=<текст роли>`, `subagent_type=<read-only форма>`. Последовательный запуск в разных сообщениях = не параллель.
4. **Tool subagent_type**:
   - Если доступен `Explore` (встроенный read-only субагент для анализа кода) — используй его.
   - Иначе — `general-purpose` с явным запретом записи в промпте.
5. **Thoroughness**: `quick` по умолчанию. `medium` — только если пользователь прямо сказал «подробнее» и проект крупный.

---

## 5. Инвариант scope

- Каждому субагенту — **непересекающийся набор путей**. Если зоны перекрываются — укрупни задачи, уменьши число агентов.
- Зоны задаются явно в промпте (`Границы: читай только <dir/**>, <dir2/**>`). Общие пути (`src/**`) допустимы только когда модулей мало и разделение бессмысленно.

---

## 6. Агрегация

После возврата всех YAML — один проход лид-агента:

1. **Склей поля по ролям** в единые коллекции:
   - spec: `flows[]`, `endpoints[]`, `rules[]`, `entities[]`, `schemas[]`, `migrations[]`, `integrations[]` (kafka/grpc/http/db/cron), `params[]`, `env_vars[]`, `log_events[]`, `metrics[]`, `calculated_metrics[]`, `health_checks[]`, `audit[]`, `auth_mechanisms[]`, `authorization[]`, `tls`, `masking_rules[]`, `performance.*`, `reliability.*`.
   - change: `current_implementation.*`, `schemas_in_scope[]`, `ddl_in_scope[]`, `callers[]`, `breaking_risk[]`, `migration_plan.*`, `migration_hints[]`, `acceptance_hints[]` (из всех трёх ролей), `errors_in_scope[]`, `headers_in_scope[]`, `log_events_in_scope[]`, `metrics_in_scope[]`, `config_params_in_scope[]`.
2. **Сними дубли** по ключам: endpoints → `method+path`, entities → `name`, params → `name`, metrics → `name`, flows → `id`, acceptance_hints → `when+then`, migrations → `file+version`.
3. **Сводные gaps** — объединить все `gaps[]`. Это вопросы к Этапу 2 интервью.
4. **Проверка полноты**: для каждого раздела целевого артефакта (service-spec.md или change.md) — есть ли хотя бы одна запись? Если раздел пуст — это gap, не отсутствие данных. Спецификация проверки — § 6.1 ниже.

Результат агрегации возвращается в вызывающий скил (`new-spec` / `propose`) в виде единого YAML или markdown-сводки — его формат определяет вызывающий скил.

### 6.1. Таблица «раздел артефакта → поля агрегата»

**service-spec.md** (target=spec):

| Раздел | Поля агрегата |
|--------|---------------|
| 2.1 Основной сценарий | `flows[]` (kind=main) |
| 2.2 Альтернативные/ошибочные | `flows[]` (kind=alternative|error), `edge_cases[]` |
| 2.3 Бизнес-правила | `rules[]`, `state_machines[]` |
| 2.4 API | `endpoints[]`, `openapi_specs[]`, `graphql_schemas[]`, `proto_files[]` |
| 2.5 Интеграции | `kafka[]`, `grpc_clients[]`, `grpc_servers[]`, `http_clients[]`, `soap_clients[]`, `databases[]`, `cron[]`, `sse_streams[]` |
| 2.6 Модели данных | `entities[]`, `schemas[]`, `migrations[]` |
| 2.7 Конфигурация | `params[]`, `env_vars[]` |
| 2.8 Логирование | `log_events[]` |
| 2.9 Валидация | `validations[]` |
| 2.10 Обработка ошибок | `endpoints[].errors[]` |
| 2.11 Хедеры | `endpoints[].headers[]` |
| 3. Критерии приёмки | `acceptance_hints[]` |
| 4.1 Безопасность | `auth_mechanisms[]`, `authorization[]`, `tls`, `masking_rules[]` |
| 4.2 Производительность | `performance.request_limits`, `performance.db_pool`, `performance.limits[]`, `performance.timeouts_global[]`, плюс `integrations.*.timeout_ms` |
| 4.3 Надёжность | `reliability.graceful_shutdown`, `reliability.probes`, `reliability.replicas`, `reliability.retry_policies[]`, `reliability.circuit_breakers[]`, `reliability.idempotency[]` |
| 4.4 Мониторинг | `metrics[]`, `calculated_metrics[]`, `health_checks[]` |
| 4.5 Аудит | `audit[]` |

**change.md** (target=change) — 16-разделная нумерация `templates/change.md`:

| Раздел | Поля агрегата |
|--------|---------------|
| 1. Предложение (Затронутые компоненты) | `callers[]`, `external_schemas_affected[]` |
| 2. Бизнес-логика | `current_implementation.rules[]` |
| 3. Глоссарий | не исследуется по коду — заполняется в интервью |
| 4. Бизнес-инварианты | не исследуется по коду — заполняется в интервью |
| 5. Состояния и переходы (FSM) | не исследуется по коду — заполняется в интервью |
| 6. Изменения моделей данных | `current_implementation.entities[]`, `schemas_in_scope[]`, `ddl_in_scope[]` |
| 7. Изменения интеграций (MODIFIED/REMOVED) | `current_implementation.endpoints[]`, `breaking_risk[]` |
| 8. Обработка ошибок | `errors_in_scope[]` |
| 9. Хедеры и метаданные | `headers_in_scope[]` |
| 10. Валидация входящих значений | `current_implementation.validations[]` |
| 11. Влияние на безопасность | `security_touchpoints` |
| 12. Миграция | `migration_plan.data_steps[]`, `migration_plan.backward_compat`, `migration_plan.rollback_plan[]`, `migration_hints[]` |
| 13. Логирование | `log_events_in_scope[]` |
| 14. Мониторинг | `metrics_in_scope[]` |
| 15. Изменения конфигурации | `config_params_in_scope[]` |
| 16. Рекомендации к критериям приёмки | `acceptance_hints[]` (из feature-scope, dependencies, cross-cutting — склеить, дедуп по `when+then`) |

Если строка таблицы с полями агрегата пуста — фиксируй как gap, не выдумывай. Разделы 3–5 пустые по коду — нормально, их заполнит Этап 2 интервью.

---

## 7. Лимиты для слабой модели и окна 200к

- **Числа — явные**: «2 субагента», «≤15 файлов», «≤200 слов summary».
- **Промпт — копипаст** из [research-roles.md](research-roles.md), без импровизации.
- **Запуск — строго параллельно**: все вызовы tool'а запуска субагента — одним сообщением лида. См. [invocation-contract.md § 3](invocation-contract.md).
- **Рекурсия субагентов запрещена**: субагент не имеет права звать другого субагента.
- **Сырые тексты из субагентов в контекст лида не копируются**. Храни только YAML-сводки.
- **Персистентность обязательна для Среднего и Крупного**, рекомендуется для Малого. Каждый возвратный YAML сразу идёт в `openspec/<specs|changes>/<name>/.research/<role>.yaml`, итоговый агрегат — в `.research/_aggregate.yaml` и `.research-notes.md`. Подробная схема — [invocation-contract.md § 8](invocation-contract.md). Это страховка от compact и ошибок ретрая.
- **Близко к 200к** — после записи YAML в файлы **выгружай сырые выводы из контекста лида** (не копируй в последующие сообщения). Работай с агрегатом через `.research/_aggregate.yaml` и `.research-notes.md`.
- **После финального артефакта** (`<service>.md` / `change.md` готов и прошёл ревью) — `.research/` и `.research-notes.md` удаляются. Они не должны попасть в git — добавить в `.gitignore` (см. § 8.6 в invocation-contract.md).

---

## 8. Fallback: нет tool запуска субагента

Снижаем класс на одну ступень:
- Крупный → стратегия Среднего
- Средний → стратегия Малого

Последовательный sweep по тому же каталогу ролей: для каждой роли — прочитай её YAML-контракт из [research-roles.md](research-roles.md), пройди по указанным путям сам, заполни YAML в рабочих заметках. В конце — та же фаза агрегации.

---

## 9. Связи

- Инструменты внутри субагента — [code-analysis-priority.md](code-analysis-priority.md) (Serena → LSP → embeddings → MCP → Grep/Glob).
- Каталог ролей и YAML-контракты — [research-roles.md](research-roles.md).
- Работа с артефактами OpenSpec (активные change, маршрутизация инсайтов) — [openspec-awareness.md](openspec-awareness.md).
