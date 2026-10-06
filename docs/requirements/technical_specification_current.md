# POC: техническая спецификация текущего кода

**Task04 — принята архитектура, реализация не начата:** [дизайн](../tasks/task04/impl_design.md) предусматривает user_input, JSON MessageAnalysisDTO, классификацию до семафора, прямой inform по шаблону при отсутствии команды и отдельный прогон TP/TN/FP/FN. [Список задач](../tasks/task04/tasks.md) и дизайн ожидают согласования. Ниже остаётся фактическая спецификация существующего кода.

**Task03:** реализованы штатная автоконфигурация GigaChat, bean `gigaChatClient`, проверка пустого ответа и ранняя проверка настроек. `application.yaml` импортирует локальный `.env`; режим `none` не требует ключа, `gigachat` использует OAuth по ключу. Подробности и проверяемые ограничения — в [дизайне](../tasks/task03/impl_design.md) и [задачах](../tasks/task03/tasks.md).

**Срез:** 05.10.2026, рабочее дерево с изменениями task01/task02: прямое использование SSM по [ADR-021](../adr/0021-direct-spring-statemachine.md). Документ описывает прочитанные исходники и контракты; приёмка task01/task02 ожидает ручного UI smoke. [Требования](requirements_current.md), [C4/sequence](c4/README.md), [ADR](../adr/README.md), [план task02](../tasks/task02/tasks.md).

## 1. Сборка и границы модулей

[Parent POM](../../pom.xml): Java 21, Maven 3.9.x, Spring Boot 3.5.15, Spring Cloud 2025.0.3, Spring Statemachine 4.0.2, MapStruct 1.6.3, OpenAPI Generator 7.25.0. В reactor девять модулей; только `agent-main` собирается в исполняемый Spring Boot JAR.

| Модуль | Ответственность |
|---|---|
| agent-common | Общие настройки mapping, ключи ACL, временная аннотация Idempotent. |
| agent-model | DTO/enums и SessionEntity; без типов SSM и callback-контрактов переходов. |
| agent-db | Семафор, repositories, native JPA persister, сериализация SSM и Liquibase. |
| agent-service | Оркестратор, SessionExecutionService с рабочей транзакцией и прямым API SSM, SessionStateMachineConfiguration с каталогом операций и переходами, отдельные guards/actions, StoredSnapshotValidator, renderer, WorkflowManager-порт. |
| agent-api-in | Generated ACL, AclController, AclInputAdapter, AclMapper, JSON-конфигурация. |
| agent-api-out | InvestCorrAdapter, generated corr/Feign и локальный stub. |
| agent-ui-back | ACL-зеркало, fixture helper, раздача статических ресурсов чата. |
| agent-ui | HTML/CSS/JavaScript чата, клиентские тесты; выполняется в браузере. |
| agent-main | Wiring, профили, упаковка и интеграционная приёмка. |

`agent-service` напрямую использует SSM и инфраструктуру хранения `agent-db`; обратной зависимости db → service нет. Собственные FlowDefinition/FsmEngine и протокол callback-планов удалены. SSM-типы используются в service.fsm и инфраструктуре хранения, но не в agent-model, входных use cases и WorkflowManager. Backend-модули исполняются в одной JVM, а не как отдельные сетевые сервисы. Границы определены [ADR-005](../adr/0005-module-hierarchy.md) и [ADR-021](../adr/0021-direct-spring-statemachine.md).

## 2. HTTP и нормализация входа

| Маршрут | Реализация / назначение |
|---|---|
| `POST /api/v1/ai/agents/{agent_code}` | AclController → общий AclInputAdapter. |
| `POST /local-api/api/v1/ai/agents/{agent_code}` | LocalAclController → тот же адаптер напрямую, без внутреннего HTTP. |
| `GET /local-api/v1/fixtures` | LocalHelperController: metadata синтетического платежа, suggestions, agentCode, integrationMode. |
| `GET /test-ui/` и статические ресурсы | Браузерный тестовый чат. |

