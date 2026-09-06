# Контракт пакета, версия 1

Корень содержит README.md, catalog.json, evidence.json; для рабочего SDD также traceability.json. analysis-config.json хранит область и ход работы. Полные формальные структуры расположены в [schemas](../assets/schemas/catalog.schema.json); локальный валидатор выполняет документированный набор проверок, а не общий движок JSON Schema.

## catalog.json

version=1, system, baseline и documents обязательны. primaryFormat=markdown, entrypoint=README.md. scope объясняет объём. Документ содержит id, kind, title, path, parent (ID или null), status. audience=reader по умолчанию; evidence — для приложения источников. Типы и обязательные разделы — в [document-types.json](../assets/document-types.json).

Один ID и путь принадлежат одному документу; иерархия без циклов. ID сохраняется при переименовании. Пути внутри пакета относительные, с прямыми слешами. attachments — список объектов path/purpose, например файл Gherkin; записи не дублируют документы.

## evidence.json

- sources: id, type (code/reversa/decision/test/documentation/synthetic), locator, revision, при наличии sha256 и location. Для относительных locator корень — каталог пакета либо явно переданный source-root. В распространяемых примерах используй относительные источники.
- claims: id, documentId, text, kind, basis, sourceIds, status; для inferred — rationale, для unknown — questionIds. Префикс CLM.
- questions: id, documentIds, text, severity (critical/major/minor), status (open/answered/accepted_gap). answered требует answer; accepted_gap — acceptance. Префикс Q.
- approved у документов/claims требует approvedBy и approvedAt. Подлинность этих записей проверяет человек; скрипт проверяет лишь наличие и согласованность.

Источники используют SRC, документы CAP/ENT/RULE/ALG/REF/WF/REQ/PAGE и другие буквенные префиксы. Формат реестра: латинский префикс, дефис, минимум три цифры. Внутри страниц FR-001, BR-001, AC-001 и т. п. получают собственные якоря. ID строки и ID документа различаются.

## traceability.json

version=1; requirements — массив {id, documentId, anchor, mode, specificationIds, ruleRefs, acceptanceRefs, questionIds}. mode: as-is/to-be/proposal. ruleRefs и acceptanceRefs: массив {documentId, anchor}; specificationIds: IDs подробных документов; questionIds: IDs вопросов. Все цели и якоря должны существовать.

Матрица Markdown должна соответствовать этому реестру. Наличие ссылки означает связь, но не полное покрытие ветвей или успешное исполнение AC. Старый пилот без traceability.json остаётся читаемым; при дальнейшей работе SDD добавь этот реестр.

## Ссылки и навигация

Внутренние ссылки относительные. Для точных требований используй явный HTML-якорь в Markdown, например <a id="fr-001"></a>. Синтаксис заголовочных якорей разных редакторов различается; генератор добавляет собственные стабильные в рамках названия раздела якоря rsa-. Не используй префикс rsa- для ручных якорей.

Валидатор проверяет обычные inline-ссылки, ссылки через определения и существующие shortcut-определения, явные якоря и распространённое преобразование заголовков. Не проверяются сетевые URL, ссылки в исполняемом HTML/MDX, динамические макросы и недекларированные shortcut-метки. Для такого расширенного синтаксиса нужна дополнительная проверка целевого редактора. Mermaid-блоки учитываются, но их грамматика проверяется отдельным рендером.
