# Review checklist

Используй перед завершением `openspec-init-master-spec`.

## Manifest checks

- [ ] Все файлы master root представлены в `files[]` или явно исключены.
- [ ] `_sdd/**` не включен в `files[]`.
- [ ] `openspec/changes/**` не включен в manifest сервиса.
- [ ] Все `path` существуют.
- [ ] Все `related_files` существуют.
- [ ] Все `depends_on` существуют.
- [ ] Для каждого `read_priority=high` есть понятный `agent_notes`.
- [ ] Для бинарных файлов указаны `format`, `size_bytes`, `hash`, `title`, `kind`.
- [ ] Добавленные/измененные/удаленные файлы отражены в manifest или `stale-files.md`.

## Navigation checks

- [ ] Есть раздел "Что читать первым".
- [ ] Есть карта по областям: workflow, API, integrations, data, errors, security, NFR.
- [ ] Есть список обязательных документов для change.
- [ ] Есть связи между основными файлами.
- [ ] Deprecated/draft документы явно указаны или отмечено, что их нет.
- [ ] Coverage gaps не скрыты.

## Coverage checks

- [ ] Для Workflow указано покрытие и основные файлы.
- [ ] Для API указано покрытие и основные файлы.
- [ ] Для интеграций указано покрытие и основные файлы.
- [ ] Для моделей данных указано покрытие и основные файлы.
- [ ] Для ошибок указано покрытие и основные файлы.
- [ ] Для безопасности указано покрытие и основные файлы.
- [ ] Для НФТ указано покрытие и основные файлы.

## Agent checks

- [ ] `openspec-propose` сможет начать с `_sdd/navigation.md`.
- [ ] `openspec-propose` сможет выбрать документы по manifest без полного чтения папки.
- [ ] `openspec-design` сможет прочитать master-spec root и sources из `change.md`.
- [ ] Если `stale-files.md` непустой, агент предупредит пользователя перед созданием `change.md`, `design.md` или `tasks.md`.
