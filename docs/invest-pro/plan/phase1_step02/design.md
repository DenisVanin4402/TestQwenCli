# Design: P1.02 — доменные контракты чата, OpenAPI и MapStruct

**Роль:** архитектор. **Стадия:** design. **Ревизия:** 8. **Статус:** Design согласован; implement завершён, на проверке. **Дата:** 28.09.2026.

r6 реализована: 142 теста прошли, приёмка шага не получена. Новый запрос — единый суффикс DTO и различение входа/выхода. Пользователь согласовал схему r7 (§3.8): «cjukfcjdfyj» («согласовано» в английской раскладке). r8 фиксирует ответ Q-10 без изменения решений; переименование реализовано и проверено (142 теста).

## 1. Привязка и объём

[WBS P1.02](../plan_phase1.md): R02 — DTO/порты и mapping; R03 — action_code/suggestions. P1.01 принят. Реализация r4 завершена и предъявлена на проверку: 150 тестов прошли, приёмка шага не получена. Новое требование — заменить все 10 records в agent-model классами DTO с Lombok-геттерами/сеттерами. Ответ Q-09 внесён: DTO содержат только конструкторы/getters/setters, без проверок и их переноса в новый слой. Код r6 реализован; результат проверки — в task.

В r4 внесены ответы пользователя на Q-06–Q-08: epkId обозначает организацию, digitalUserId — пользователя; additionalInfo необязателен, только переносится; идемпотентность принадлежит существующей внешней библиотеке. В P1.02 планируются DTO/OpenAPI/validation/mapping, временная аннотация @Idempotent без поведения и исчерпывающая русская документация всех OpenAPI, интерфейсов/методов и DTO/полей, включая существующие. После ответов пользователь явно согласовал r4 и разрешил дореализацию: «все согласовано , сделай дореализацию».

Источники: [модули §§5,7–9](../../final/modules.md), [контракт чата §0](../../final/integrations.md), [архитектура](../../final/architecture.md), [исполнение](../../final/execution.md), [workflow status](../../final/workflow-status.md), [FSM](../../final/state-machine.md), [handoff P1.01](../phase1_step01/task.md), [индекс ADR](../../adr/README.md). ADR-001 сохраняет клиентов за портами; ADR-002/003 относятся к будущей persistence, здесь схема не меняется.

Результат — доменные контракты сообщений и входных use cases, две самостоятельные OpenAPI-спецификации прототипа, генерация DTO/API и проверенное JSON/mapping. Контракт ГигаАссистента не объявляется утверждённым промышленным API.

Принятое решение Q-05: входные порты чата/query/fixture сейчас; полноценные out-порты/DTO corr и LLM — в P1.03/P1.04; DB/FSM — P1.05/P1.06. Собственный механизм входной идемпотентности из предложений r3 исключён по ответу пользователя; подключение существующей библиотеки оформляется отдельно при наличии её контракта.

Вне шага: контроллеры/работающие endpoints, аутентификация и платёжная привязка, orchestration/ResponsePolicy, генерация GUID, LLM/corr-клиенты, FSM, repositories/миграции, экран чата. Наличие кодов recall/details не делает эти сценарии доступными в Phase 1.

## 2. Критерии приёмки

