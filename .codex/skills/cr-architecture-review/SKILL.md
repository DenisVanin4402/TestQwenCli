---
name: cr-architecture-review
description: Validate a change request stage before closure with a senior architect review. Use when Codex needs to review a CRXXX-TYYY stage implementation against work-items.md, plan_TYYY.md, execution-progress.md, ADRs, architecture docs, performance risks, security risks, and architectural tradeoffs, then produce or update review_TYYY.md for human approval.
---

# CR Architecture Review

## Рабочий процесс

1. Определи этап `CRXXX-TYYY` и прочитай `work-items.md`, `plan_TYYY.md`, `execution-progress.md`, связанные ADR и затронутые архитектурные документы.
2. Сверь фактический diff и реализацию этапа с заявленным stage-level планом.
3. Проверь риски производительности: лишний runtime overhead, рост времени сборки, нестабильные тесты, дублирование моделей, неограниченный рост generated code или очередей.
4. Проверь риски безопасности: расширение публичного API, SSRF/validation gaps, неверные callback contracts, некорректный scope доступа, секреты и небезопасные defaults.
5. Оцени архитектурные приемы: границы компонентов, разделение contract/domain/persistence, заменяемость слоев, связность, тестируемость и соответствие ADR.
6. Запиши результат в `docs/external-service-gateway/chrequests/CRXXX/review_TYYY.md`.

## Формат review_TYYY.md

Используй разделы:

- `# CRXXX-TYYY: senior architect review`
- `## Итог`
- `## Соответствие плану`
- `## Производительность`
- `## Безопасность`
- `## Архитектурные приемы`
- `## Замечания`
- `## Рекомендация`
- `## Human approval`

Для каждого замечания указывай:

- severity: `critical`, `high`, `medium`, `low` или `note`;
- ссылку на файл, раздел или задачу CR;
- риск;
- предлагаемое действие;
- статус human approval: `pending`, `accepted`, `rejected` или `deferred`.

## Правила

- Не расширяй scope CR автоматически: `review_TYYY.md` является входом для решения человека.
- Не вноси production-правки в ходе review, если пользователь явно не попросил исправить замечания.
- Если замечаний нет, явно напиши, какие области были проверены и какие остаточные риски остаются.
- Пиши review на русском языке, сохраняя технические имена и протокольные значения на английском.
