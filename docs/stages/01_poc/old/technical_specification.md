# Техническая спецификация целевого POC запроса статуса

**Редакция:** 05.10.2026. **Статус:** действующая спецификация POC с согласованными уточнениями команд и жизненного цикла FSM. Результаты проверок и их ограничения приведены в [истории этапа](history.md). Прежний протокол попыток/результатов corr заменён [действующими ADR](../../../adr/README.md).

С 05.10.2026 исходящая сводка использует propose/state CONFIRMATION, действия — suggestions; GET сессии удалён по [ADR-020](../../../adr/0020-acl-chat-actions.md).

## 1. Границы и последовательность

**Уточнение task02 (05.10.2026):** выбрано удаление заменяемой абстракции FSM по [ADR-021](../../../adr/0021-direct-spring-statemachine.md). Прежнее ограничение SSM-типов модулем agent-db снимается; конкретные перенос исполнения и сокращение моделей описаны в [дизайне](../../../tasks/task02/impl_design.md), согласованном 05.10.2026. Транзакции, native-совместимость и внешний ACL сохраняются.

Только STATUS через усечённый ACL 1.6, native SSM/JPA, PostgreSQL и локальную H2, синхронный WorkflowManager и ответ оркестратора. [Требования](requirements.md) задают бизнес-поведение. [План](architecture_refactoring_plan.md) задаёт шаги, TO BE и пять sequence; этот документ уточняет контракты, не дублирует архитектуру вторым механизмом.

Путь: controller → ACL adapter → SessionTurnOrchestrator → транзакционная инфраструктура FSM → предметный результат → локальный шаблон в оркестраторе → adapter → HTTP. MapStruct выполняет структурный mapping на внешних границах и именованное создание предметных снимков/проекций. SSM-типы используются в service.fsm и инфраструктуре agent-db; generated DTO не входят в agent-service/model/db.

## 2. Входные API и исходный контекст

| Метод | Назначение и источник |
|---|---|
| POST /api/v1/ai/agents/{agent_code} | agent-api-in; производная openapi/acl-poc.yaml, tag Acl. |
| GET /local-api/v1/fixtures | agent-ui-back; синтетический каталог для сборки ACL metadata, не создаёт FSM. |
| POST /prototype/v1/status-requests | Временный corr-стенд, generated API из agent-api-out/openapi/invest-corr-prototype.yaml; не банковский адрес. |

Полные пути источников: agent-api-in/src/main/resources/openapi/acl-poc.yaml, agent-ui-back/src/main/resources/openapi/poc-local-api.yaml, agent-api-out/src/main/resources/openapi/invest-corr-prototype.yaml. API/DTO генерируются Maven в target/generated-sources/openapi стандартным OpenAPI Generator без своих Mustache-шаблонов; описания остаются в summary/description YAML. Ручные копии и редактирование target запрещены. InvestCorrHttpApi связывает сгенерированный интерфейс corr с @FeignClient без дублирования HTTP-методов. [Исходная схема GA](../../../api/rest_api_acl_gigaasistant.02.013.02.yml) сохраняется отдельно.

Обязательны JSON-тело, UUID Request-Id и Gigachat-Session-Id, message.version=1.6, performative, sender, receiver, UUID conversation_id и reply_with. Request-Id совпадает с reply_with. Receiver соответствует agent_code и настроенному агенту. Gigachat-Session-Id определяет сессию; conversation_id используется только для корреляции.

Первому request/status нужны девять значений:

| ACL | Внутренние данные |
|---|---|
| metadata.organization.epk_id | epkId |
| metadata.customer_info.digital_user_id | digitalUserId |
| metadata.additional_info: массив key/value | paymentId, paymentNumber, paymentDate, amount, currency, recipientName, organizationName |

Значения additional_info строковые. Дубликаты распознаваемых ключей, пустые обязательные значения и неразбираемая дата/сумма отклоняются без выбора первого/последнего значения. Для синтетического стенда используются ISO-дата, десятичная строка суммы и валюта, например 2026-10-03, 12500.00, RUB. Реальные форматы источника — зависимость GA Q-09.

