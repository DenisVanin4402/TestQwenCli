# task02. Дизайн реализации

**Статус:** согласован пользователем 05.10.2026 вместе с [tasks.md](tasks.md), реализация начата. Обязательное уточнение: файлы actions и guards с выделенной прикладной логикой сохраняются; перенос их алгоритмов в конфигурацию или исполнитель запрещён.

## 1. Прямой путь исполнения

SessionTurnOrchestrator → конкретный SessionExecutionService → штатная StateMachine → Guard/Action → штатный persister → commit → SessionResponseRenderer → TurnOutDTO → ACL.

| Участок | Конкретное изменение |
|---|---|
| agent-db/api | Удалить FsmEngine и FlowDefinition. SessionPersistenceException перенести в service.fsm: это ошибка исполнения с ResultCode/terminal, а не порт заменяемого движка. |
| agent-service/fsm/SessionExecutionService | Объединить транзакционные обязанности SsmFsmEngine и lifecycle NativeMachineStore; удалить прежние классы. Сохранить ensureSemaphore(UUID), process(SessionTurnInDTO), find(UUID). process/find вызываются через Spring proxy; создание семафора остаётся отдельной транзакцией SessionSemaphore. |
| agent-service/fsm/SessionStateMachineConfiguration | Заменить SessionFlowDefinition прямым StateMachineBuilder и четырьмя штатными переходами. Собрать здесь регистрацию STATUS из OperationConfiguration и удалить отдельную конфигурацию операций. Сохранить OperationCatalog как прикладной контракт чтения метаданных, без SSM в возвращаемых данных. |
| agent-service/fsm/definition | Удалить RegisteredTransitionPlan, OperationSource, ActionBinding. OperationDefinition оставить единственной связью OperationSpecDTO, штатных actions и списков дополнительных штатных guards; она не описывает source/target и не возвращает план допуска. |
| agent-model | Удалить FlowTransitionDTO, TransitionCheckDTO, TransitionPlan, TransitionAction. Сохранённые предметные DTO остаются без изменений. |
| agent-db | Оставить SessionRepository, SessionSemaphore, NativePersistenceConfiguration и библиотечное JPA-хранилище. StoredSnapshotValidator перенести в service.fsm, заменить зависимость FlowDefinition на OperationCatalog. Бизнес-алгоритмы не получают SessionEntity. |
| Maven | Явно подключить используемые SSM core/data-jpa зависимости в agent-service с существующей версией из parent. Новых модулей, версий библиотеки и зависимостей db → service нет. |

Исполнение содержит только lock/restore/send/await/save/stop и техническую обработку ошибок. Оно не выбирает target по if/switch и не содержит проверок полей платежа. Конфигурация содержит связи; guards/actions — сами алгоритмы.

## 2. Guards/actions без своего протокола

- Классы в guard.common реализуют `Guard<SessionState, SessionEvent>.evaluate(StateContext)`; классы action — `Action<SessionState, SessionEvent>.execute(StateContext)`. Удалить TransitionGuard и отдельный GuardChain.
- Одна короткая функция композиции в конфигурации последовательно вызывает штатные guards. После false следующие проверки и action не вызываются. Нет DTO с callback, map планов, reflection или поиска beans во время хода.
- Один пакетный SessionExecutionContext на обрабатываемое событие: input, рабочий snapshot, выбранное определение операции, rejectionCode, первая ошибка, признак завершённого action. Контекст передаётся через message header, не сохраняется в ExtendedState и не живёт в singleton-полях. Это данные вызова, без методов второго движка.
- Для SELECT операция берётся из входа; для CONFIRM — из сохранённой подготовки. Отсутствие подготовки сначала обрабатывает ActivePreparationGuard. Выбор обработчиков не выбирает состояние и не заменяет проверки.
- Стандартный callback выполняет конкретный action операции, отмечает успешное завершение и сохраняет ошибку перед её повторным выбросом. Общие cancel/noPreparation actions привязываются напрямую с тем же контролем ошибок. Перехваченная SSM ошибка должна доходить до транзакционной границы; одного ACCEPTED недостаточно.
- Прикладные алгоритмы существующих guards/actions переносятся без ослабления проверок. Общие проверки выполняются до дополнительных проверок операции; порядок задаётся явно в конфигурации.

| Source / event | Guards по порядку | Action / target |
|---|---|---|
| CHOOSING_REQUEST_TYPE / SELECT | InitialContext, ContextConsistency, дополнительные prepareGuards | prepareAction / AWAITING_CONFIRM |
| CHOOSING_REQUEST_TYPE / CANCEL | ExistingSession, ContextConsistency | ReportNoPreparation / CHOOSING_REQUEST_TYPE |
| AWAITING_CONFIRM / CANCEL | ExistingSession, ContextConsistency | CancelPreparation / CHOOSING_REQUEST_TYPE |
| AWAITING_CONFIRM / CONFIRM | ActivePreparation, ContextConsistency, PreparationNumber, SeparateConfirmation, ConfirmationData, дополнительные executeGuards | executeAction / COMPLETED |

