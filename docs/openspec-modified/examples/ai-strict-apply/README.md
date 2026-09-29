# Одна рабочая копия и автоматически построенный просмотр

Актуальный пример document-copies schema v3 / ai-strict. На изменяемый документ остаётся один редактируемый content и один генерируемый review. Base, первоначальное согласование и master перед применением доступны в Git по descriptors manifest.

```text
.spec-copy/
  manifest.json
  content/workflow/discount.md
  review/workflow/discount.md
  application-state.json        # появляется только при начале записи в master
```

Manifest хранит ссылки на версии в Git, checksum change.md и, после фактического согласования просмотра, поле review_approval. Review проверяется повторной генерацией в памяти; отдельные index, метаданные просмотра и файл согласования не нужны. Ход и результат применения объединены в application-state.json. Описания конфликтов и существенные решения находятся в change.md; правило EOL задано общим `.gitattributes` репозитория.

В `success` есть application-state.json с проверкой уже применённого результата. В `conflict` его нет: применение остановлено до первой записи в master.

## Открыть результат

- [Change](success/openspec/changes/discount-boundary/change.md).
- [Представление для аналитика](success/openspec/changes/discount-boundary/.spec-copy/review/workflow/discount.md).
- [Единственная рабочая копия](success/openspec/changes/discount-boundary/.spec-copy/content/workflow/discount.md).
- [Manifest с checksum change.md](success/openspec/changes/discount-boundary/.spec-copy/manifest.json).
- [Действующий master](success/openspec/orders/workflow/discount.md).
- [Результат проверки и история применения](success/openspec/changes/discount-boundary/.spec-copy/application-state.json).

Таблица CH-ID в change ведёт прямо на review. В нём виден лимит **1500**, совпадающий с content и master. Старого отдельного review с лимитом 1000 больше нет в рабочем дереве.

| Версия | Условие | Лимит | Где хранится |
|---|---|---|---|
| Исходная | > 10000 | 1000 | Git: base |
| Первоначально согласованная | >= 10000 | 1000 | Git: approved |
| Master перед применением | > 10000 | 1500 | Git: review_base |
| Итог | >= 10000 | 1500 | Единственный content; совпадает с master |

Review строится программно из review_base → content. Красным показано удаление, зелёным добавление; внутри строки отдельно выделен только новый `=`. При изменении content/master/источников старая проверка готовности перестаёт действовать.

## Генерация и проверка

Из корня репозитория установи зависимости из `Openspec/openspec-modified/skills/openspec-apply-change/scripts/requirements.txt` в выбранное Python-окружение. `<tool>` ниже — `Openspec/openspec-modified/skills/openspec-apply-change/scripts/spec_review.py`:

```text
python <tool> --root Openspec/openspec-modified/examples/ai-strict-apply/success render openspec/changes/discount-boundary
python <tool> --root Openspec/openspec-modified/examples/ai-strict-apply/success check openspec/changes/discount-boundary --result
python <tool> --root Openspec/openspec-modified/examples/ai-strict-apply/conflict check openspec/changes/discount-boundary
```

Success уже содержит применённый результат: для него проверяется --result, новое применение не требуется. Миграция v2 → v3 и сокращение служебных файлов не меняли master/content и не выдают новое согласование аналитика. Ссылка на прежний manifest сохранена в `manifest.legacy_manifest`, на исходный результат применения — в `application-state.provenance`. Эти Git-ссылки позволяют восстановить историю без отдельных файлов миграции и отчётов. Git commit из descriptors должен оставаться доступен; без истории проверка останавливается.

## Если аналитик изменил только change.md

В `.spec-copy/manifest.json` обоих примеров сохранено поле `change_sha256` — checksum требований при подготовке content. Любой потребитель change проверяет её обычной командой:

```text
python <tool> --root <корень-примера> check openspec/changes/discount-boundary
```

Если поменять требование в change.md, оставив content прежним, проверка завершится с exit code 1: `change.md changed since content was prepared`. Обычный render также остановится и не обновит checksum. После актуализации content выполни render с `--save-change-hash`, затем check. Смена статуса change и CRLF/LF не считается правкой требований.

## Конфликт

- [Описание конфликта в change](conflict/openspec/changes/discount-boundary/change.md#открытые-вопросы).
- [Review с явной отметкой конфликта](conflict/openspec/changes/discount-boundary/.spec-copy/review/workflow/discount.md).
- [Неизменённый master с порогом 15000](conflict/openspec/orders/workflow/discount.md).

Content сохраняет исходное предложение до решения пользователя. Это не согласованный итог объединения; check --ready блокирует применение.

## Просмотр

Открывай review в Markdown Preview IntelliJ/VS Code. Формат — Markdown с HTML, без diff-заголовков и служебных hashes. Цвет зависит от поддержки CSS, знаки −/+ и зачёркивание сохраняют смысл. Генератор поддерживает CommonMark, таблицы, списки и код; произвольный исходный HTML требует отдельного renderer. Mermaid/PlantUML показываются как исходный код.

Учебные согласования и сервисы не относятся к production; код приложения и refresh `_sdd` вне примера. Полный [протокол и команды](../../skills/openspec-apply-change/references/document-copies.md).