| AC | Вход / условие | Результат | Требование |
|---|---|---|---|
| AC-01 | Свободный текст и клик в обоих адаптерах | Сохраняются epkId/sessionId/content и правила action_code; добавляются digitalUserId/requestId/additionalInfo по AC-08–AC-10 и решению Q-06 | R03 / уточнение пользователя |
| AC-02 | Ответ с предложениями и без | Всегда sessionId/content/suggestions; пустой массив — []; GUID UUID, код/текст обязательны; mapping сохраняет UUID/порядок | R03 |
| AC-03 | Недопустимый wire-вход | Missing/null/wrong type/blank обязательных полей, превышение лимита, дополнительные поля и неизвестный код отклоняются на wire-границе до LLM (HTTP 400 в будущем HTTP-адаптере), по принятым Q-01/Q-02 | R02/R03 |
| AC-04 | Доменный ответ | SuggestionSpec без GUID, Suggestion с готовым UUID. DTO больше не гарантирует non-null/уникальность/неизменяемость; существующий wire-контракт сохраняется | Ответ Q-09 |
| AC-05 | Порты и mapping | Только проектные/JDK типы в сигнатурах; заявленный EPK отделён от доверенного контекста; mapper не создаёт доверие/DomainEvent/ключ отправки | R02 |
| AC-06 | UI create/query | Fixture-вход только fixtureId; created содержит sessionId/epkId/digitalUserId для сообщения UI; query возвращает последний ответ с прежними GUID и занятость либо отсутствие ответа до первого хода — Q-04 | R02/R03 |
| AC-07 | Чистая сборка | OpenAPI до MapStruct, generated sources только target, unmappedTargetPolicy=ERROR; сохранена регрессия P1.01 | R02 |
| AC-08 | Новые поля в manual/click обоих адаптеров | digitalUserId/requestId и массив key/value проходят в домен дословно, включая строковый paymentId; mapper не создаёт ID и не повышает доверие | Уточнение пользователя |
| AC-09 | Обязательность и additionalInfo | epkId/digitalUserId/requestId/sessionId/content обязательны; additionalInfo можно опустить или передать []; explicit null запрещён; key/value — строки non-null, ключи уникальны; paymentId не обязателен и не требует UUID; action_code отсутствует при ручном вводе | Уточнение пользователя |
| AC-10 | Дополнительные данные | Mapping сохраняет порядок и optional→[], paymentId не обрабатывается. DTO не проверяет список/элементы и не обеспечивает защитное копирование | Ответ Q-09 |
| AC-11 | Временная @Idempotent | Аннотация agent-common/common/utils/Idempotent: METHOD/RUNTIME/Documented на ConversationUseCase.handle, без AOP/cache/БД/поведения; реальная библиотека не подключена и совместимость не обещается | Уточнение пользователя |
| AC-12 | Документация всего текущего API и DTO | На русском описаны OpenAPI, интерфейсы/методы, DTO/поля, параметры, результаты, ошибки, обязательность/nullability и семантика; generated получают описание из спецификаций стандартным генератором (уточнение §3.6); review и clean generation подтверждают результат | Уточнение пользователя |
| AC-13 | Замена records | Все 10 типов — классы с Lombok getters/setters и no-args/all-args конструкторами; имена/пакеты/JSON сохраняются, вызовы используют bean accessors | Новый запрос |
| AC-14 | Простые DTO | Конструкторы/setters не проверяют значения; нет нового валидатора/hooks и equals/hashCode/toString. Существующие wire-проверки JSON сохранены | Ответ Q-09 |
| AC-15 | Имена типов | Все DTO имеют точный суффикс DTO; входные InDTO, выходные OutDTO, внутренние DTO по согласованной таблице §3.8 | Новый запрос / Q-10 |
| AC-16 | Генерация и совместимость | Generated имена заданы исходными схемами/конфигурацией; JSON-поля, required/nullability, enum values, URLs и поведение r6 сохранены | R02/R03 |

AC-03 не проверяет смысл и актуальность известного кода — P1.08. AC-02/06 доказывают сохранение UUID при mapping заданного объекта, не восстановление receipt из БД.

## 3. Детальный дизайн

### 3.1. Доменные типы и порты

Java-пути ниже относительно src/main/java/ru/sberbank/pprb/agent/ своего модуля. В r4 типы реализованы records; r6 переводит их в mutable DTO без Jackson/JPA/OpenAPI-аннотаций по §3.7. Таблица описывает поля и прежний контракт r4; конструкторные гарантии отменены ответом Q-09 (§3.7), wire-проверки остаются.