Маршруты POC включены профилем `poc-local`. GET сессии отсутствует. Источники генерации: [acl-poc.yaml](../../agent-api-in/src/main/resources/openapi/acl-poc.yaml), [poc-local-api.yaml](../../agent-ui-back/src/main/resources/openapi/poc-local-api.yaml). Исходная схема GA и её полная поверхность не являются реализованным API этого приложения.

Адаптер требует JSON-сообщение, полные UUID заголовков `Request-Id` и `Gigachat-Session-Id`, `message.version="1.6"`, sender/receiver, UUID conversation_id/reply_with. Receiver и agent_code должны соответствовать `poc.acl.agent-code`. Необслуживаемый agent_code даёт 404 без тела. Некорректные данные, не позволяющие построить корреляцию, дают транспортный 400 без выдуманного ACL ID. При различии Request-Id/reply_with ответ failure коррелируется по заголовку; FSM не вызывается.

| ACL | SessionTurnInDTO.event (SessionEvent) | Операция |
|---|---|---|
| `request` + зарегистрированный `content.action_code=status` | SELECT | operation=STATUS |
| `accept_propose` | CONFIRM | Из сохранённой подготовки, не из сообщения |
| `reject_propose` | CANCEL | Общее действие отмены |

AclMapper сразу устанавливает SessionEvent; отдельного TurnCommand и повторного перевода команды в событие нет.

Для accept/reject content не определяет команду. Согласуемые значения копируются из state[] типа CONFIRMATION в список без удаления дубликатов/null. Типы MEMORY, REDIRECTION, OPERATION не добавляются в подтверждение; неизвестный/отсутствующий type или null-элемент отклоняется как форма входа.

Metadata отображается в PaymentContextInDTO: organization.epk_id → epkId; customer_info.digital_user_id → digitalUserId; семь известных additional_info → paymentId/paymentNumber/paymentDate/amount/currency/recipientName/organizationName. Дата преобразуется в LocalDate, сумма — BigDecimal. Пропуск оставляет null для дальнейшей проверки; явный JSON null, пустое значение и дубликат известного ключа дают INVALID_REQUEST. Полноту первого контекста и неизменность последующего проверяют guards под семафором.

## 3. Программная схема операции и FSM

[SessionStateMachineConfiguration.statusOperation](../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionStateMachineConfiguration.java) регистрирует единственную OperationDefinition:

- operation=STATUS, actionCode=`status`, title/messageText=`Запросить статус`;
- proposalTemplateKey=`status.proposal`;
- confirmationFields: `paymentNumber` из PAYMENT_NUMBER, тип STRING;
- PrepareOperationAction и ExecutePreparedOperationAction; дополнительных guards STATUS нет.

Эта же конфигурация реализует OperationCatalog и создаёт машину через `StateMachineBuilder<SessionState, SessionEvent>`, задавая четыре перехода штатным API. Дубликаты operation/actionCode отклоняются при запуске; собственного списка DTO переходов и отдельного OperationRegistry нет. OperationSpecDTO содержит только метаданные и схему, OperationDefinition связывает их с исполняемыми компонентами.

| Source / event → target | Общие guards, строго по порядку | Action |
|---|---|---|
| CHOOSING_REQUEST_TYPE / SELECT → AWAITING_CONFIRM | InitialContext, ContextConsistency | PrepareOperation |
| CHOOSING_REQUEST_TYPE / CANCEL → CHOOSING_REQUEST_TYPE | ExistingSession, ContextConsistency | ReportNoPreparation |
| AWAITING_CONFIRM / CANCEL → CHOOSING_REQUEST_TYPE | ExistingSession, ContextConsistency | CancelPreparation |
| AWAITING_CONFIRM / CONFIRM → COMPLETED | ActivePreparation, ContextConsistency, PreparationNumber, SeparateConfirmation, ConfirmationData | ExecutePreparedOperation |

Отдельные классы guards реализуют `Guard<SessionState, SessionEvent>.evaluate(StateContext)`, actions — `Action<SessionState, SessionEvent>.execute(StateContext)`. Функция `guards` в конфигурации вызывает общие проверки в указанном порядке, затем дополнительные prepareGuards/executeGuards операции; первый false останавливает цепочку и запрещает action. Операция для SELECT берётся из входа, для CONFIRM — из сохранённой подготовки; CANCEL использует общее действие. Target определяется переходом SSM; конфигурация связывает компоненты, а прикладные алгоритмы остаются в их классах.

