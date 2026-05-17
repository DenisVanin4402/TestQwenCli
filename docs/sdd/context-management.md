# Управление контекстом SDD

Документ описывает политику управления контекстом при агентской разработке, чтобы агенты получали релевантный контекстный пакет, а не весь репозиторий.

## Слои контекста

### Source of truth (всегда актуальные)
- Содержимое: требования, acceptance criteria, scope
- Где: `docs/specs/**/spec.md`
- Когда загружать: всегда для текущей feature

### Decision memory (архитектурные решения)
- Содержимое: почему выбрали архитектурный путь
- Где: `docs/adr/*.md`
- Когда загружать: когда затронуты boundary/stack/data-flow

### Project memory (команды и ограничения)
- Содержимое: соглашения, локальные команды, conventions
- Где: `AGENTS.md`, `CONVENTIONS.md`, `QWEN.md`
- Когда загружать: в начале сессии

### Procedure memory (повторяемые процедуры)
- Содержимое: повторяемые workflow и доменная экспертиза
- Где: `.qwen/skills/**/SKILL.md`, `.qwen/commands/sdd/*.md`
- Когда загружать: по требованию или auto trigger

### Runtime state (текущее состояние)
- Содержимое: что сделано сейчас, blockers
- Где: `task-state.md`, `work-log.md` в соответствующей спеке
- Когда загружать: для handoff/resume

### Retrieval context (внешние документы)
- Содержимое: большие docs/API/policies
- Где: docs/, external references
- Когда загружать: just-in-time при необходимости

## Context Packet

Формат структурированного пакета контекста, который агент получает перед запуском:

```md
# Context Packet

## Цель
Описание текущей задачи и REQ-ID.

## Текущая фаза
Spec / Plan / Implementation / Verification / Review

## Source of truth
- Spec: docs/specs/<id>-<slug>/spec.md#req-X
- Plan: docs/specs/<id>-<slug>/plan.md
- ADR: docs/adr/<id>-<title>.md

## Acceptance criteria
- AC-1 ...
- AC-2 ...

## Ограничения
- Что нельзя менять
- Что нужно сохранить

## Релевантные файлы
- src/...
- tests/...

## Текущее состояние
- Сделано: ...
- Не сделано: ...
- Блокеры: ...

## Команды верификации
- mvn test
- mvn compile

## Требуемый результат
- Изменённые файлы
- Запущенные тесты
- Spec compliance notes
- Residual risks
```

## Бюджет контекста

Практическая рекомендация:

| Бюджет | Содержимое |
|--------|------------|
| 20-30% | Spec, acceptance criteria, текущий task-state |
| 20-30% | Релевантные файлы кода |
| 10-20% | ADR, project rules, conventions |
| 10-20% | Retrieved docs/API/policies |
| 10-15% | Недавние tool outputs и evidence |
| Reserve | Место для новых tool results |

## Compaction Protocol

Когда контекст близок к лимиту или требуется handoff другому агенту:

1. Записать `task-state.md` и `work-log.md`
2. Сжать разговор в structured summary
3. Новый агент сверяет summary с файлами, spec и git state
4. Если summary противоречит артефактам — артефакты побеждают

## Ручное управление контекстом

- `@` в Qwen CLI — для injection конкретных файлов
- `!` — для команд shell
- `?` — для поиска и исследования
- `/clear` — для сброса истории чата при сохранении контекста в файлах

## Правила

1. Не заполнять окно контекста «до отказа» — лучше маленький high-signal packet.
2. Source-of-truth артефакты побеждают историю чата.
3. Контекст должен быть воспроизводимым — другой агент должен получить те же результаты из того же context packet.
