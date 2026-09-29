# Tasks: <!-- название -->

> **Change**: [change.md](change.md)
>
> **Design**: [design.md](design.md)
>
> **Change profile**: lite
>
> **Статус**: В реализации

<!-- Если design пропущен по договорённости, заменить ссылку на «Не требуется: <причина>». -->

## 1. Реализация

- [ ] 1.1 <!-- Конкретная правка объекта; ссылка на CH-ID. -->

## 2. Проверка

- [ ] 2.1 <!-- Проверить AC-ID, граничный случай и отсутствие регрессии затронутого поведения. -->
- [ ] 2.2 <!-- Выполнить check и применимые проверки проекта, сверить реализацию с content и закреплённым Git approved. При устаревшей связи change/content остановить выполнение. -->

## Обновление master specification

После реализации для document-copies выполнить отдельный openspec-apply-change: check, анализ base/approved/master, обновление единственного content, render --save-change-hash/check, решение о текущем review, check --ready, запись/check --result, refresh `_sdd`. Смысловой конфликт требует решения пользователя. Apply не является задачей implement.