SessionExecutionContext создаётся для одного события и передаётся через message header: input, рабочий snapshot, выбранная операция, первый rejectionCode, ошибка callback, признаки допуска/завершения action и восстановленной терминальности. Он не сохраняется в ExtendedState и не хранится в singleton-полях. Техническая функция `tracked` отмечает завершение action и сохраняет ошибку перед повторным выбросом, чтобы перехват внутри SSM не превратился в успешный commit.

PrepareOperationAction копирует доверенный контекст, увеличивает lastPreparationNo через Math.incrementExact, создаёт UUID подготовки, сохраняет preparedRequestId и confirmationSnapshot, включая служебный preparationNo. ExecutePreparedOperationAction записывает confirmedRequestId/confirmedAt, вызывает WorkflowManager из серверной подготовки и устанавливает REQUEST_SUBMITTED. CancelPreparationAction удаляет только подготовку; ReportNoPreparationAction устанавливает NO_ACTIVE_PREPARATION.

Номер подтверждения разбирается как `[0-9]+`, положительный long, и сравнивается численно с активным preparationNo. Ведущие нули служебного номера допустимы текущим parser. Бизнес-номер paymentNumber сравнивается посимвольно, включая ведущие нули и пробелы. Универсальная схема поддерживает STRING, DATE (ISO LocalDate), DECIMAL (BigDecimal.compareTo); в рабочем STATUS используется только STRING. Неполный/лишний/дублированный набор запрещён; сначала проверяется формат всего бизнес-набора, затем совпадение. Description игнорируется.

Пример state успешного предложения; accept_propose должен вернуть эти пары в той же сессии с новым Request-Id:

```json
[
  {"key":"paymentNumber","value":"00042","type":"CONFIRMATION","description":"Номер платежа"},
  {"key":"preparationNo","value":"1","type":"CONFIRMATION","description":"Номер подготовки"}
]
```

## 4. Оркестрация и транзакционная граница

[SessionTurnOrchestrator.handle](../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/turn/SessionTurnOrchestrator.java) сразу возвращает validationError без доступа к БД. Для нормализованного входа порядок следующий:

1. `SessionExecutionService.ensureSemaphore` вызывает `SessionSemaphore.ensure` с REQUIRES_NEW: найти/создать agent_session и завершить отдельный commit. Конфликт первой вставки проверяется после rollback; только существующая строка позволяет продолжить.
2. Spring открывает рабочую транзакцию `SessionExecutionService.process`. `SessionRepository.getAndLock` берёт PESSIMISTIC_WRITE с lock.timeout=0 (PostgreSQL FOR UPDATE NOWAIT). Конфликт → SESSION_BUSY.
3. Приватный `processLocked` того же сервиса проверяет наличие native-записи и formatId, создаёт один экземпляр SSM через `SessionStateMachineConfiguration.create`. Существующий снимок восстанавливается штатным persister и проверяется; отсутствующая FSM запускается в CHOOSING_REQUEST_TYPE. Бизнес-допуск первого хода остаётся у guards.
4. Через `sendEvent` отправляется входной SessionEvent с SessionExecutionContext в header, ожидается `complete()`. SSM вызывает штатные guards и синхронный transition action. Затем сервис проверяет ошибку callback/машины, rejectionCode и завершение допущенного action; перехваченная SSM ошибка доводится до транзакционной границы.
5. Отказ guard выбрасывает типизированное исключение до persist. Если переход вообще не найден, существующий снимок получает INVALID_COMMAND либо SESSION_COMPLETED: state/подготовка прежние, но lastRequestId/lastResult/projectionVersion обновляются и сохраняются. Без инициализированного контекста native save запрещён.
6. После успешного Action записываются состояние, предметный исход и ExtendedState; штатный persister сохраняет **тот же** экземпляр FSM. В finally выполняется stopReactively().block(). Ошибка остановки вызывает rollback либо добавляется к основной ошибке.
7. Spring завершает commit/rollback и освобождает блокировку. Только затем оркестратор получает snapshot либо исключение и формирует ответ. Renderer не перечитывает БД; его ошибка после commit не вызывает повтор Action.

