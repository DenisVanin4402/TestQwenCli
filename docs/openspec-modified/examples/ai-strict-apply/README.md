# Цветные копии и строгий AI apply

Актуальный пример для IntelliJ IDEA / VS Code. Change связывает требование с обычной копией discount.md: красная строка «−» удаляется, зелёная «+» добавляется. Строки компактные (`padding:2px 5px`, знаки −/+ размером 10px); внутри строки более насыщенным фоном и жирным начертанием выделены только изменившиеся символы. Здесь `>` меняется на `>=`, поэтому отдельно выделен только добавленный `=`. Заголовки и остальной текст остаются документом, без git diff, hashes и журналов.

## Открыть документы

- [change.md](success/openspec/changes/discount-boundary/change.md).
- [discount.md с выделенными строками](success/openspec/changes/discount-boundary/.spec-copy/review/workflow/discount.md).
- [Исходная чистая копия](success/openspec/changes/discount-boundary/.spec-copy/base/workflow/discount.md).
- [Согласованная чистая версия](success/openspec/changes/discount-boundary/.spec-copy/proposed/workflow/discount.md).

## Сценарий после merge релиза

| Версия | Условие | Максимальная скидка |
|---|---|---|
| Исходная base | > 10000 | 1000 |
| Предложенная proposed | >= 10000 | 1000 |
| Master после merge релиза | > 10000 | 1500 |
| Результат AI apply | >= 10000 | 1500 |

ИИ переносит только CH-01 в актуальный master и сохраняет независимый лимит 1500. Он не заменяет current старой proposed-копией. Решение принимается агентом по смыслу требований; файловые инструменты только записывают выбранную правку и проверяют байты.

- [Master после применения](success/openspec/orders/workflow/discount.md).
- [Master перед применением](success/openspec/changes/discount-boundary/.spec-copy/current/workflow/discount.md).
- [Предварительный просмотр фактической правки](success/openspec/changes/discount-boundary/.spec-copy/preview/workflow/discount.md).
- [План агента](success/openspec/changes/discount-boundary/.spec-copy/application-plan.md).
- [Фактический отчёт](success/openspec/changes/discount-boundary/.spec-copy/application-report.md).

## Конфликт

Если релиз поменял порог на 15000, агент останавливается до записи:

- [Понятное описание конфликта и варианты](conflict/openspec/changes/discount-boundary/.spec-copy/conflicts.md).
- [Master с релизным порогом — сохранён](conflict/openspec/orders/workflow/discount.md).

Все сервисы и согласования здесь учебные. Реальные мастер-документы проекта не затрагиваются. Код приложения и refresh _sdd не входят в пример; статус change не переводится в «Реализовано».

## Просмотр в редакторе

В IntelliJ открой discount.md и включи Editor and Preview / Preview; в VS Code — Markdown: Open Preview (Ctrl+Shift+V). Все копии и предварительные просмотры хранятся только в `.md`; цвет задаётся встроенной разметкой внутри Markdown. Если просмотрщик очищает стили, цвет может исчезнуть; знаки −/+ и зачёркивание сохраняют смысл правок. Настройки редакторов автоматически не изменяются.

Официальная справка: [IntelliJ Markdown preview](https://www.jetbrains.com/help/idea/markdown.html), [VS Code Markdown preview](https://code.visualstudio.com/docs/languages/markdown#_markdown-preview).
