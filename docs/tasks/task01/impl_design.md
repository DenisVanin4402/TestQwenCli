# task01. Дизайн реализации

**Уточнение 05.10.2026:** согласованная [task02](../task02/impl_design.md) заменяет техническую часть этого дизайна о FlowDefinition/FsmEngine/TransitionPlan и вложенных выходных проекциях. Поведенческие требования, схемы подтверждения, preparationNo и отдельные файлы guards/actions сохраняются. Текущая приёмка находится в [задачах task02](../task02/tasks.md); этот документ не требует восстановления удалённых обёрток.

**Редакция:** 05.10.2026. **Статус:** согласован пользователем 05.10.2026; реализация начата.

## 1. Проверенная исходная реализация

Проверены исходники agent-model, agent-service, agent-db, agent-api-in, agent-ui-back, agent-ui и тесты agent-main, а также действующие ADR и спецификация этапа 2.

| Место | Факт и последствие для реализации |
|---|---|
| AclMapper.toInput()/command()/confirmation() | Входящий state не читается; status зашит в mapper; исходящие шесть полей берутся из view.context. Все три места требуют изменения. |
| SessionFlowDefinition.transitions() | Четыре перехода; prepare/cancel/confirm — private-методы, guards — лямбды. Алгоритмы переносим в классы. |
| FlowTransitionDTO | Сейчас BiPredicate и BiConsumer. Boolean не передаёт причину отказа и выбранную привязку операции. Расширяем существующий контракт перехода. |
| NativeMachineStore.process()/create()/event()/snapshot() | Первый SELECT_STATUS, проверки входного контекста и operation == STATUS зашиты в инфраструктуре. Результат ACCEPTED используется как признак успеха. Убираем бизнес-ветки и учитываем фактический допуск/action. |
| ContextBinding | initialContext()/verify() смешивают бизнес-проверки входа и контроль хранения. Входные проверки уходят в guards, копирование — в action; техническая проверка снимка остаётся в agent-db. |
| PreparationDTO / SessionSnapshotDTO | Уже есть preparationId, preparationNo и сохраняемый lastPreparationNo. Нет отдельного снимка согласуемых значений и Request-Id создания подготовки. |
| SessionResponseRenderer | До анализа результата читает все шесть реквизитов. Его нельзя вызывать для отказа первого сообщения с неполным контекстом. |
| SessionTurnOrchestrator | Уже преобразует SessionPersistenceException в failure без повторного чтения. Этот путь подходит для типизированного отказа guard. |
| SessionSemaphore / NativePersistenceConfiguration | formatId=status-v2, Kryo-регистрации 100–109 с пропуском 103. Изменение PreparationDTO требует нового формата. |
| LocalHelperController / TestSession.send() | Начальные suggestions создаёт ACL mapper. UI уже возвращает state при подтверждении и отказе; алгоритм сравнения в браузере не нужен. |

Исходные точки: [mapper](../../../agent-api-in/src/main/java/ru/sberbank/pprb/agent/gigaassistant/mapper/AclMapper.java), [renderer](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/turn/SessionResponseRenderer.java). Прежние SessionFlowDefinition и NativeMachineStore заменены в task02 на [штатную конфигурацию SSM](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionStateMachineConfiguration.java) и [SessionExecutionService](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionExecutionService.java).

## 2. Объём и наблюдаемое поведение

Реализуется общий механизм и одна рабочая операция STATUS. Её CONFIRMATION содержит ровно paymentNumber и preparationNo. Первый SELECT по-прежнему требует полный доверенный контекст платежа: уменьшение согласуемого набора не уменьшает исходные данные для принадлежности/исполнения.

accept_propose сравнивается с активной серверной подготовкой; только после всех guards вызывается WorkflowManager. reject_propose удаляет подготовку, не требует state и не отменяет банковский платёж. Повторный SELECT после отказа выдаёт новый preparationNo даже при том же номере платежа. В AWAITING_CONFIRM повторный SELECT запрещён; COMPLETED остаётся терминальным.

