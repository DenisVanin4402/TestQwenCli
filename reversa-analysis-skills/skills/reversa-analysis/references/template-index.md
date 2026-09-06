# Шаблоны документов

Выбирай применимые документы по содержанию. Маркеры {{...}} заполняются по источникам; шаблон не должен оставаться в готовом пакете. Диаграммы шаблонов показывают только нотацию, а не факты какой-либо системы.

| Шаблон | kind для каталога | Применение |
|---|---|---|
| [overview.md](../assets/templates/overview.md) | overview | Пакет системной аналитики |
| [scope.md](../assets/templates/scope.md) | overview | Границы анализа |
| [capability-map.md](../assets/templates/capability-map.md) | overview | Карта возможностей |
| [domain.md](../assets/templates/domain.md) | domain | Предметная модель |
| [data.md](../assets/templates/data.md) | domain | Логические данные |
| [glossary.md](../assets/templates/glossary.md) | reference | Глоссарий |
| [capability.md](../assets/templates/capability.md) | capability | Паспорт capability |
| [rules.md](../assets/templates/rules.md) | rules | Предметные правила |
| [reference.md](../assets/templates/reference.md) | reference | Справочник |
| [algorithm.md](../assets/templates/algorithm.md) | algorithm | Алгоритм |
| [workflow.md](../assets/templates/workflow.md) | workflow | Рабочий процесс |
| [states.md](../assets/templates/states.md) | states | Состояния |
| [permissions.md](../assets/templates/permissions.md) | permissions | Участники и доступ |
| [requirements.md](../assets/templates/requirements.md) | rules | Функциональные требования |
| [acceptance.md](../assets/templates/acceptance.md) | acceptance | Сценарии проверки |
| [decisions.md](../assets/templates/decisions.md) | decisions | Решения |
| [questions.md](../assets/templates/questions.md) | questions | Открытые вопросы |
| [evidence.md](../assets/templates/evidence.md) | overview | Основания анализа |
| [traceability.md](../assets/templates/traceability.md) | overview | Трассируемость |
| [sdd-guide.md](../assets/templates/sdd-guide.md) | overview | Работа по SDD |
| [change.md](../assets/templates/change.md) | overview | Проект изменения спецификации |
| [review-report.md](../assets/templates/review-report.md) | overview | Результаты ревью |

В [document-types.json](../assets/document-types.json) задан минимум разделов. Дополнительные разделы допустимы; не удаляй обязательный раздел без объяснения неприменимости. Начальную структуру создаёт init; образцы JSON и схемы находятся в assets/schemas.
