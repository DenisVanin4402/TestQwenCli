# Спецификация: SDD from Diff — генерация спецификации из MR

## Проблема

Когда системный аналитик вносит бизнес-изменения в проект, он открывает MR в release-ветку, где изменяет множество файлов:
- Документация (`.md`) — бизнес-требования, правила
- Контракты (`openapi.yaml`, `dbml`) — API-спецификации, схемы БД
- Конфигурации (`.properties`, `.yml`)
- Код (`src/`)

Разработчикам и AI-агентам нужно понять: **что именно изменилось и как это реализовать в коде**. Текущий SDD-фреймворк поддерживает только сценарий «user request → spec с нуля», но не «MR с изменениями → delta-spec».

## Цель

Добавить в SDD-фреймворк сценарий **Spec from Diff**:
1. Извлечь diff из MR аналитика
2. Классифицировать изменения по типу (контракты, требования, код, конфиги)
3. Определить прямое и косвенное влияние на проект
4. Сгенерировать delta-spec — спецификацию, содержащую ТОЛЬКО изменения
5. Передать delta-spec в существующий SDD-конвейер

## Пользователи

- Системные аналитики — создают MR с изменениями
- Разработчики — получают delta-spec для понимания что реализовать
- AI-агенты — получают delta-spec как source of truth для implementation
- Ревьюеры — проверяют соответствие кода delta-spec

## Scope

**Включает:**
- Шаблон `delta-spec.md`
- Директория `_template/delta-spec.md`
- Скрипт `sdd-diff-extract.*` для извлечения и классификации diff
- Qwen CLI команда `/sdd:from-diff`
- Qwen CLI навык `sdd-diff-analyzer`
- Артефакт `diff-summary.md` (извлечённый diff)
- Артефакт `impact-map.md` (карта влияния)
- Обновление `workflow.md` — новый entrypoint
- Обновление `instructions.md` — новый сценарий

**Не включает:**
- GitHub/GitLab API интеграцию
- Автоматический parsing openapi.yaml (только извлечение diff)
- CI-интеграцию
- Автоматический MR review

## User Stories

- US-1: Как разработчик, я хочу получить delta-spec из MR аналитика, чтобы сразу понять что изменилось без чтения всех файлов.
- US-2: Как AI-агент, я хочу получить diff-summary и impact-map, чтобы генерировать минимальные целевые изменения.
- US-3: Как ревьюер, я хочу видеть матрицу трассировки delta-spec, чтобы проверить соответствие кода изменениям.
- US-4: Как аналитик, я хочу чтобы мои изменения в .md / yaml / dbml автоматически превращались в spec, чтобы разработчики не упустили требования.

## Требования

### REQ-1: Шаблон delta-spec

Реализация: Создать `docs/specs/_template/delta-spec.md` — шаблон спецификации изменений.

### REQ-2: Скрипт diff-extractor

Реализация: Создать `scripts/sdd-diff-extract.sh` и `scripts/sdd-diff-extract.bat` — утилита для извлечения diff из git.

### REQ-3: Формат diff-summary

Реализация: Определить формат `diff-summary.md` — структурированное резюме изменений.

### REQ-4: Формат impact-map

Реализация: Определить формат `impact-map.md` — карта прямого и косвенного влияния.

### REQ-5: Qwen команда /sdd:from-diff

Реализация: Создать `.qwen/commands/sdd/from-diff.md` — команда для генерации delta-spec из diff.

### REQ-6: Qwen навык sdd-diff-analyzer

Реализация: Создать `.qwen/skills/sdd-diff-analyzer/SKILL.md` — навык анализа diff и генерации контекста.

### REQ-7: Обновление workflow.md

Реализация: Добавить альтернативный entrypoint «from diff» в workflow.md.

### REQ-8: Обновление AGENTS.md

Реализация: Добавить инструкции для работы с delta-spec.

### REQ-9: Обновление instructions.md

Реализация: Добавить описание сценария «from diff» в инструкцию фреймворка.

## Acceptance Criteria

- AC-1: Шаблон `delta-spec.md` содержит все required секции для delta-spec
- AC-2: `sdd-diff-extract.sh` работает на Linux/Mac — извлекает diff и классифицирует файлы
- AC-3: `sdd-diff-diff-extract.bat` работает на Windows
- AC-4: Команда `/sdd:from-diff` генерирует delta-spec, diff-summary, impact-map
- AC-5: Навык `sdd-diff-analyzer` описывает процедуру анализа diff
- AC-6: `workflow.md` содержит описание нового entrypoint
- AC-7: `AGENTS.md` содержит правила для работы с delta-spec
- AC-8: `instructions.md` содержит описание сценария «from diff»
- AC-9: `mvn test` проходит без регрессий

## NFR

### Security (NFR-Security)

- Diff-extractor не содержит secrets из diff-вывода (если accidentally попали).
- Delta-spec не содержит чувствительных данных из изменённых файлов.

### Performance (NFR-Performance)

- Diff-extractor выполняется < 10 секунд для репозитория < 1000 файлов.
- Delta-spec генерация < 5 минут для MR < 20 файлов.

### Observability (NFR-Observability)

- work-log.md записывает каждый шаг diff-extraction.
- diff-summary.md фиксирует timestamp и branch-info.

### Reliability (NFR-Reliability)

- Скрипт работает с git-репозиториями (требуется .git).
- Корректно обрабатывает отсутствие изменений (empty diff).

## Матрица трассировки

| REQ | AC | Проверка | Статус |
|-----|----|----------|--------|
| REQ-1 | AC-1 | Шаблон существует | pending |
| REQ-2 | AC-2, AC-3 | Скрипты работают | pending |
| REQ-3 | AC-3 | формат в diff-summary | pending |
| REQ-4 | AC-4 | формат в impact-map | pending |
| REQ-5 | AC-4 | Команда создана | pending |
| REQ-6 | AC-5 | Навык создан | pending |
| REQ-7 | AC-6 | workflow.md обновлён | pending |
| REQ-8 | AC-7 | AGENTS.md обновлён | pending |
| REQ-9 | AC-8, AC-9 | instructions.md, mvn test | pending |