Не добавляются реальные RECALL/изменение реквизитов, endpoints, таблицы аудита, retry, второй движок, EventChecks или универсальные плагины. Исходный внешний YAML не меняется. UI продолжает обращаться к зеркалу в agent-ui-back.

## 3. Внутренние данные и контракты

Корень Java-пакетов — ru.sberbank.pprb.agent. DTO — обычные классы по ADR-006; без бизнес-валидации в конструкторах и без generated ACL/SSM типов.

| Модуль / тип | Изменение |
|---|---|
| model.enums.TurnCommand | SELECT_STATUS заменить на SELECT_OPERATION; ACCEPT_PROPOSAL и REJECT_PROPOSAL сохранить. SessionEvent остаётся SELECT/CONFIRM/CANCEL. Operation пока содержит только STATUS. |
| model.dto.turn.SessionTurnInDTO | Добавить Operation operation для SELECT и List<ConfirmationValueDTO> confirmation. Для CONFIRM/CANCEL operation не используется. Не сворачивать входные пары в Map. validationError оставить для транспортных ошибок. |
| model.dto.payment.ConfirmationValueDTO | Новый ключ/value, оба String. Используется во входе и в независимой копии сохранённого снимка. Во входе сохраняются дубли, null/пустые значения; валидность определяют guards. |
| model.dto.operation.ConfirmationFieldDTO | Декларация key, source, valueType, description. source — перечисление известных полей платежа; valueType — STRING, DATE или DECIMAL. preparationNo зарезервирован отдельно. |
| model.dto.operation.OperationSpecDTO | Код Operation, actionCode, подпись/текст саджеста, ключ шаблона предложения, упорядоченная схема confirmationFields. Конструктор сохраняет List.copyOf(confirmationFields); builder использует тот же конструктор. Нет функций, guards/actions и бизнес-методов. |
| model.dto.payment.PreparationDTO | Добавить preparedRequestId и List<ConfirmationValueDTO> confirmationSnapshot. Снимок содержит бизнес-поля и preparationNo. Контекст — отдельная глубокая копия; confirmedRequestId/confirmedAt остаются признаками фактического согласия. |
| model.dto.payment.PreparationViewDTO | Добавить List<ConfirmationParameterDTO> confirmation — готовые key/value/description для wire. Значения берутся из сохранённого снимка, описания — из схемы; внутренний UUID подготовки наружу не требуется. |
| model.dto.session.SessionViewOutDTO | Заменить availableCommands на availableOperations — список OperationSpecDTO для саджестов. Подтверждение/отказ отображаются по propose, поэтому второй список команд не нужен. Удалить устаревшие комментарии про GET. |

ConfirmationParameterDTO(key, value, description) — отдельный DTO представления. Renderer соединяет сохранённые key/value с описаниями схемы и общим описанием preparationNo. Набор ожидаемых значений не пересчитывается.

preparedRequestId фиксирует сообщение создания именно этой подготовки. SeparateConfirmationGuard сравнивает согласие с ним, а не с изменяемым lastRequestId: промежуточный отклонённый ход не должен разрешить повтор Request-Id подготовки.

### Контракт перехода без зависимости db → service

В agent-model добавить минимальные библиотечно независимые интерфейсы model.fsm:

- TransitionPlan.check(SessionSnapshotDTO, SessionTurnInDTO) → TransitionCheckDTO.
- TransitionAction.execute(SessionSnapshotDTO, SessionTurnInDTO, OperationSpecDTO) → void.

TransitionCheckDTO содержит либо rejectionCode, либо конкретный TransitionAction и OperationSpecDTO (null для общего CANCEL). Успешный результат обязательно содержит action. Это временная привязка исполнения, **она не сериализуется**.

В FlowTransitionDTO сохранить source/event/target; guard/action заменить на TransitionPlan plan. FlowDefinition.transitions() и FsmEngine.process()/find()/ensureSemaphore() сохраняют публичные сигнатуры. В FlowDefinition добавить Optional<OperationSpecDTO> operationSpec(Operation) для проверки совместимости хранимой подготовки. agent-db знает интерфейсы и результат проверки, но не реализации actions/guards и не каталог операций.