Адаптер проверяет форму. Под семафором внутренняя логика дополняет пропуски последующих входов сохранёнными значениями и проверяет явную подмену. FixtureId, HTTP и metadata как wire-структура не передаются FSM. Локальные metadata не доказывают банковскую авторизацию.

## 3. Команды и ответ

| ACL-вход | Внутреннее намерение |
|---|---|
| request/status | SELECT с operation=STATUS |
| accept_propose | CONFIRM |
| reject_propose | CANCEL |

Адаптер не читает FSM. SSM guards трактуют допустимость намерения по состоянию; внешней transition policy нет. Для accept/reject нижние поля текста/action_code игнорируются, content может быть пустым. Неизвестные JSON-поля игнорируются, неподдерживаемые performative/action codes отклоняются. Proxy, request/COMMON, свободный текст и входные inform/failure не входят в POC. Status — временный код выбора операции. Команда restart_status удалена и отклоняется на входе.

В AWAITING_CONFIRM разрешены только CONFIRM и CANCEL. Повторный request/status возвращает INVALID_COMMAND без изменения подготовки и вызова менеджера. Отказ возвращает CHOOSING_REQUEST_TYPE; следующий выбор создаёт новую подготовку с новым preparationNo. Accept возвращает state предложения: guards сравнивают preparationNo и точный состав/значения confirmationSnapshot по схеме операции. Request-Id согласия должен отличаться от preparedRequestId. Старое согласие на прежнюю подготовку отклоняется, даже если paymentNumber совпадает; описание и порядок полей не влияют на сравнение. UI ведёт последовательный чат и разрешает следующее действие после ответа агента.

До сохранённой FSM допустим только первый корректный request/status с полным контекстом. Согласие без подготовки не создаёт операцию. Если другой запрос уже создал машину, текущий вход обрабатывается по обычным guards под тем же семафором, не получает чужой результат как replay.

Ответ формируется после транзакции. Оркестратор получает предметный код/данные, выбирает локальный шаблон и подставляет снимок текущей подготовки. Action не знает текста, templateId, performative или final_message. Не выполняется повторное чтение изменяемого черновика или второй переход для сохранения текста.

Адаптер переставляет sender/receiver, сохраняет conversation_id, устанавливает in_reply_to из Request-Id. При несовпадении reply_with используется ID заголовка и нет бизнес-перехода. Если корректную корреляцию построить нельзя, возвращается транспортная ошибка без выдуманного ID.

| Исход | Локальный HTTP/ACL |
|---|---|
| Подготовка | 200, propose, content.result, state CONFIRMATION, final_message=false. |
| Отказ | 200, inform, content.result, suggestions для нового status, final_message=false. |
| Action и commit успешны | 200, content.status_code="200", inform, content.result, final_message=true. |
| Невалидный вход/контекст, недопустимая команда, busy или завершённая сессия | Failure с соответствующим кодом текущего профиля; новый Action не выполняется. |
| Ошибка Action/хранения | Failure после завершения неуспешной транзакции, без успешного результата; сообщение не отрицает возможного внешнего эффекта. |

В текущем локальном профиле HTTP-ошибки входа/busy/terminal — 400, ошибки исполнения/БД — 500; content.status_code является строкой HTTP-кода. Полная детализация реальных ошибок GA — отдельная интеграция по [реестру](../../../api/error_codes.md). Успешные поля и final_message уже обязательны по [ACL 1.6](../../../api/api_contract_details_1.6.md). Для известной завершённой сессии final_message отражает её терминальность и в ответе об ошибке; ошибка текущего Action не создаёт терминальность.

## 4. Сохранение и транзакции