| Модуль / файл | Контракт |
|---|---|
| agent-model/model/enums/SuggestionActionCode.java | REQUEST_STATUS, REQUEST_RECALL, REQUEST_DETAILS, CONFIRM_REQUEST, CANCEL_REQUEST; отдельный от FSM enum |
| agent-model/model/dto/UserMessagePayload.java | String claimedEpkId (организация), String claimedDigitalUserId (пользователь), String requestId, String externalSessionId, String text, SuggestionActionCode actionCode (nullable), List<AdditionalInfoEntry> additionalInfo (non-null, пустой допустим). Первые пять строк nonblank; текст/ID не trim; key уникальны |
| agent-model/model/dto/AdditionalInfoEntry.java | String key, String value; строки non-null, включая произвольное значение paymentId; без Jackson/JPA, UUID parsing и обработки платежа |
| agent-model/model/dto/SuggestionSpec.java | SuggestionActionCode actionCode, String text; оба обязательны, текст nonblank |
| agent-model/model/dto/Suggestion.java | UUID guid, SuggestionActionCode actionCode, String text; все обязательны, текст nonblank |
| agent-model/model/dto/AssistantMessage.java | String sessionId, String content, List<Suggestion> suggestions; обязательны, строки nonblank; пустой список разрешён, GUID/коды уникальны |
| agent-model/model/value/ChannelContext.java | String channelId, String authenticatedOrganizationEpkId, String authenticatedDigitalUserId; все nonblank, серверный источник, не mapping тела сообщения |
| agent-model/model/dto/ConversationQuery.java | String externalSessionId; nonblank |
| agent-model/model/dto/ConversationView.java | String sessionId, boolean inFlight, AssistantMessage lastResponse (nullable до первого ответа); sessionId ответа совпадает с sessionId представления |
| agent-model/model/dto/TestConversationRequest.java, TestConversationCreated.java | Вход String fixtureId; результат String sessionId, String epkId (организация), String digitalUserId; строки nonblank |
| agent-service/service/port/in/ConversationUseCase.java | @Idempotent AssistantMessage handle(UserMessagePayload message, ChannelContext channel); аннотация пока только маркер |
| agent-common/common/utils/Idempotent.java | Временная аннотация @Target(METHOD), @Retention(RUNTIME), @Documented; без элементов и исполняемого механизма |
| agent-service/service/port/in/ConversationQueryUseCase.java | ConversationView get(ConversationQuery query, ChannelContext channel) |
| agent-service/service/port/in/TestConversationUseCase.java | TestConversationCreated create(TestConversationRequest request, ChannelContext channel) |
| agent-common/common/mapper/CommonMapperConfig.java | Spring component model, constructor injection, unmappedTargetPolicy=ERROR |

Порты без implementations/beans. ChannelContext разделяет канал, организацию и пользователя; DTO не доказывает аутентификацию. Будущие адаптеры создают его из доверенного серверного источника, не из тела запроса. Конкретный механизм идентификации — P1.10/P1.11.

DomainEvent, CallContext/Deadline, Interpretation, corr envelope, DB/FSM SPI не создаются пустыми заглушками — Q-05. Mapper не вызывает FSM, LLM, сеть или БД. Общий путь интерпретации текста с кодом и без будет реализован позднее; P1.02 не заявляет проверку модели.

### 3.2. OpenAPI прототипа

Две спецификации OpenAPI 3.0.3:

- agent-api-in/src/main/openapi/gigaassistant.yaml: POST /api/v1/gigaassistant/turns, operationId handleTurn, 200 AssistantMessageDto.
- agent-ui-back/src/main/openapi/chat-ui.yaml: POST /test-ui/api/messages (handleMessage, 200), POST /test-ui/api/conversations (createConversation, 201), GET /test-ui/api/conversations/{id} (getConversation, 200).

UserMessageDto: required epkId/digitalUserId/requestId/sessionId/content — strings; optional action_code — non-null enum пяти кодов; optional additionalInfo — non-null массив AdditionalInfoEntryDto, [] допустим. Каждый элемент содержит required key/value (strings non-null), дополнительные свойства запрещены. Ключи регистрозависимы и не повторяются; paymentId не обязателен, другие ключи разрешены. Все ID opaque strings, не UUID. additionalProperties=false на сообщении. В запросе нет guid/paymentRef/event/state/replyTo/messageId; paymentId только внутри additionalInfo, без выделенного поведения.

AssistantMessageDto: required sessionId/content/suggestions; non-null массив SuggestionDto с разрешённым нулём элементов. SuggestionDto: required guid (string/uuid), action_code, text. Нет банковского статуса/реквизитов/внутренних версий/доставки SMS.

TestConversationRequestDto: required fixtureId. TestConversationCreatedDto: required sessionId/epkId/digitalUserId. ConversationViewDto: required sessionId/inFlight; optional lastResponse, non-null при наличии, отсутствует до первого ответа. Это последнее опубликованное сообщение, не история — Q-04. inFlight означает исполняющийся пользовательский ход, не банковскую заявку.

Принятые лимиты Q-02: epkId/sessionId/fixtureId — 1–128 символов; content — 1–4000; text предложения — 1–256; строки nonblank. Вход не обрезать/не trim. Длина в OpenAPI и Java считается одинаково, в Unicode code points: обычная @Size считает UTF-16, потому нужна явная проверка границы и тест supplementary Unicode. Лимиты приняты пользователем 28.09.2026.