В service.fsm.guard определить TransitionGuard.check(snapshot, input, spec) → Optional<ResultCode>. spec может отсутствовать у общего CANCEL и при отсутствии подготовки; первый guard обязан отклонить неподходящий вход до проверки её полей. GuardChain — final-утилита с закрытым конструктором; по уточнению пользователя не регистрируется в Spring и не внедряется. Статический GuardChain.check(orderedGuards, snapshot, input, spec) последовательно возвращает первый отказ; не вызывает actions и не хранит состояние запроса.

## 4. Каталог и регистрация

| Класс / пакет agent-service | Ответственность |
|---|---|
| fsm.definition.OperationDefinition | Неизменяемая регистрация: OperationSpecDTO, prepareAction, executeAction и упорядоченные prepareGuards/executeGuards. Не пересоздаёт OperationSpecDTO: тот сам обеспечивает неизменяемость схемы. Защитные копии только списков guards. |
| port.in.OperationCatalog | Только чтение для адаптера/представления: Optional<OperationSpecDTO> resolve(String actionCode), Optional<OperationSpecDTO> operationSpec(Operation), List<OperationSpecDTO> choices(SessionState). Не раскрывает actions/guards. |
| fsm.definition.RegisteredTransitionPlan | При создании связывает единый список общих guards с дополнительными guards операции и action; сохраняет готовые неизменяемые цепочки. Источник операции — INPUT/PREPARATION/NONE. Реализует TransitionPlan. |
| fsm.SessionFlowDefinition | Реализует FlowDefinition и OperationCatalog: декларация source/event/target и порядка компонентов, хранит неизменяемый список OperationDefinition, предоставляет resolve()/operationSpec() напрямую, choices(state) из зарегистрированных SELECT-привязок. Удалить private prepare/cancel/confirm и бизнес-лямбды. |

RegisteredTransitionPlan получает список определений напрямую из SessionFlowDefinition и принимает один список guards. В конструкторе для каждой операции он дополняется её проверками подготовки/исполнения; runtime не собирает списки повторно. check() технически выбирает готовую привязку и один раз вызывает статический GuardChain.check(). Если подготовка отсутствует, используется общий список с null spec: первый ActivePreparationGuard возвращает отказ без разыменования подготовки. Техническое чтение кода/выбор цепочки не определяет допустимость сообщения. Для NONE используется явно заданный общий action. Отсутствующая регистрация сохранённой операции выявляется технической проверкой снимка до события, fallback на STATUS запрещён.

SessionFlowDefinition проверяет только уникальность Operation/actionCode и единственность перехода для source/event. Отдельный OperationRegistry удалён по согласованию пользователя: проверки бизнес-полей, source/valueType и заполненности текстов из него не переносятся в другой валидатор. Списки задают порядок; @Order и порядок сканирования beans его не определяют. Не создаём конкурирующих CONFIRM-переходов по операциям.

Регистрация STATUS: actionCode=status; схема [paymentNumber ← PAYMENT_NUMBER, STRING, «Номер платежа»]; PrepareOperationAction и ExecutePreparedOperationAction; дополнительные guards пока пусты. Поддержанные источники покрывают нынешние шесть полей платежа, без reflection и без источников будущих ещё не реализованных изменений. Новый набор из этих полей задаётся регистрацией; новый алгоритм — новым action/guard.

Каталог один: адаптер разрешает action_code через OperationCatalog; renderer вызывает choices(state); fixture вызывает choices(CHOOSING_REQUEST_TYPE). SessionFlowDefinition хранит определения, связывает их с переходами и реализует читаемый каталог; отдельной прослойки хранения нет. Саджест не гарантирует допуск: окончательное решение остаётся guards SSM. Ни mapper, ни fixture не содержат собственного списка status.

## 5. Guards, actions и переходы

Все следующие guards находятся в service.fsm.guard.common. Бизнес-условия полностью реализованы в check(), включая разбор и сравнение. Нет делегирования этих алгоритмов прежнему ContextBinding или новому «сервису проверок».