## 3. Модели и настройки

SessionTurnInDTO.command заменить на event типа SessionEvent. AclMapper отображает request/accept_propose/reject_propose в SELECT/CONFIRM/CANCEL; TurnCommand и повторный перевод внутри FSM удалить. Enum SessionEvent и его native-регистрацию не менять.

TurnOutDTO содержит requestId, result, terminal, confirmation (List<ConfirmationParameterDTO>) и availableOperations (List<OperationSpecDTO>). SessionViewOutDTO и PreparationViewDTO удалить. Renderer.render(snapshot) возвращает TurnOutDTO; summary остаётся локальным значением при заполнении ResultDTO. Ошибки используют ту же форму с пустыми списками. AclMapper читает параметры и операции напрямую.

SessionQueryUseCase.find(UUID) и реализация оркестратора возвращают Optional<SessionSnapshotDTO> после завершения транзакционного чтения. Это внутренний запрос сохранённых данных для проверки persistence, не новый HTTP endpoint. Тесты проверяют предметные поля снимка; сформированный ответ проверяют через renderer/HTTP. SessionResponseMapper удалить, если после удаления двух проекций у него не осталось самостоятельной работы; PreparationMapper для структурного копирования сохранить.

PaymentContextInDTO и TrustedPaymentContextDTO остаются разными: частичный недоверенный ввод и проверенный сохранённый контекст имеют разные обязанности. ConfirmationValueDTO сохраняет пары и дубли входа; ConfirmationParameterDTO добавляет подпись для ответа. OperationSpecDTO содержит только метаданные/схему, OperationDefinition — исполняемые компоненты. Их объединение протащило бы SSM в API или изменило persisted-формат.

OperationConfiguration удаляется за счёт единого места регистрации в SessionStateMachineConfiguration. Новых YAML-параметров нет. Настройки БД, corr, профилей и текстов сохраняются: удаление рабочих возможностей настройки не требуется для снятия абстракции FSM. Тексты, formatId и сериализаторы не превращаются в новую систему конфигурации.

## 4. Хранение и ошибки

- Сохранить status-v3, ExtendedState-ключи, состав PreparationDTO/TrustedPaymentContextDTO/ConfirmationValueDTO и Kryo ID. projectionVersion в persisted-снимке сохраняется для совместимости, но не копируется в удаляемые выходные проекции.
- Перед изменениями сохранить в тестовом ресурсе небольшой native status-v3 образец текущего кода; после рефакторинга восстановить его и подтвердить прежнее предложение. Это fixture совместимости, не копия Maven-отчёта. Несовместимый formatId отклоняется до restore; сброс или миграция БД не предусмотрены.
- Проверки целостности восстановленных данных сохраняются. Request input, callbacks и SessionExecutionContext никогда не попадают в сериализацию.
- Штатный guard denial возвращает прежний ResultCode через контролируемое исключение; native save не выполняется. Неинициализированная сессия не сохраняется; технический семафор может оставаться.
- Ошибки callbacks дают OPERATION_FAILED и rollback. Сбои хранения/commit дают STORAGE_UNAVAILABLE; busy — SESSION_BUSY. Ошибка stop вызывает rollback либо добавляется как suppressed к основной ошибке. terminal при контролируемом отказе восстановленной COMPLETED сохраняется.
- Жизненный цикл: под блокировкой один экземпляр, restore/start → sendEvent с ожиданием complete → проверка ошибки и завершения action → persist → stop в finally → commit. Никаких scheduler, doAction, кешей или повторов.
- Renderer вызывается после успешного выхода из транзакционного proxy. Внешний corr мог выполниться до rollback; текст ошибки и количество вызовов сохраняют текущий контракт.

## 5. Проверка и документы

Адаптировать существующие OperationFlowTest, SessionPersistenceTest, HTTP/rollback/PostgreSQL/JAR и renderer/boundary тесты. RegisteredTransitionPlanTest заменить проверкой реальной SSM: порядок guards, остановка после отказа, один action и независимость параллельных сессий. Не сохранять тесты удаляемой реализации ради числа тестов.

До переноса добавить только недостающие регрессии по требованиям task01; обнаруженные дефекты исправлять через RED/GREEN. Изменение внутренних сигнатур само по себе не требует искусственного RED. Полная приёмка — [tasks.md](tasks.md).

ADR-021 снимает заменяемую границу ADR-005/008; транзакционные гарантии ADR-009 сохраняются. После согласования актуализировать подробные описания POC и ссылки task01 на заменённые механизмы. Не отмечать незакрытую task01 завершённой; перенесённые обязательства сверить явно. validation.md создавать только по фактическому результату отдельного валидатора.