Принятые транспортные ошибки Q-03: 400 неверный JSON/контракт, 401 нет идентификации, 403 чужая сессия, 404 неизвестная сессия/fixture, 409 занятая сессия, 500 непредвиденная ошибка, 503 невозможна обработка до принятия хода. Закрытый DTO {code,message}, enum INVALID_REQUEST/UNAUTHENTICATED/ACCESS_DENIED/NOT_FOUND/SESSION_BUSY/INTERNAL_ERROR/SERVICE_UNAVAILABLE; без исходного ввода/stacktrace/банковских данных. Каждая операция объявляет применимые коды. Ошибки LLM после принятия хода позднее формируют обычный технический ответ чата. HTTP mapping/advice реализуются на P1.10/P1.11.

GUID не возвращается во входе, одинаковый текст/код не обеспечивает дедупликацию. requestId предназначен для существующей внешней библиотеки идемпотентности; её механизм здесь не реализуется. Доступ/занятость здесь только документируются, не реализуются.

### 3.3. Генерация и mapping

В parent добавить версии-кандидаты из modules.md: OpenAPI Generator 7.25.0, MapStruct 1.6.3; совместимость подтвердит implement clean build. BOM P1.01 не менять. В двух адаптерах отдельные executions generate-sources: generatorName=spring, interfaceOnly=true, useSpringBoot3=true, skipDefaultInterface=true, useBeanValidation=true, openApiNullable=false. Отключить лишние docs/tests/supporting files; генерировать DTO и серверные интерфейсы без контроллеров/default implementations.

Output: target/generated-sources/openapi/{gigaassistant|chat-ui}. Пакеты каждой интеграции .dto.generated и .client.generated. Generated sources вручную не менять/не коммитить. POM адаптеров получают необходимые Spring Web/Jakarta Validation/Jackson зависимости и MapStruct API/processor; test зависимости — в проверяемых модулях. Версии централизованы parent/Boot. Service/model не получают web-зависимостей.

GigaAssistantMessageMapper и ChatUiMessageMapper — в пакетах соответствующих интеграций .mapper. Вход: epkId → claimedEpkId, digitalUserId → claimedDigitalUserId, requestId → requestId, sessionId → externalSessionId, content → text, actionCode → actionCode, additionalInfo → список AdditionalInfoEntry с дословными key/value и исходным порядком. Отдельные внешние enum преобразуются в доменный по именам; неизвестное не превращать в null/default. Выход сохраняет GUID, код, текст и порядок. Wire action_code генерируется как Java actionCode. UI mapper дополнительно преобразует create/query DTO; query не генерирует новые GUID. ChannelContext mapper не создаёт. paymentId не преобразуется mapper в доверенный paymentRef; requestId не генерируется вместо отсутствующего значения.

