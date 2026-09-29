# Одна рабочая копия и её представление

`Spec update mode: document-copies`, `Apply policy: ai-strict`, manifest schema v3.
ИИ определяет смысл изменений; [spec_review.py](../scripts/spec_review.py) читает Git, строит представление и проверяет его. Скрипт не меняет master и не делает commits.

## Состав

```text
openspec/changes/<name>/
  change.md
  .spec-copy/
    manifest.json
    content/<путь-документа>.md
    review/<путь-документа>.md
    application-state.json       # только с начала apply; здесь же конечный результат
```

`content` — единственный редактируемый текст будущего master; `review` — его генерируемое представление. Таблица CH-ID в change содержит прямые ссылки на review. Поле `Document copies` в шапке ведёт на эту таблицу внутри change. Отдельный index не создаётся.

Планы переноса, существенные решения и конфликты фиксируй в соответствующих разделах change, включая «Открытые вопросы». Краткий результат работы выдай в ответе; ход записи и её результат сохраняй только в application-state.json. Обязательных отдельных планов, отчётов или файлов согласования нет.

## Manifest

```json
{
  "schema_version": 3,
  "apply_policy": "ai-strict",
  "master_root": "openspec/orders",
  "review_status": "draft",
  "change_sha256": "<SHA-256 требований change.md при подготовке content>",
  "documents": [{
    "id": "D001",
    "path": "workflow/discount.md",
    "operation": "modify",
    "change_ids": ["CH-01"],
    "base": {"commit": "<полный SHA>", "path": "openspec/orders/workflow/discount.md", "sha256": "<SHA-256 байтов>"},
    "review_base": {"commit": "<полный SHA>", "path": "openspec/orders/workflow/discount.md", "sha256": "<SHA-256 байтов>"}
  }]
}
```

`path` относительно master_root; пути content/review выводятся из него. `master_root` относительно project root (`--root`), а Git-пути — относительно **корня Git-репозитория**, который может находиться выше. Исторические пути после переезда change не переписываются.

- `base` — исходная версия документа в Git.
- `approved` — первоначально согласованная версия в Git; появляется после pin-intent.
- `review_base` — версия, относительно которой показан результат; перед apply должна побайтно совпадать с master.
- `intent_approval` — ссылка на первоначально согласованный change и контроль его требований/scope; сохраняется через pin-intent.
- `review_approval` — `{ "fingerprint": "<версия просмотренного результата>", "evidence": "<ссылка на фактическое решение>" }`; появляется через acknowledge после реального согласования результата.
- `legacy_manifest` — необязательная Git-ссылка на прежний manifest для мигрированного change; новым changes не нужна.

`review_status`: `draft` — проект, `ready` — итог подготовлен, `conflict` — итог не определён. Это не бизнес-статус change и не факт согласования.

Для add `base=null`, content существует, включая допустимый пустой файл. Для delete content отсутствует, `approved=null` после фиксации намерения. Rename — согласованная пара delete/add. Дубликаты назначения, абсолютные пути, `..`, symlink/junction запрещены.

## История и переводы строк

Base/approved/review_base доступны по полным commit SHA; дополнительных файлов base/proposed/current/candidate/preview нет. Сохраняй Git-историю доступной обычными ветками/тегами/историей проекта. При утрате объекта проверка останавливается; не подменяй его текущим master. Для незакоммиченного исходника нужен корректный checkpoint; скрипт сам не делает commit или fetch.

В общем `.gitattributes` репозитория должно быть правило:

```gitattributes
**/.spec-copy/** -text
```

При подготовке первого change добавь это правило, сохранив остальные настройки файла; отдельные `.gitattributes` внутри changes не создавай. Правило сохраняет точные байты content/review при checkout, включая EOL. Правила для master автоматически не меняй.

## Команды

Нужен Python 3.10+ с зависимостями из [requirements.txt](../scripts/requirements.txt). `<tool>` — путь к `scripts/spec_review.py`:

```text
python -m pip install -r <skill-dir>/scripts/requirements.txt
python <tool> --root . source --revision HEAD --path openspec/orders/workflow/discount.md
python <tool> --root . render openspec/changes/discount-boundary --save-change-hash
python <tool> --root . check openspec/changes/discount-boundary
python <tool> --root . pin-intent openspec/changes/discount-boundary --revision <SHA согласованного commit>
python <tool> --root . acknowledge openspec/changes/discount-boundary --evidence "Ссылка на фактическое решение аналитика"
python <tool> --root . check openspec/changes/discount-boundary --ready
python <tool> --root . check openspec/changes/discount-boundary --result
```

