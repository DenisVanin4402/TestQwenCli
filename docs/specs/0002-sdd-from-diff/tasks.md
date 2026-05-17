# Задачи: SDD from Diff

## Задачи

### TSK-1: Создать шаблон delta-spec

**REQ:** REQ-1
**Описание:** Создать `docs/specs/_template/delta-spec.md` — шаблон delta-specifications
**Вход:** spec.md
**Выход:** delta-spec.md template
**Зависимости:** None
**Оценка сложности:** S

### TSK-2: Создать diff-extractor скрипты

**REQ:** REQ-2
**Описание:** Создать `scripts/sdd-diff-extract.sh` и `scripts/sdd-diff-extract.bat` — утилита извлечения и классификации diff
**Вход:** git-diff между двумя коммитами/branch
**Выход:** diff-summary.md, impact-map.md (структурированные файлы)
**Зависимости:** TSK-1
**Оценка сложности:** L

### TSK-3: Создать Qwen команду /sdd:from-diff

**REQ:** REQ-5
**Описание:** Создать `.qwen/commands/sdd/from-diff.md`
**Вход:** workflow.md, spec.md
**Выход:** from-diff.md command definition
**Зависимости:** TSK-1
**Оценка сложности:** M

### TSK-4: Создать Qwen навык sdd-diff-analyzer

**REQ:** REQ-6
**Описание:** Создать `.qwen/skills/sdd-diff-analyzer/SKILL.md`
**Вход:** research doc, spec.md
**Выход:** SKILL.md
**Зависимости:** TSK-3
**Оценка сложности:** S

### TSK-5: Обновить workflow.md

**REQ:** REQ-7
**Описание:** Добавить альтернативный entrypoint "from diff" в workflow.md
**Вход:** workflow.md (существующий)
**Выход:** Обновлённый workflow.md
**Зависимости:** TSK-1
**Оценка сложности:** S

### TSK-6: Обновить AGENTS.md

**REQ:** REQ-8
**Описание:** Добавить инструкции для работы с delta-spec
**Вход:** AGENTS.md (существующий), spec.md
**Выход:** Обновлённый AGENTS.md
**Зависимости:** TSK-1
**Оценка сложности:** S

### TSK-7: Обновить instructions.md

**REQ:** REQ-9
**Описание:** Добавить описание сценария "from diff" в инструкцию фреймворка
**Вход:** instructions.md (существующий), spec.md
**Выход:** Обновлённый instructions.md
**Зависимости:** TSK-1
**Оценка сложности:** M

## Граф зависимостей

```
TSK-1 -> TSK-2 -> TSK-3 -> TSK-4
      ↳ TSK-5
      ↳ TSK-6
      ↳ TSK-7
```

## Владение и блокировки

| Задача | Ответственный | Статус | Блокеры |
|--------|---------------|--------|---------|
| TSK-1 | Builder | pending | none |
| TSK-2 | Builder | pending | TSK-1 |
| TSK-3 | Builder | pending | TSK-1 |
| TSK-4 | Builder | pending | TSK-3 |
| TSK-5 | Builder | pending | TSK-1 |
| TSV-6 | Builder | pending | TSK-1 |
| TSK-7 | Builder | pending | TSK-1 |