OpenAPI само не исполняет JSON-ограничения. В каждой интеграции малая .config/*JsonConfiguration и .validation/*MessageContractValidator для будущего HTTP подключения, сейчас проверяемые непосредственно. Настройки: unknown properties запрещены, scalar coercion запрещён, неизвестный enum не становится null. Explicit null обязательных полей/action_code/additionalInfo запрещён; отсутствующие action_code/additionalInfo допустимы. Setter/mixin с Nulls.FAIL позволяет различать missing и null без правки generated sources. Сериализация исключает nullable action_code/lastResponse точечно; suggestions=[] сохраняется, глобальный NON_EMPTY не подходит. Полноценное подключение к HTTP и MockMvc — P1.10/P1.11; прямой JSON-тест не выдаётся за работающий endpoint.

### 3.4. Данные и эффекты

Транзакции, persistence, lease/concurrency, retries/deadline, stub/real YAML и Liquibase здесь Н/П: только данные и преобразования. UUID выделяет будущий сервис/composer. Запись receipt вместе с состоянием и восстановление после перезапуска — P1.05–P1.08. Не создавать альтернативное in-memory хранилище для имитации этого результата.

### 3.5. Уточнения r4 по ответам пользователя

Обязательны `epkId` (организация в едином профиле клиента), `digitalUserId` (пользователь), `requestId` (текущий запрос), `sessionId`, `content`. Формулировка «все поля обязательны кроме additionalInfo» трактуется с сохранением уже согласованного правила: `action_code` отсутствует при ручном вводе и передаётся при клике. Это явно предъявляется в r4, а не меняет manual-сценарий молча.

```json
{
  "epkId": "epk-demo-org",
  "digitalUserId": "digital-user-1",
  "requestId": "request-001",
  "sessionId": "session-status-001",
  "content": "Статус",
  "action_code": "REQUEST_STATUS",
  "additionalInfo": [{"key": "paymentId", "value": "payment-uid-42"}]
}
```

`additionalInfo` можно опустить или передать пустой массив; explicit null и null-элементы отклоняются. В домене отсутствие нормализуется в пустой список (r4 immutable; r6 не гарантирует неизменяемость); mapping сохраняет значения и порядок присутствующих элементов. `key` и `value` — обязательные строки non-null без ограничения формата UUID; ключи сравниваются дословно и регистрозависимо, дубли запрещены. Другие ключи разрешены; ни один ключ, включая `paymentId`, не обязателен. Новых лимитов длины key/value и числа элементов не вводится; пустые строки key/value не запрещаются без отдельного требования. Актуальное написание `paymentId` заменяет раннее `paymentid`; переименование входных ключей/алиасы не добавляются. Значение paymentId — произвольная строка, оно только переносится: никакого выбора, проверки, закрепления или смены платежа по нему сейчас нет. Не передавать эти метаданные в LLM автоматически.

Прежние лимиты epkId/sessionId/fixture/content/suggestion text сохраняются; для digitalUserId/requestId задаётся nonblank без нового произвольного верхнего лимита. Все строки сохраняются дословно. UI created получает digitalUserId вместе с организационным epkId и sessionId; UI сможет сформировать обязательные поля будущего сообщения. Ответ чата не расширяется входными идентификаторами автоматически.

Идемпотентность реализует существующая внешняя библиотека с `@Idempotent`. Пока в `agent-common/common/utils/Idempotent.java` планируется временная документированная аннотация METHOD/RUNTIME без параметров, cache, AOP, БД и поведения. Место применения — общий для каналов `ConversationUseCase.handle`. При подключении реальной библиотеки проверить её импорт, параметры и требования к точке перехвата; если нужен concrete method, перенести/добавить аннотацию туда по контракту библиотеки. Совместимость временного маркера с библиотекой не обещается. Свои политики replay/conflict/pending, область уникальности и хранилище входной дедупликации не проектируются. requestId не заменяет внутренний turnId, corr requestId или submission idempotency key и не связывает согласие с показанным вопросом.

### 3.6. Русская документация

Все OpenAPI, интерфейсы/методы и DTO/поля P1.02 документируются на русском, включая порты, mapping, DTO, enum и аннотацию. По уточнению пользователя после r4 детализация сокращается примерно на 40%: оставить назначение, параметры/результат и существенные ограничения; убрать повторы схемы и сигнатур. Сохранить различие организации/пользователя, правила additionalInfo и отсутствие обработки paymentId/идемпотентности у маркера.

Generated DTO/API получают описания стандартным генератором из OpenAPI. Англоязычный служебный Javadoc допускается; четыре шаблона оформления удалены. Только beanValidationCore.mustache сохраняет согласованный подсчёт длины в Unicode code points. Target вручную не редактируется. Документация проверяется review без искусственных тестов. Правило краткости действует и в следующих шагах.

Затронутые архитектурные документы синхронизируются с разделением организации/пользователя, новыми примерами входа, только транспортным additionalInfo и внешней библиотекой идемпотентности. Платёжный контекст будущих workflow — отдельная задача их design; текущий шаг не создаёт привязку. ADR-001–003 сохраняются, новые миграции и библиотечная интеграция в P1.02 не добавляются.

### 3.7. Lombok DTO — r6, ответ Q-09

Заменить девять records `model/dto` (AdditionalInfoEntry, AssistantMessage, ConversationQuery, ConversationView, Suggestion, SuggestionSpec, TestConversationCreated, TestConversationRequest, UserMessagePayload) и `model/value/ChannelContext`. Имена/пакеты/поля сохранить. Классы: private поля, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`; без `@Data`, equals/hashCode/toString, ручных проверок и копирования. Краткий Javadoc перенести на поля, не наращивая объём.

В agent-model добавить Lombok (`provided`) и annotation processor с версией из Boot dependency management. MapStruct адаптеров читает скомпилированные bean accessors; clean reactor подтверждает совместимость. Новые binding/шаблоны без необходимости не добавлять; сохранить beanValidationCore. Вызовы `field()` заменить на `getField()`/`isInFlight()` в исходниках/тестах; wire DTO/OpenAPI не меняются.

Ответ пользователя: «пока логику никакую проверок в дто не делаем, кроме конструктора гетеров и сеттеров». Прежние record-гарантии non-null/nonblank, уникальности, согласованности sessionId и неизменяемости списков больше не предоставляются самими DTO. Допускается создание/изменение DTO с любыми значениями полей. DtoValidator, mapper hooks или другой перенос снятых проверок не вводятся. Существующие wire-валидаторы JSON/required/type/лимитов/дубликатов не удаляются; mapper сохраняет текущие преобразования, включая optional→[]. Не приписывать ему проверки, ранее выполнявшиеся только record-конструкторами. Сравнение DTO по значениям в тестах заменить проверкой нужных полей; библиотечные equals/hashCode не добавлять ради старых assertions.

### 3.8. Единые имена DTO — принято по Q-10

Направление относительно входного API приложения: `InDTO` приходит в приложение, `OutDTO` возвращается клиенту. Это не направление метода mapper. Внутренние DTO без однозначного направления получают `DTO`; для реально общих типов обоих направлений применяется то же правило. Пакеты сохраняются, доменные и wire-классы остаются отдельными. Таблица ниже заменяет прежние имена в §§3.1–3.7; остальные решения r6 сохраняются.

Проверены все десять DTO agent-model и generated модели двух адаптеров (6 GigaAssistant, 9 UI, включая enum); других DTO в исходниках нет. Таблица распространяется на обе интеграции, если схема присутствует:

| Текущий domain | Текущий generated/schema | Предложенное имя |
|---|---|---|
| UserMessagePayload | UserMessageDto | UserMessageInDTO |
| AdditionalInfoEntry | AdditionalInfoEntryDto | AdditionalInfoEntryInDTO |
| AssistantMessage | AssistantMessageDto | AssistantMessageOutDTO |
| Suggestion | SuggestionDto | SuggestionOutDTO |
| ConversationQuery | — (вход path id) | ConversationQueryInDTO |
| ConversationView | ConversationViewDto (UI) | ConversationViewOutDTO |
| TestConversationRequest | TestConversationRequestDto (UI) | TestConversationInDTO |
| TestConversationCreated | TestConversationCreatedDto (UI) | TestConversationOutDTO |
| SuggestionSpec | — | SuggestionSpecDTO |
| ChannelContext | — | ChannelContextDTO |
| — | ErrorDto | ErrorOutDTO |
| SuggestionActionCode (enum) | SuggestionActionCodeDto (enum) | SuggestionActionCode (enum, без DTO) |

AdditionalInfoEntry вложен только во вход, поэтому InDTO; использование обоими каналами не делает его двунаправленным. Suggestion вложен в ответ, поэтому OutDTO. SuggestionSpec — внутренняя заготовка ответа без GUID, ChannelContext — доверенный серверный контекст: не придумываем им wire-направление. Enum не DTO; отдельные enum интеграций и домена сохраняются, при конфликте imports использовать полные имена в mapper. То же относится к одноимённым domain/wire DTO.

OpenAPI Generator 7.25.0 сейчас получает имена напрямую из schemas, отдельного modelNameSuffix нет. При implement переименовать schemas/$ref в обеих исходных спецификациях и обновить ссылки в описаниях; clean generation должна дать точные имена с DTO. При нормализации акронима генератором зафиксировать точные modelNameMappings в POM, без новых шаблонов и правки target. Это изменение имён Java/схем, не JSON-формы или URL; потребители generated типов обновляют imports. Синхронизировать файлы domain, порты, mapper, JSON mixins/конфигурации, валидаторы, тесты и документацию. Правила простых Lombok DTO r6 сохраняются, новые проверки не вводятся.

## 4. Тестовый дизайн до кода

| Test ID | AC | Уровень / вход | Ожидаемый RED | GREEN |
|---|---|---|---|---|
| T-01 | 01,04 | Unit domain: «Да», nullable code, два suggestions/[], повтор GUID/кода, null/blank, изменение исходного списка | Нет инварианта или защитной копии | Дословные данные сохранены; невалидное отвергается; список неизменяем |
| T-02 | 01,02,05,06 | Unit MapStruct обоих адаптеров: manual/click, пять кодов, заданные UUID, query с/без ответа, fixture | Потерянные/непреобразованные поля | Полные mapping без новых UUID/доверия |
| T-03 | 01,02,03,06 | JSON/validation обоих адаптеров: положительные примеры и параметризованные missing/null/blank/type/unknown/extra/границы длины; сериализация []/UUID | Разрешён запрещённый вход или неверная форма JSON | Runtime JSON соответствует согласованному контракту |
| T-04 | 07 | Clean build и шесть существующих проверок P1.01 | Generation/processor ещё не подключены; build failure не подменяет предметный RED | clean verify без старых generated sources и пропущенных обязательных проверок |
| T-05 | 06,08–10 | Unit domain/mapping обоих адаптеров: manual/click, разные org/user ID, requestId, отсутствие/[]/несколько key/value, fixture created с digitalUserId, изменение исходного списка | Данные потеряны/перепутаны либо mutable; после harness тест исполним | Дословные ID/порядок, defensive copy, уникальность ключей, нормализация отсутствующего массива; контекст не создаётся из тела |
| T-06 | 08,09 | JSON/validation обоих адаптеров: отсутствие/[] additionalInfo, произвольный paymentId/другие key без paymentId, missing/null/type/blank обязательных ID, дубли, null-элемент/поле, лишнее свойство | Корректный новый вход отвергается либо запрещённый принимается | Контракт соблюдается; action_code optional manual, null запрещён; нет самовольного UUID/лимитов metadata; fixture/response/query регрессия проходит |
| R-01 | 11,12 | Review аннотации и всей русской документации, затем clean generation/verify | Искусственный RED не требуется: документация и маркер без поведения | METHOD/RUNTIME/Documented на handle, нет механизма идемпотентности; русские описания сохранены в generated API/DTO |

T-01–T-06 — исторический объём r2/r4; 150 тестов r4 пройдены. В r6 удалить устаревшие ожидания конструкторного reject/неизменяемости, сохранив wire-регрессию. T-07: минимальная проверка создания/изменения DTO без отклонения значений (предметный RED — старый конструктор ещё запрещает значения; compilation failure не RED). T-08: существующие mapping/JSON-тесты переводятся на bean accessors и проверки полей; GUID/порядок/ID/null-mapping и wire reject сохраняются. Не создавать тесты каждого Lombok-метода ради количества. Clean verify проверяет generation/MapStruct/bootstrap.

R-02 r7: rename не меняет поведение, искусственный RED не нужен. Обновить существующие mapping/JSON assertions/imports, выполнить clean verify; проверить точные generated имена и отсутствие старых ссылок в действующих исходниках. Wire-regression подтверждает прежние JSON/ошибки, review — неизменность схем кроме имён/ссылок. Исторические разделы r2–r6 не переписывать под новые имена.

Unit первыми. Минимальные сигнатуры/каркас для компиляции тестов допустимы до RED; инварианты/JSON настройки/mapping — после предметного RED. Ошибка сети/загрузки зависимости не RED. Не создавать искусственные architecture/Good-Bad/compile-failure fixtures. Изоляция сигнатур и настройка ERROR проверяются review и настоящей компиляцией mapper.

Команды: точечный mvn -pl agent-model,agent-api-in,agent-ui-back -am test; итоговый mvn clean verify один раз после изменений. Отчёты в target, в task только краткое свидетельство. DB/полноценный HTTP/Ultra/e2e здесь Н/П по границе шага, остаются обязательными на соответствующих следующих шагах.

## 5. Порядок implement

Ответ Q-10 внесён в r8; по обновлённому правилу согласование разрешает implement без дополнительной команды: Java-разработчик выполняет rename по таблице, обновляет исходные схемы/ссылки, clean generation и существующую регрессию R-02. Затем review, handoff и остановка; P1.03 не запускать.

## 6. Вопросы и развилки

Q-01–Q-08 закрыты; r4 была согласована и реализована. Q-09 закрыт ответом: простые Lombok DTO без проверок/нового валидатора. r6 согласована; implement разрешён.

| ID | Принятое решение | Ответ / статус |
|---|---|---|
| Q-01 | A — неизвестный action_code отклоняется на wire-границе, HTTP 400 до LLM. Известный неактуальный код обрабатывает policy на P1.08; raw-code вариант B не выбран | «принимаю», 28.09.2026 / закрыт |
| Q-02 | ID 128/content 4000/suggestion text 256, строки nonblank, без обрезания | «принимаю», 28.09.2026 / закрыт |
| Q-03 | HTTP-коды и закрытый {code,message} из §3.2 для локального прототипного контракта; 403 и 404 различаются | «принимаю», 28.09.2026 / закрыт |
| Q-04 | A — query sessionId,inFlight,lastResponse?; полной истории нет | «принимаю», 28.09.2026 / закрыт |
| Q-05 | A — входные порты §3.1 сейчас, corr/LLM/DB/FSM со своими DTO в соответствующих следующих шагах | «принимаю», 28.09.2026 / закрыт |

| ID | Варианты / рекомендация | Ответ / статус |
|---|---|---|
| Q-06 | Все основные поля обязательны; additionalInfo optional, другие ключи разрешены, дубли запрещены; paymentId произвольная строка. action_code optional при ручном вводе по прежнему правилу. Детальная трактовка §3.5 предъявляется в r4 | Ответ пользователя 28.09.2026 / закрыт |
| Q-07 | Идемпотентность выполняет существующая внешняя библиотека; сейчас временная @Idempotent без поведения. Собственные replay/conflict/pending политики исключены | Ответ пользователя 28.09.2026 / закрыт |
| Q-08 | epkId — организация в едином профиле клиента, digitalUserId — пользователь; paymentId сейчас только передаётся, никакой обработки/привязки | Ответ пользователя 28.09.2026 / закрыт |
| Q-09 | Только конструкторы/getters/setters; без проверок в DTO и без нового слоя проверок при этой замене. Wire-проверки сохраняются | Ответ пользователя / закрыт |
| Q-10 | Принята схема XInDTO/XOutDTO относительно входного API, внутренние XDTO, enum без DTO; точная таблица §3.8 | «cjukfcjdfyj» = «согласовано», 28.09.2026 / закрыт |

## 7. Риски и воспроизведение

Ограничение r6: DTO больше не обеспечивает прежние инварианты и value equality. Review проверяет корректность bean mapping и сохранность wire-проверок; clean reactor — Lombok/MapStruct. БД/Docker/Ultra не требуются.

После implement демонстрация — JSON/mapping tests и clean verify; работающий чат не обещается. requestId добавляется в контракт по запросу пользователя, но дедупликация и защита устаревшего подтверждения требуют разных механизмов; первое планируется по Q-07, второе не возникает автоматически из нового поля.

## 8. Согласование ревизии

| Ревизия | Изменение | Открытые вопросы | Решение пользователя |
|---|---|---|---|
| 1 | Первичный дизайн с вариантами | Q-01–Q-05 на момент подготовки | 28.09.2026 пользователь принял рекомендации: «принимаю»; ответы внесены в r2 |
| 2 | Зафиксированы принятые решения Q-01–Q-05, обновлены объём и контракты | Нет | Согласована: «согласовано», 28.09.2026 |
| 3 | Добавлены digitalUserId/requestId/additionalInfo, AC/тестовый дизайн и предложения по идемпотентности/платёжной привязке | Q-06–Q-08 | Не согласована |
| 4 | Внесены ответы Q-06–Q-08, optional additionalInfo/paymentId, разделение org/user, временная @Idempotent, полная русская документация | Нет | Согласована 28.09.2026: «все согласовано , сделай дореализацию»; получена команда дореализации |
| 5 | Все records → Lombok DTO; предложен перенос инвариантов на границу использования | Q-09 | Не согласована; предложение заменено ответом в r6 |
| 6 | Внесён ответ Q-09: простые DTO без проверок/нового валидатора | Нет | Согласована; implement разрешён: «согасовано, меняй» |
| 7 | Суффикс DTO и направления In/Out, таблица переименований | Q-10 на момент подготовки | Схема явно согласована: «cjukfcjdfyj» («согласовано»), 28.09.2026 |
| 8 | Зафиксирован ответ Q-10; решения r7 не менялись | Нет | Ответ принят; по обновлённому правилу согласование разрешает implement |

**Схема r7 согласована:** «cjukfcjdfyj» («согласовано»), 28.09.2026. **Текущая r8:** фиксирует согласованную схему без изменения решений; открытых вопросов нет. Пользователь изменил правило: согласование сразу разрешает implement. Реализация завершена, передана на проверку.