| Guard | Проверка |
|---|---|
| InitialContextGuard | Если сессия ещё не инициализирована, требует полный PaymentContextInDTO по нынешнему initialContext(). Для существующей сессии пропускает к ContextConsistencyGuard. Ничего не копирует в snapshot. |
| ExistingSessionGuard | CANCEL допустим только для уже инициализированной бизнес-сессии; наличие строки семафора недостаточно. |
| ActivePreparationGuard | Есть активная неподтверждённая подготовка; согласие ещё не зафиксировано. |
| ContextConsistencyGuard | Каждое явно переданное поле metadata совпадает с серверным контекстом; пропуск допустим для существующей сессии. Сумма сравнивается численно. Для первого SELECT проверку полноты уже сделал InitialContextGuard. |
| PreparationNumberGuard | Ровно один preparationNo; непустая строка десятичных цифр, положительный long без переполнения; число равно preparation.preparationNo. Не использует projectionVersion/preparationId. |
| SeparateConfirmationGuard | Request-Id согласия отличается от preparedRequestId; новый lastRequestId не ослабляет проверку. |
| ConfirmationDataGuard | После проверки preparationNo требует точный бизнес-набор по схеме: без null-элементов, пропусков, дублей, пустых и лишних ключей; сравнивает с confirmationSnapshot, а не с текущим metadata. |

STRING сравнивается точно, без trim/числового преобразования: 0042 ≠ 42. DATE — строгая ISO_LOCAL_DATE; DECIMAL — BigDecimal.compareTo, без double. Порядок входных пар и description не важны. PreparationNumberGuard не удаляет элементы из входа; ConfirmationDataGuard исключает общий ключ логически. Все null/дубли должны оставаться наблюдаемыми до проверки.

| source / event → target | Явная цепочка → action |
|---|---|
| CHOOSING_REQUEST_TYPE / SELECT → AWAITING_CONFIRM | InitialContextGuard → ContextConsistencyGuard → guards подготовки выбранной операции → prepareAction |
| CHOOSING_REQUEST_TYPE / CANCEL → CHOOSING_REQUEST_TYPE | ExistingSessionGuard → ContextConsistencyGuard → ReportNoPreparationAction |
| AWAITING_CONFIRM / CANCEL → CHOOSING_REQUEST_TYPE | ExistingSessionGuard → ContextConsistencyGuard → CancelPreparationAction |
| AWAITING_CONFIRM / CONFIRM → COMPLETED | ActivePreparationGuard → ContextConsistencyGuard → PreparationNumberGuard → SeparateConfirmationGuard → ConfirmationDataGuard → дополнительные guards операции → executeAction |

По уточнению пользователя для CONFIRM передаётся один список: active, context, number, separate, data; дополнительные guards операции добавляются при создании привязки. ActivePreparationGuard первым проверяет наличие подготовки, прежде чем последующие guards используют её данные. Утилита выполняет всю готовую цепочку единственным вызовом.

Actions находятся отдельно, в service.fsm.action:

- **PrepareOperationAction.execute()**: создаёт доверенную копию контекста первого сообщения либо копию ранее сохранённого; увеличивает lastPreparationNo через Math.incrementExact; генерирует внутренний preparationId; фиксирует preparedRequestId; по схеме извлекает/форматирует бизнес-значения и добавляет строковый preparationNo; сохраняет подготовку и CONFIRMATION_REQUIRED. Переполнение — технический отказ с rollback, без переиспользования номера.
- **ExecutePreparedOperationAction.execute()**: фиксирует confirmedRequestId/confirmedAt, строит OperationContext из сохранённой подготовки, один раз вызывает WorkflowManager.callOperation(preparation.operation, context), устанавливает REQUEST_SUBMITTED. Реквизиты входного согласия в OperationContext не копируются.
- **CancelPreparationAction.execute()**: очищает preparation, устанавливает PREPARATION_CANCELLED; lastPreparationNo не сбрасывает.
- **ReportNoPreparationAction.execute()**: устанавливает NO_ACTIVE_PREPARATION для существующей сессии без подготовки.

