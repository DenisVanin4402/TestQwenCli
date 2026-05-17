# Gates SDD — Definition of Ready и Definition of Done

Документ описывает проверяемые гейты (gates) для каждой фазы SDD-процесса.

## Definition of Ready (DoR)

Задача считается готовой к началу реализации, когда все проверки пройдены:

### Гейт 1: Spec Ready

- [ ] Spec.md существует в `docs/specs/<id>-<slug>/`
- [ ] Секции problem, users, goals, non-goals заполнены
- [ ] Requirements имеют уникальные IDs (REQ-*)
- [ ] Acceptance criteria имеют IDs (AC-*)
- [ ] NFR (security, privacy, performance, observability) описаны
- [ ] Нет `[NEEDS CLARIFICATION]` для критических путей
- [ ] User/approve получен

### Гейт 2: Plan Ready

- [ ] plan.md существует
- [ ] Выявлены затронутые компоненты и файлы
- [ ] Выявлены зависимости
- [ ] ADR создана для значимых решений (если применимо)
- [ ] Контракты (contracts/) обновлены, если меняется API

### Гейт 3: Task Ready

- [ ] tasks.md существует с графом зависимостей
- [ ] Каждая задача ссылается на REQ-ID
- [ ] Каждая задача имеет чёткий вход и выход
- [ ] Определение ownership — кто/что за что отвечает

## Фазовые гейты

### Spec Lint Gate

- [ ] Обязательные секции в spec.md заполнены
- [ ] Поиск `[NEEDS CLARIFICATION]` — все должны получить ответ
- [ ] Requirement IDs присутствуют
- [ ] tasks.md ссылается на requirement IDs

### Contract Check Gate

- [ ] OpenAPI/asyncapi схемы обновлены при изменении API
- [ ] OpenAPI diff показывает совместимость или явно задокументирован break

### Test-first Check Gate

- [ ] Критические AC покрыты тестами
- [ ] Тесты явно падают (expected failing) перед реализацией
- [ ] Или есть explicit exception с обоснованием

### Implementation Gate

- [ ] Все verification commands прошли
- [ ] `mvn test` — без регрессий
- [ ] Typecheck/lint — без ошибок

### Security Check Gate

- [ ] Нет новых векторов injection
- [ ] Нет secrets в коде или артефактах
- [ ] Authz проверки на месте
- [ ] Input validation добавлена где нужно

### Observability Check Gate

- [ ] Логи для critical paths добавляют контекст
- [ ] Ошибки имеют понятные сообщения
- [ ] Метрики добавлены, если применимо

### Drift Check Gate

- [ ] Матрица трассировки заполнена
- [ ] Spec ↔ code ↔ tests согласованы
- [ ] Несоответствия задокументированы и приняты

## Definition of Done (DoD)

Работа считается завершённой, когда:

- [ ] Все acceptance criteria выполнены
- [ ] Матрица трассировки показывает все REQ -> AC -> Tests -> Code как pass
- [ ] Нет открытых `[NEEDS CLARIFICATION]` в текущей спеке
- [ ] `work-log.md` обновлён с командами и результатами
- [ ] `task-state.md` показывает all done
- [ ] `handoff.md` заполнен (если есть следующий агент)
- [ ] Найденные проблемы workflow исправлены или записаны как follow-up
- [ ] AGENTS.md/QWEN.md обновлены со стабильными правилами

## Как использовать гейты

1. Перед переходом к следующей фазе — проверить соответствующий гейт вручную.
2. Результат проверки записать в `work-log.md` и `task-state.md`.
3. Если гейт не пройден — не продолжать, а исправить.
4. Исключения (waivers) должны быть явно задокументированы с обоснованием и сроком действия.