Семафор — постоянная строка, а не признак занятости. Блокировка держится в том числе во время внешнего вызова. Конкурентная вставка первого семафора может ожидать unique constraint; это не рабочая очередь NOWAIT. Нет кеша живых FSM, scheduler hopping, doAction, owner/fencing или второго пути исполнения.

## 5. Хранение и внутреннее чтение

Liquibase master включает последовательные changeset 0001–0003; история не переписывается. После 0003 предметное хранение использует:

| Объект | Состав / назначение |
|---|---|
| `agent_session` | UUID session_id, format_id; постоянный семафор. |
| `state_machine` | machine_id=sessionId.toString(), state, state_machine_context; native JPA Spring Statemachine. Контекст — BLOB H2 или PostgreSQL OID/LOB. |
| ExtendedState | sessionId, context, projectionVersion, lastPreparationNo, lastResult, lastRequestId; preparation при наличии. |
| PreparationDTO | preparationId, preparationNo, preparedRequestId, operation, копия context, confirmationSnapshot, confirmedRequestId, confirmedAt. |

Готовые ответы, summary/templateId, wire ACL, command journal, outbox и corr-attempts не сохраняются. Таблицы прежних binding/command/execution/corr удалены changeset 0003. Проекционная версия не является expectedRevision или ключом согласия.

Текущий [FORMAT_ID](../../agent-db/src/main/java/ru/sberbank/pprb/agent/db/session/SessionSemaphore.java) — `status-v3`. Формат, сохраняемые DTO, ключи ExtendedState и Kryo ID при task02 не изменены; совместимость проверяется восстановлением и подтверждением [образца до рефакторинга](../../agent-main/src/test/resources/fsm/status-v3-before.base64). В обработке хода formatId проверяется до restore даже для старого семафора без native-записи. Старые status-v2/legacy-v1 не конвертируются. При внутреннем read отсутствие native-записи возвращает empty до проверки formatId.

[NativePersistenceConfiguration](../../agent-db/src/main/java/ru/sberbank/pprb/agent/db/config/NativePersistenceConfiguration.java) использует DefaultStateMachinePersister и JpaRepositoryStateMachinePersist, фиксированные Kryo ID 100–110 с сохранённым пропуском 103; ConfirmationValueDTO имеет ID 110. [StoredSnapshotValidator](../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/StoredSnapshotValidator.java) в agent-service проверяет структуру сохранённого контекста/подготовки, согласованность копий, номера, согласия и зарегистрированной схемы. Он не проверяет новое сообщение и не выбирает переход. SessionPersistenceException также находится в service.fsm и передаёт код ошибки на границу оркестратора.

`SessionQueryUseCase.find` → `SessionTurnOrchestrator.find` → `SessionExecutionService.find` выполняет короткую транзакцию с PESSIMISTIC_READ/NOWAIT (FOR SHARE), restore/проверку/stop и возвращает `Optional<SessionSnapshotDTO>` после завершения транзакции. Нет ensure, sendEvent, persist, renderer или corr. Отсутствующий семафор/native → empty. Транзакция не readOnly из-за FOR SHARE PostgreSQL. HTTP-маршрута для этого use case нет.

## 6. Внешний менеджер

Порт: `void WorkflowManager.callOperation(Operation operation, OperationContext context)`. OperationContext содержит operationId=preparationId, epkId, digitalUserId, paymentId, необязательные changes; STATUS передаёт changes=null.

[InvestCorrAdapter](../../agent-api-out/src/main/java/ru/sberbank/pprb/agent/investcorr/service/InvestCorrAdapter.java) принимает только STATUS без changes. MapStruct формирует generated StatusRequest; InvestCorrClient выполняет один submit. В real-режиме Feign вызывает `POST /prototype/v1/status-requests` по [стендовому YAML](../../agent-api-out/src/main/resources/openapi/invest-corr-prototype.yaml).

| Wire | Значение |
|---|---|
| `Idempotency-Key` | operationId.toString() |
| `clientRequestId` | Тот же operationId |
| `operation` | `status` |
| `epkId`, `digitalUserId`, `paymentId` | Сохранённая серверная подготовка |