PreparationMapper оставляет глубокое копирование и структурный toOperation(); удаляется constant STATUS и алгоритм сборки подготовки. Копирование PaymentContextInDTO в доверенный DTO переносится из db.session.PaymentContextMapper в service.fsm.PreparationMapper. Часы/UUID и WorkflowManager — допустимые технические зависимости actions. Пустые пакеты guard.status/guard.recall и actions несуществующих операций не создаются.

## 6. Исполнение, ошибки и persistence

### NativeMachineStore

1. Проверить formatId технической строки до любого восстановления и до первой записи; прежняя ветка проверки только при наличии native-записи оставляла окно для записи нового формата под старой меткой.
2. Для новой сессии создать только временный каркас sessionId/state, с нулевыми счётчиками и без бизнес-контекста. Входные данные проверяют guards, заполняет action. Для существующей — один restore и техническая проверка снимка.
3. event() явно сопоставляет SELECT_OPERATION → SELECT, ACCEPT_PROPOSAL → CONFIRM, REJECT_PROPOSAL → CANCEL. Текст/action_code не переопределяет CONFIRM.
4. SSM guard вызывает transition.plan.check(), сохраняет результат в ExecutionContext текущего хода и возвращает true только при допуске. SSM transition action берёт **эту же** успешную привязку и непосредственно вызывает её TransitionAction.execute(). Повторного разрешения операции и повторного прогона guards нет.
5. После ожидания complete() проверить перехваченное исключение, состояние ошибки машины, guard rejection и факт завершения action. ACCEPTED само по себе не считается выполнением. Технические ошибки имеют приоритет над штатным отказом.
6. При ожидаемом отказе guard бросить существующий SessionPersistenceException с его ResultCode и restoredTerminal на границе process(). Guard возвращает причину, а не исключение. Это использует существующий путь failure оркестратора, не рендерит неполный snapshot и не сохраняет отклонённый ход.
7. При успешном action обновить state/lastRequestId/projectionVersion, выполнить putScenario()/native persist. Без успешно инициализированного контекста сохранение запрещено. В finally остановить тот же экземпляр до завершения транзакции.

ExecutionContext хранит результат проверки, признак actionCompleted и первую техническую ошибку только на время хода. Singleton-компоненты состояния запроса не содержат. Action не вызывается из GuardChain.

Если перехода для события нет: текущая существующая сессия получает INVALID_COMMAND либо SESSION_COMPLETED по прежнему пути результата без выполнения action; новая сессия — INVALID_COMMAND без сохранения каркаса. Условие первого SELECT_STATUS из store удаляется. На отсутствующем переходе ContextConsistencyGuard не выполняется: приоритет имеет недоступность события, а не несовпадение metadata. Для COMPLETED сохраняются HTTP 400/final_message=true; соответствующий E2E-тест проверяет эту гарантию, не прежнее место проверки контекста.

### Типизированные причины

| Случай | ResultCode / HTTP |
|---|---|
| Неполный первый контекст | INVALID_REQUEST / 400 |
| Подмена metadata | CONTEXT_MISMATCH / 400 |
| Нет активной подготовки, повтор Request-Id подготовки, CANCEL первой сессии | INVALID_COMMAND / 400 |
| Нет/дубли/null/формат preparationNo или неправильная форма бизнес-набора | Новый INVALID_CONFIRMATION / 400 |
| Корректный номер относится к другой подготовке | Новый STALE_PREPARATION / 400 |
| Корректно сформированные бизнес-значения отличаются от сохранённых | Новый CONFIRMATION_MISMATCH / 400 |
| Исключение guard/action | OPERATION_FAILED / 500, rollback |
| Повреждение/несовместимость хранения | STORAGE_UNAVAILABLE / 500 |
| Сбой commit/остановки | Существующий технический путь 500; локальный rollback |

Новые причины добавляются в ResultCode, AclInputAdapter.status() и error.* каталога. TurnOutcome расширять для отказов guard не нужно: они не сохраняются. failure не отдаёт новые suggestions/state; final_message соответствует известной завершённости, без дополнительного чтения. Отказ guard не меняет даже projectionVersion/lastRequestId/native bytes. Техническая строка семафора после неудачного первого сообщения может остаться.