1. Обеспечить SessionEntity-семафор по sessionId отдельной короткой транзакцией/autocommit, завершить commit.
2. Начать рабочую JPA-транзакцию, захватить семафор FOR UPDATE NOWAIT. Занятость возвращается без очереди/retry; конкурентная первая вставка может ждать уникальный индекс.
3. Проверить наличие native FSM. Для существующей проверить один formatId до десериализации и восстановить; отсутствующую создать для допустимого первого входа. Повреждённая/несовместимая FSM не пересоздаётся.
4. На одном экземпляре выполнить событие через SSM guards и synchronous transition action, без scheduler hopping/doAction.
5. Дождаться `StateMachineEventResult.complete()`. Если SSM захватила ошибку Action, передать её к транзакционной границе. Сохранить тот же экземпляр native persister и прикладные данные; это ещё не commit.
6. В `finally` выполнить `stopReactively().block()`, не переиспользовать экземпляр. Ошибка остановки вызывает rollback либо добавляется к уже возникшей основной ошибке. Машина создаётся напрямую, без acquire/release через StateMachineService.
7. После выхода из транзакционного метода Spring выполняет commit/rollback. Только успешный commit позволяет вернуть предметный результат оркестратору для подготовки ответа. Строка семафора остаётся при любом исходе рабочей транзакции. При потере связи во время commit его результат может быть неизвестен; ошибка не доказывает отсутствие внешнего эффекта.

Отдельный ensure-семафор переживает rollback первой инициализации. Следующий допустимый запрос создаёт отсутствующую FSM обычным путём. Все мутации используют семафор до чтения; owner/token/expectedRevision раздельного load/apply не нужны.

Native ExtendedState содержит исходные данные, подготовку/согласие и последний предметный результат. Готовая сводка и wire-ответ не сохраняются. Native ID — sessionId.toString(); SessionEntity содержит formatId. Перенос старых снимков не нужен; применённая история Liquibase не переписывается.

## 5. Внутреннее чтение и идентичности

Внутреннее чтение берёт FOR SHARE NOWAIT, получает SessionSnapshotDTO существующей FSM и завершает транзакцию без событий/save/corr. При занятом писателе — busy; отсутствующие семафор/FSM не создаются. Оно возвращает предметные данные без повторного построения ответа; HTTP GET сессии не публикуется по ADR-020.

Ответ на событие — один TurnOutDTO: requestId, result, terminal, confirmation и availableOperations. Вложенные SessionViewOutDTO/PreparationViewDTO удалены по task02. ProjectionVersion остаётся только в native-снимке для совместимости status-v3; браузер его не использует. Номер согласия — preparationNo, а не версия представления. Список кнопок — представление; окончательный guard остаётся в SSM.

Идентичности: sessionId/requestId — вход и будущая библиотека; preparationId — внутренний идентификатор подготовки, передаваемый менеджеру как operationId логической внешней операции. Подтверждение хранит requestId и время согласия; preparationId во входном сообщении не требуется. Универсальный commandId, resultCommandId, отдельный submissionId и дублирующий ключ вместо operationId не нужны.

Входной adapter передаёт handle отдельный ключ sessionId:requestId для маркера Idempotent. Внутренние initialize/apply не аннотируются; своего InputRequest, replay/hash и журнала команд нет. Библиотека отсутствует, поэтому сохранённый HTTP-повтор и конфликт payload сейчас не гарантированы.

## 6. WorkflowManager

Контракт: void callOperation(Operation operation, OperationContext context). Контекст: UUID operationId; String epkId/digitalUserId/paymentId; необязательный PaymentDetailsChangesDTO changes. Для STATUS changes отсутствует. Состав будущих изменений — ADR-006, реализация recall/details сейчас не создаётся.

Временный InvestCorrAdapter можно переработать под этот порт: MapStruct → generated DTO → один Feign-вызов submitStatus. Для существующего стендового контракта operationId передаётся как clientRequestId и Idempotency-Key; это два требуемых wire-поля одного значения, не разные внутренние идентичности. Остальные поля — operation=status, epkId, digitalUserId, paymentId. Реальная схема corr требует отдельной сверки.