Успех требует не-null ответа, outcome=ACCEPTED и непустого reference. REJECTED, неполный ответ и transport/decode/HTTP exception превращаются в исключение Action и rollback; upstream reasonText клиенту не передаётся. Ответ не превращается в событие FSM или банковский результат.

CorrFeignConfiguration закрепляет Retryer.NEVER_RETRY для этого клиента; заданный retryer и неположительные connect/read timeout запрещены. По YAML оба timeout по 2000 мс; учитываются default и настройки по имени клиента. Fallback на stub отсутствует. Stub выбирается конфигурацией и моделирует ACCEPTED, REJECTED, TIMEOUT, ACCEPTED_RESPONSE_LOST; дедупликации в нём нет.

Внешний эффект и локальный commit не атомарны. Ошибка persist, stop или commit после corr не отменяет его действие. Повторная явная команда после rollback может снова вызвать corr с той же preparationId. Аннотация Idempotent на handle получает ключ `sessionId:requestId`, но библиотека отсутствует: сохранённого replay и проверки hash нет.

## 7. Ответ и ошибки

[SessionResponseRenderer](../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/turn/SessionResponseRenderer.java) после транзакции выбирает шаблон из [session-responses.properties](../../agent-service/src/main/resources/session-responses.properties) и использует сохранённый confirmationSnapshot. Для STATUS summary содержит только paymentNumber. OperationCatalog — общий источник начальных fixture-suggestions и последующих предложений выбора. Тексты/схемы проверяются при создании renderer в пределах реализованных проверок шаблонов.

`render(snapshot)` сразу возвращает TurnOutDTO: requestId, result, terminal, confirmation и availableOperations. Summary используется локально при формировании ResultDTO. Вложенные SessionViewOutDTO/PreparationViewDTO и SessionResponseMapper удалены; PreparationMapper сохраняется для структурного копирования контекста. Ошибки используют тот же TurnOutDTO; при отказе до получения снимка списки пусты. AclMapper читает параметры и операции напрямую, внешний ACL-контракт не изменён.

AclMapper переставляет sender/receiver, сохраняет conversation_id, устанавливает in_reply_to из Request-Id; content.status_code — строковый HTTP-код. ACL не возвращает отдельное поле с внутренним ResultCode.

| Внутренний исход | HTTP / ACL | Дополнительные поля |
|---|---|---|
| CONFIRMATION_REQUIRED | 200 / propose | result, state CONFIRMATION, final_message=false |
| PREPARATION_CANCELLED, NO_ACTIVE_PREPARATION | 200 / inform | result, suggestions, final_message=false |
| REQUEST_SUBMITTED | 200 / inform | result, final_message=true |
| INVALID_REQUEST/COMMAND, UNSUPPORTED_COMMAND, CONTEXT_MISMATCH | 400 / failure | reason; без suggestions/state предложения |
| INVALID_CONFIRMATION, STALE_PREPARATION, CONFIRMATION_MISMATCH | 400 / failure | reason; guard-отказ без native save |
| SESSION_BUSY, SESSION_COMPLETED | 400 / failure | final_message=true при известной завершённой FSM |
| SESSION_NOT_FOUND | 404 / failure | Защитная внутренняя ветка отсутствующего семафора, не GET API |
| OPERATION_FAILED, STORAGE_UNAVAILABLE | 500 / failure | reason без обещания отсутствия внешнего эффекта |

Transport validation перед FSM не узнаёт терминальность. Сбой Spring commit обрабатывается оркестратором как STORAGE_UNAVAILABLE, terminal=false. Ошибка renderer происходит за пределами try обработки транзакции; стабильный ACL failure для неё этим кодом не гарантирован, но commit уже не отменяется и внешний вызов не повторяется.

## 8. Конфигурация и запуск

[application.yaml](../../agent-main/src/main/resources/application.yaml) и профильные YAML задают параметры без резервных Java-default. Нужен ровно один профиль БД: h2 либо postgres. bootstrap несовместим с ними и запускает только каркас без web/БД. Для демонстрации API используется poc-local вместе с выбранной БД.