Границы SsmFsmEngine сохраняются: REQUIRES_NEW для семафора, NOWAIT и одна рабочая транзакция, общий commit/rollback. Успешный внешний эффект при последующем сбое локального commit не объявляется отменённым; повторов не добавляется.

### Хранимый формат

- SessionSemaphore.FORMAT_ID изменить на **status-v3**. Добавленные поля PreparationDTO и confirmationSnapshot несовместимы со status-v2. Не требуется новая таблица или Liquibase-миграция.
- Существующие Kryo ID не перенумеровывать; ConfirmationValueDTO зарегистрировать как 110. В снимке использовать обычный ArrayList, не коллекции конфигурации/beans. Реальный round-trip проверяется тестом persister и перезапуском JAR.
- Старую сессию status-v2 отклонять до десериализации, в том числе старую строку семафора без native-записи при попытке process. Не обновлять метку на месте, не удалять данные и не восстанавливать пустую сессию поверх старой. Для локального чата — новый диалог с новым UUID.
- Из ContextBinding убрать initialContext()/verify(input). Оставшуюся проверку хранения переименовать в StoredSnapshotValidator: типы, обязательный сохранённый контекст, идентичность sessionId, счётчики, структура подготовки/согласия и соответствие двух сохранённых копий контекста. Это проверка хранения, не входного ACL.
- В snapshot() заменить operation == STATUS на наличие зарегистрированной операции; через FlowDefinition предоставить библиотечно независимое описание схемы для технической проверки сохранённых ключей/типов. Проверить preparedRequestId, точный состав confirmationSnapshot, номер в нём и равенство lastPreparationNo. Не сравнивать с новым входом. Несовместимая схема — STORAGE_UNAVAILABLE.
- Конфигурация, TransitionCheckDTO, action/guard и готовые тексты не попадают в ExtendedState. При будущей несовместимой правке схемы обновляется единый formatId; второй механизм версий не вводится.

## 7. ACL, представление, fixture и UI

**AclInputAdapter / AclMapper.** Адаптер получает OperationCatalog. Для request разрешает action_code, передаёт найденную operation в mapper; неизвестный код даёт прежний UNSUPPORTED_COMMAND. Mapper.command() сопоставляет performative с обобщённой командой без знания STATUS. accept_propose всегда означает CONFIRM активной подготовки; content.action_code при нём не выбирает операцию.

Mapper переносит только пары state с type=CONFIRMATION в List<ConfirmationValueDTO>. Валидные MEMORY/REDIRECTION/OPERATION не участвуют в согласии. Null-элемент state, отсутствующий/неизвестный type — ошибка формы INVALID_REQUEST; отсутствие массива или null/пустое значение CONFIRMATION-параметра не превращается в корректное согласие: передаётся отсутствие/дефект значения для guard. Не применять Map/Set, distinct или фильтрацию пустых значений. Проверки конверта, UUID, дат/сумм metadata остаются транспортными.

**SessionResponseRenderer / SessionResponseMapper.** Строить PreparationViewDTO.confirmation из сохранённых значений и описаний схемы. Для сводки использовать ключ шаблона операции и бизнес-значения в порядке схемы; preparationNo не включать в пользовательский текст. STATUS: «Запросить статус платежа №42?». Не извлекать обязательные шесть реквизитов из view.context для CONFIRMATION_REQUIRED.

Каталог session-responses.properties сохранить, но заменить фиксированную PaymentArgument-схему для предложения схемой операции: шаблон операции принимает строковые аргументы её бизнес-полей. Остальные исходы — общие тексты без реквизитов; REQUEST_SUBMITTED может использовать название операции из каталога. При старте проверять наличие шаблонов и допустимые индексы отдельно для каждого набора. Не сохранять текст/ключ шаблона в FSM. Текущую настройку формата даты использовать только для отображения DATE из сохранённого значения.