Транспортный ответ остаётся внутри адаптера: не возвращает в FSM outcome DTO или событие результата. Ошибки, определённые используемым контрактом, не выдаются за успешное исполнение; исключение доходит до rollback без catch→UNCERTAIN. Нет отдельной бизнес-ветви повторной подготовки после corr-result. Произвольный upstream-текст не вставляется клиенту.

CorrFeignConfiguration закрепляет Retryer.NEVER_RETRY только для настроенного corr-клиента; connect/read timeout по умолчанию 2000 мс задаются в YAML. Эффективные настройки учитывают default и переопределения по integrations.invest-corr.name; неположительные тайм-ауты и настраиваемый retryer запрещены. Нет Resilience4j corr retry, fallback на stub, дополнительных таймеров/deadline. Заглушка позволяет проверить успех и ошибку одного вызова; прежние сценарии серии попыток не входят в новый POC.

Внешний вызов внутри Action до commit/ответа может выполниться даже при последующей ошибке. Нет распределённой атомарности или exactly-once. Полный WorkflowManager позднее сам реализует Outbox/УСС; агент не ждёт его результатов.

## 7. Конфигурация и проверки

Application.yaml задаёт integrations.invest-corr.name/url и стандартные транспортные настройки Feign; профиль poc-local выбирает stub=enabled и сценарий заглушки. Имя определяет ключ индивидуальных настроек spring.cloud.openfeign.client.config, поэтому при его переопределении меняется и этот ключ. При stub=disabled обязательны имя и абсолютный HTTP(S)-адрес. Одновременно активен нужный адаптер; некорректная конфигурация real не включает stub автоматически. Node-id, corr retry и таймеры удаляются вместе со старым путём.

В application.yaml также задаются порт HTTP, poc.acl.agent-code, poc.responses.catalog и poc.responses.payment-date-format. Значения по умолчанию не дублируются в Java. Профильные YAML содержат локальный адрес сервера, выбор stub и параметры подключения к БД; для H2 и PostgreSQL доступны переменные окружения URL/логина/пароля. Ключи сериализации, версия ACL, formatId и бизнес-события являются контрактами и не превращаются в настройки запуска.

Liquibase: classpath:db/changelog/db.changelog-master.xml; Hibernate ddl-auto=validate; sql.init.mode=never. Профили h2/postgres/ bootstrap не смешиваются. POC-local включает fixtures и локальный адрес 127.0.0.1; синтетические данные не являются банковским окружением.

H2 использует сохраняющую данные конфигурацию для restart-проверки (POC_H2_URL); PostgreSQL получает POC_POSTGRES_URL/USER/PASSWORD. Для интеграционных тестов — отдельная БД через POC_TEST_POSTGRES_URL/USER/PASSWORD. H2 не доказывает конкуренцию PostgreSQL.

Сборка кода: mvn -pl agent-main -am verify; целевая DB-приёмка: mvn clean verify -Pintegration-tests. Запуск: java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=poc-local,h2 либо poc-local,postgres. Полный профиль включает PostgreSQL, E2E и перезапуск собранного JAR.

Проверки по ADR-017: связанный ACL-сценарий, семафор/NOWAIT, создание после сбоя, native restore/LOB, совместный rollback, один corr без retry, ответ после commit и отдельный JAR. Отдельно проверяется реальный отказ commit PostgreSQL по отложенному внешнему ключу: persist завершился, HTTP возвращает failure, сохранённый снимок не изменён, corr не повторяется. Эта проверка не моделирует потерю соединения с неизвестным результатом commit. Физическое удаление прежнего пути — обязательный критерий. Наличие тестов не подменяет результат их запуска.

## 8. Отложенные зависимости

Реальный corr, тексты, пользовательская библиотека идемпотентности и полные GA-шаблоны/ошибки проверяются на своих этапах. Старый вопрос восстановления corr-result в агенте снят; менеджер владеет внешними попытками/УСС. При подключении GA сверяется доставка state и последовательный режим чата из §3. PreparationNo защищает согласие на конкретную подготовку, но не заменяет отложенную библиотеку входной идемпотентности.