| Настройка / окружение | Значение или назначение |
|---|---|
| SERVER_PORT / SERVER_ADDRESS | 8080; в poc-local адрес по умолчанию 127.0.0.1 |
| POC_ACL_AGENT_CODE | invest-pro |
| POC_RESPONSES_CATALOG / POC_PAYMENT_DATE_FORMAT | session-responses / dd.MM.yyyy |
| POC_H2_URL / POC_H2_USER / POC_H2_PASSWORD | По умолчанию jdbc:h2:file:./data/poc;WRITE_DELAY=0, sa, пустой пароль |
| POC_POSTGRES_URL / POC_POSTGRES_USER / POC_POSTGRES_PASSWORD | Обязательные параметры выбранной PostgreSQL |
| INVEST_CORR_STUB / INVEST_CORR_STUB_SCENARIO | В poc-local: enabled / accepted |
| INVEST_CORR_NAME / INVEST_CORR_URL | invest-corr / пустой URL; для real требуется абсолютный HTTP(S)-адрес |
| FEIGN_CONNECT_TIMEOUT / FEIGN_READ_TIMEOUT | По 2000 мс |

Режим corr real включается `INVEST_CORR_STUB=disabled`; конфигурация не переключается на stub при ошибке. Имя corr определяет ключ индивидуальной настройки `spring.cloud.openfeign.client.config`. Hibernate ddl-auto=validate, sql.init.mode=never, open-in-view=false; схему меняет Liquibase. Независимо от corr/БД `GIGACHAT_MODE=gigachat` включает клиент модели; по умолчанию `none`, без credentials. Bootstrap исключает автоконфигурацию GigaChat; сочетание bootstrap+gigachat отклоняется.

```powershell
mvn -pl agent-main -am verify
java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=poc-local,h2
```

Для PostgreSQL выбрать `poc-local,postgres` и задать параметры подключения. Эти команды приведены для воспроизведения; при подготовке документа они не выполнялись.

## 9. Проверки и расхождения с прежними описаниями

В [task02](../tasks/task02/tasks.md) зафиксирован полный `mvn -B -Pintegration-tests clean verify` с отдельной тестовой PostgreSQL (`POC_TEST_POSTGRES_URL/USER/PASSWORD`): 103 успешные проверки без ошибок и пропусков. `node --test agent-ui/src/test/js/session.test.mjs` — 7/7. [Независимая валидация](../tasks/task02/validation.md) выполнена; обязательный ручной UI smoke ещё открыт. При актуализации документа проверки приложения не повторялись. Карта тестов дана в [требованиях](requirements_current.md#8-проверяемость-и-статус).

| Прежнее описание | Фактический срез |
|---|---|
| Восемь модулей | Девять, включая agent-ui. |
| Сводка всех реквизитов платежа | Шаблон STATUS показывает paymentNumber; state содержит paymentNumber + preparationNo. |
| Согласие без идентификации конкретной подготовки | preparationNo, preparedRequestId и точное сравнение confirmationSnapshot. |
| Заменяемый FsmEngine/FlowDefinition, планы и DTO переходов | SessionExecutionService и штатный SSM builder/Guard/Action в agent-service; agent-db отвечает за хранение. |
| TurnCommand и повторный перевод в SessionEvent | AclMapper сразу формирует SELECT/CONFIRM/CANCEL в SessionTurnInDTO.event. |
| Вложенные выходные проекции и renderer при find | Плоский TurnOutDTO для ответа; внутренний find возвращает SessionSnapshotDTO без renderer. |
| status-v2 | status-v3, без автоматического переноса старых снимков. |
| Helper GET сессии / восстановление чата после reload | Только fixtures по HTTP; find существует внутри приложения, UI начинает новый диалог. |
| Любой отклонённый ход не записывает снимок | Это верно для guard denial; отсутствие перехода обновляет метаданные результата существующей FSM. |
| Прежние 81 успешная проверка подтверждают текущий POC | Результат task02 — 103 Maven + 7 JS; приёмка ожидает ручного UI smoke. |

Реальные corr/GA-контракты, библиотека идемпотентности, банковская авторизация, утверждение текстов и Outbox/УСС остаются отложенными. Этот документ не меняет ADR и не закрывает их внешние зависимости.
