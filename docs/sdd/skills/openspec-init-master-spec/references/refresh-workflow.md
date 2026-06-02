# Refresh workflow

Refresh обновляет `_sdd/manifest.yaml`, не перечитывая неизмененные документы без необходимости.

## Алгоритм

1. Прочитай текущий `manifest.yaml`.
2. Собери актуальный список файлов master-spec root.
3. Исключи `_sdd/**`, `openspec/changes/**`, временные файлы и build output.
4. Для каждого файла получи `size_bytes` и content hash.
5. Сравни с manifest:
   - `unchanged`: путь есть, hash совпал;
   - `added`: путь есть в дереве, но отсутствует в manifest;
   - `modified`: путь есть, hash отличается;
   - `deleted`: путь есть в manifest, но отсутствует в дереве.
6. Для `unchanged` перенеси существующую запись без глубокого чтения.
7. Для `added` классифицируй как новый файл.
8. Для `modified` перечитай содержимое, обнови tags/relations/read_priority.
9. Для `deleted` удали запись и проверь все `related_files`/`depends_on`.
10. Обнови `navigation.md` и `coverage.md`.
11. Запиши `stale-files.md`, если остались проблемы.

## Hash mismatch

Hash mismatch означает, что содержимое файла изменилось после предыдущей генерации manifest. Это не ошибка само по себе, но требует обновления:

- summary/tags/entities/integrations/endpoints/events;
- `related_files` и `depends_on`;
- coverage;
- read priority.

Если классификация изменилась неоднозначно, не выбирай молча: добавь запись в `stale-files.md`.

## Added files

Новый файл должен попасть в manifest или в явный exclude-list. Если файл не описан, manifest считается stale.

## Modified files

Для измененного файла обнови hash и `last_seen_at`. Если изменение затрагивает доменные связи, обнови navigation и coverage.

## Deleted files

Для удаленного файла:

- удали запись из manifest;
- удали или пометь битые ссылки в других записях;
- добавь warning, если файл был `read_priority=high`.

## Final output

В финальном ответе refresh всегда показывает:

- сколько файлов добавлено;
- сколько изменено;
- сколько удалено;
- сколько бинарных файлов учтено;
- какие документы требуют ручной проверки.