AclMapper.confirmation() только преобразует view.preparation.confirmation в AclState(type=CONFIRMATION); сам не добавляет preparationNo и не знает список полей. suggestions() преобразует готовые availableOperations; initialSuggestions() со встроенным status удалить/заменить структурным toSuggestions(). LocalHelperController получает начальные варианты из того же каталога; FixtureMapper продолжает преобразование двух generated-типов AclSuggestion.

**Контракт и UI.** В acl-poc.yaml уточнить descriptions/examples state: STATUS возвращает paymentNumber и preparationNo, оба обязательны для согласия. Generated-классы руками не менять. Внешний docs/api/rest_api_acl_gigaasistant.02.013.02.yml и структура fixture API сохраняются.

TestSession.send() уже передаёт state без разбора схемы, в том числе preparationNo. Производственный JS менять только при найденном дефекте доставки; добавить проверку точного эха двух полей и замены state после нового предложения. Отклонение без state принимается сервером; текущая отправка state при reject_propose также допустима. Серверная ошибка по действующим правилам убирает кнопки, но не отменяет подготовку; новый UX повтора в task01 не вводится.

## 8. Проверки и порядок выполнения

Полный перечень работ и AC — в [tasks.md](tasks.md). TDD применять к новому поведению; проверки распределить по слоям без дублирования всей матрицы на каждом.

| Уровень | Минимальная необходимая проверка |
|---|---|
| Service | Порядок guards, остановка на первом отказе, отсутствие общего изменяемого состояния; STATUS-схема и альтернативный набор из нынешних шести полей на том же prepare/confirm механизме; разбор STRING/DATE/DECIMAL; однозначность регистрации переходов. |
| ACL boundary | Явное сопоставление трёх сообщений, разрешение action_code через каталог, сохранение дублей/null, изоляция других типов state и структурный outbound mapping. |
| Реальный HTTP / SSM | Параметризованный тест основного ACL и зеркала: два поля → корректное согласие → один внешний вызов; отсутствующий/дублированный/неверный номер, подмена paymentNumber, лишний ключ → 400 и ноль вызовов. Две неподтверждённые подготовки одного платежа после CANCEL: старый номер не исполняет новую. |
| Persistence | Отказ guard не пишет native; невалидный первый ход не создаёт бизнес-сессию; успех сохраняет expected snapshot/consent; rollback, NOWAIT, stop/commit; format-v2 отказ до restore; round-trip status-v3. |
| Renderer/UI | Сводка и state одной подготовки; общий источник suggestions; точное эхо state, только актуальные кнопки, без GET/повторов. |
| Перезапуск | PocJarSmokeIT восстанавливает status-v3, тот же preparationNo и ожидаемые значения; подтверждение возвращает state, полученный до остановки JVM. |

Адаптировать существующие PocHttpClient и helper-методы тестов: корректное согласие явно получает state из соответствующего proposal. Не подставлять «верные» данные скрыто в универсальный post() и не читать актуальную БД перед каждым согласием — это скроет тесты устаревшего/испорченного подтверждения. В persistence-тестах сохранять полученную подготовку в локальной переменной; в конкурентном тесте не делать find() под удерживаемой блокировкой.

Затронутые существующие наборы: AclBoundaryTest, SessionResponseRendererTest, LocalHelperBoundaryTest, PocHttpIntegrationTest, SessionPersistenceTest и наследующий его PostgresSessionPersistenceIT, PocHttpRollbackTest, PocEndToEndIT, PocCorrTimeoutIT, PocJarSmokeIT, session.test.mjs. Чисто транспортные тесты corr менять не требуется.

После согласования: целевые RED/GREEN проверки, затем один итоговый `mvn -B -Pintegration-tests clean verify` и `node --test agent-ui/src/test/js/session.test.mjs`. Полный Maven-профиль требует отдельную тестовую PostgreSQL и POC_TEST_POSTGRES_URL/USER/PASSWORD; отсутствие окружения фиксируется как незавершённая проверка, не skip. Обычный verify включает JAR smoke, но не заменяет профиль всех IT. Затем краткая ручная проверка чата и отдельный валидатор-архитектор по AGENTS.md. Логи остаются в target.