- `source` печатает проверенный Git descriptor.
- `render` строит только review. После подготовки/актуализации content флаг `--save-change-hash` также сохраняет checksum change в manifest. Обычный render её не обновляет. Весь набор разбирается до первой записи представления.
- `check` сравнивает checksum change, затем заново строит ожидаемый review и сверяет сохранённые файлы побайтно. Метаданные вычисляются в памяти и возвращаются в результате команды; отдельного файла с ними нет.
- `pin-intent` после реального согласования и commit сохраняет `approved` и `intent_approval` в manifest. Commit должен совпадать с рабочим content и требованиями. Команда не выдаёт согласование; первоначальное намерение не перепривязывается после независимых релизных правок.
- `acknowledge` сохраняет уже полученное решение о текущем результате в `manifest.review_approval`. Fingerprint связывает требования, остальные поля manifest, content, представление и renderer; само поле review_approval исключено из расчёта, чтобы не было самоссылки. Команда не меняет review и не выдаёт согласование от имени аналитика. Если результат уже согласован в сессии/PR, используй имеющееся решение.
- `check --ready` дополнительно проверяет допустимый этап, исходное намерение/scope, согласование текущего результата, отсутствие незавершённой записи и совпадение master с review_base.
- `check --result` сверяет записанный master с content, для delete — отсутствие. Это не проверка кода или `_sdd`.

Любой skill, использующий конкретный document-copies change, сначала запускает check. Несовпадение `change_sha256` означает, что content требует актуализации. Не сохраняй новую checksum только ради продолжения downstream-этапа. При задаче редактирования актуализируй документы; при диагностике можно объяснить расхождение.

Смена `Статус` в начальной шапке change, UTF-8 BOM и CRLF/LF этого файла не меняют checksum требований. Статус внутри текста требования и остальные правки учитываются. Байты content/review сверяются точно. Render не обновляет review_approval: правка входов делает старое согласование результата неактуальным.

## Представление

Аналитик открывает review по ссылке из таблицы CH-ID в change. Не редактируй представление вручную: оно строится из review_base → content. Для обычного apply это актуальный master → итог; конфликтный просмотр явно помечен как предложение с неразрешённым итогом.

Формат — Markdown с HTML: заголовки, списки, таблицы, код; удаления красные с «−» и зачёркиванием, добавления зелёные с «+». Внутри строки выделяются изменившиеся Unicode-графемы. Относительные ссылки пересчитываются от исходного master, ссылки на изменяемые документы ведут на их review.

Поддерживаются CommonMark, таблицы и зачёркивание. Mermaid/PlantUML показываются кодом. Произвольный HTML в исходнике останавливает renderer; HTML-комментарии не отображаются. Структурная перестройка может выделяться целым блоком. Поддержка CSS зависит от Markdown Preview редактора; «−/+» и зачёркивание сохраняют смысл без цвета. Сверка байтов не доказывает смысловую правильность ИИ.

## Подготовка и применение

1. Закрепи base из Git, для three-dot — merge-base, для two-dot — Base SHA. Подготовь manifest и content по завершённому change, review_base=base.
2. Выполни render --save-change-hash/check. Таблица change содержит прямые ссылки на review; master не меняется.
3. После реального согласования и commit закрепи intent через pin-intent. Design/implement читают эти источники и проверяют checksum change.
4. После изменения master сопоставь base, approved и актуальный master. Обновляй тот же content и review_base, сохраняя независимые изменения. Конфликты и решения записывай в change, обновляя checksum только после сверки content. Содержательное изменение требований требует нового согласования намерения.
5. Выстави ready, выполни render/check и покажи результат. Зафиксируй реальное решение через acknowledge, затем check --ready.
6. Создай application-state.json непосредственно перед записью, примени проверенный content по [ai-apply.md](ai-apply.md), выполни check --result. В этом же state сохрани конечный результат. Отчёт выдай в ответе.

## Переход со старого состава

Сначала проверь старые hashes, доступность Git-истории и отсутствие незавершённого apply. Для v1/v2 восстанови base/approved и рабочий content из достоверных версий; исторический `examples/deterministic-apply` остаётся примером v1.

Для прежнего v3 перенеси описание конфликтов и важные решения в change, объедини сведения о ходе и результате apply в application-state.json. Прежний manifest можно сохранить Git-ссылкой legacy_manifest, исходный результат применения — ссылкой provenance в state. Не копируй все старые отчёты внутрь manifest. Сохрани достаточную историю, чтобы получить прежние документы и результат; миграцию не объявляй новым apply или согласованием.

Обнови ссылки change на прямые review/реестр, перенеси правило EOL в общий .gitattributes, сверь content и сохрани актуальную checksum change. Перегенерируй review и проверь его; только после этого удали лишние служебные файлы. Старую запись согласования не превращай автоматически в новый fingerprint. В учебном success допустим state с outcome=verified-existing-result и provenance: это проверка существующего master, не новое применение.
