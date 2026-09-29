# Design: P1.03 — Invest corr: порты, stub и Feign

В этом шаге реализуем слой обращения к Invest corr: проверку полномочий пользователя, получение названия организации и отправку подтверждённых запросов на статус платежа, отзыв или уточнение реквизитов. Добавим два переключаемых варианта: заглушку с заданными ответами для автономной разработки и HTTP-клиент для обращения к настроенному адресу по прототипному контракту. После реализации сервисы проекта смогут использовать единый интерфейс corr, а разработчики — проверять успешные ответы, отказы и тайм-ауты без доступа к банку. Это подготовит интеграционную основу для будущих сценариев чата; подключение настоящего банка потребует его согласованного API.

**Роль:** архитектор. **Стадия документа:** design. **Ревизия:** 2. **Дата:** 28.09.2026. **Статус:** Design согласован; implement разрешён полностью в пределах шага.

## 1. Привязка и объём

[WBS P1.03](../plan_phase1.md): R04/R05; принятые зависимости P1.01 и P1.02 r8. Источники: [ADR-001](../../adr/0001-outbound-clients-and-stubs.md), [интеграции, §§1–5](../../final/integrations.md), [модули](../../final/modules.md), [исполнение](../../final/execution.md). ADR-002/003 сохраняются; БД в этом шаге не добавляется.

Сейчас `agent-api-out` содержит только package-info и зависимости OpenFeign/GigaChat; три out-порта и corr DTO отсутствуют. В `agent-main` есть только `application-bootstrap.yaml`; `AgentApplication` сканирует свой пакет main. Принятые DTO — простые Lombok-классы без валидации и defensive copy. Это правило сохраняется.

Создать три сервисных контракта corr, общий клиент и две взаимоисключающие реализации, MapStruct, прототипную OpenAPI, управляемые fixtures и HTTP-проверки. Все операции `status/recall/details` доступны на границе corr, но workflow recall/details не запускаются. Настоящий API банка, авторизация банка, SMS, FSM, persistence, входная идемпотентность и retry-оркестрация не реализуются. Устойчивый независимый HTTP-стенд и восстановление после падения приложения относятся к P1.12; in-process stub этого шага теряет реестр при перезапуске.

## 2. Критерии приёмки

| AC | Условие | Проверяемый результат | Требование |
|---|---|---|---|
| AC-03-01 | Вызов любого из трёх портов | Доменные DTO преобразованы в отдельные wire DTO; результат типизирован; зависимости service → api-out/db у клиента не появляются | R05 |
| AC-03-02 | enabled / disabled / ошибочная конфигурация | Ровно один InvestCorrClient; enabled без сети даже при заданном URL; disabled обращается к `.url`; отсутствие/неизвестный режим и неверный URL disabled останавливают старт | R04 |
| AC-03-03 | Fixtures полномочий и организации | Все перечисленные исходы; неизвестная доверенная связка не получает разрешение; несовпадение организации не становится FOUND | R05 |
| AC-03-04 | Каждый тип submit, повтор и конфликт | Стабильные ключ, канонический payload/hash и corrRequestId; один логический приём, включая конкурентный повтор; другой payload с тем же ключом отклонён | R05 |
| AC-03-05 | Реальный Feign против локального HTTP | Проверены три маршрута, JSON, заголовки и исходы; 500/обрыв/невалидный 200 не становятся ACCEPTED/REJECTED; нет fallback и скрытых retries | R04/R05 |
| AC-03-06 | Deadline истёк до/во время вызова | До вызова нет сети/приёма; начатый submit с неизвестным исходом — UNCERTAIN; HTTP ограничен native callTimeout, кроме синхронного DNS с timeout ОС (Q-04); нет позднего эффекта stub | R04 |
| AC-03-07 | Запуск bootstrap и регрессия | Конфигурация corr действительно импортирована main; автономный запуск сохраняется; исходные контракты P1.02 не изменены | R04 |

## 3. Детальный дизайн реализации

### 3.1. Файлы и границы

Пути Java ниже относительно `src/main/java/ru/sberbank/pprb/agent/` соответствующего модуля.

| Модуль / файлы | Ответственность |
|---|---|
| agent-model: `model/dto/{PermissionQueryInDTO,PermissionResultOutDTO,OrganizationQueryInDTO,OrganizationResultOutDTO,SubmissionEnvelopeInDTO,SubmissionResultOutDTO,OrganizationDTO,PaymentChangesDTO,CallContextDTO}.java`; `model/enums/{OperationType,PermissionOutcome,OrganizationOutcome,SubmissionOutcome,CorrErrorCode}.java` | Простые DTO с Lombok getters/setters, no-args/all-args; enum без суффикса |
| agent-common: `common/utils/Deadline.java` | Техническое значение с монотонным остатком времени, фабрикой от Duration и заменяемым источником nanos для тестов; не DTO |
| agent-service: `service/port/out/{PermissionPort,OrganizationPort,CorrespondencePort}.java` | `check(query, context)`, `resolveName(query, context)`, `submit(envelope, context)`; без внешних типов |
| agent-api-out: `investcorr/service/InvestCorrAdapter.java`, `investcorr/mapper/InvestCorrMapper.java` | Реализация трёх портов; MapStruct с CommonMapperConfig; проверка ответов/доверенной привязки и mapping ошибок |
| agent-api-out: `investcorr/client/{InvestCorrClient,StubInvestCorrClient,FeignInvestCorrClient,InvestCorrFeignApi}.java` | Единый контракт wire-клиента и один вызов транспорта; transport-интерфейс Feign описан вручную |
| agent-api-out: `investcorr/config/{InvestCorrProperties,InvestCorrConfiguration,InvestCorrFeignConfiguration}.java` | Строгая конфигурация; условная регистрация; ограниченное включение Feign только в disabled |
| agent-api-out: `investcorr/exception/InvestCorrCallException.java`, `investcorr/utils/{SubmissionCanonicalizer,CorrStubRegistry,DeadlineFeignClient}.java` | Типизированные транспортные ошибки; canonical JSON; атомарный in-memory реестр; транспортный timeout с исключением DNS по Q-04 |
| agent-api-out: `src/main/openapi/invest-corr-prototype.yaml`, `src/main/resources/investcorr/fixtures/default.yaml` | Явно прототипная OpenAPI 3 и синтетические fixtures; generated DTO в `investcorr.dto.generated` под target |
| agent-api-out/pom.xml | Генерация только DTO стандартным генератором, MapStruct, validation/Jackson, тестовый starter; Feign OkHttp transport для полного call timeout |
| agent-main: `AgentApplication.java`, `src/main/resources/application.yaml`; README.md | Явный import InvestCorrConfiguration; enabled по умолчанию и URL из окружения; инструкция/ограничения |

Не расширять component scan на все модули и не активировать GigaChat. Версии уже принятых BOM/библиотек не менять. Использовать управляемую совместимую версию Feign OkHttp; если BOM не управляет нужным артефактом, до фиксации версии проверить совместимость. Все новые интерфейсы, методы, DTO/поля и OpenAPI описаны кратко на русском. Собственные Javadoc-шаблоны не добавлять.

### 3.2. Доменные данные

- `PermissionQueryInDTO`: digitalUserId, organizationRef, paymentRef, operationType (только recall/details), operationId, expectedAggregateVersion. Результат: outcome ALLOWED/DENIED/TECHNICAL_ERROR, operationId, expectedAggregateVersion, errorCode. Ответ возвращает связь с подготовкой; решение об актуальности принимает будущий оркестратор.
- `OrganizationQueryInDTO`: epkId организации, digitalUserId, expectedOrganizationRef, trustedIdentityRef. Результат: FOUND/NOT_FOUND/AMBIGUOUS/BINDING_MISMATCH/TECHNICAL_ERROR, OrganizationDTO(ref,name), source, fetchedAt, errorCode. FOUND допустим только при точном совпадении ref с ожидаемым; неполный ответ — техническая ошибка. Источник/время назначает адаптер после корректного ответа.
- `SubmissionEnvelopeInDTO`: submissionId, operationId, summaryVersion, idempotencyKey, type, paymentRef, digitalUserId, organization(ref,name), changes, notificationPolicy, payloadHash. `PaymentChangesDTO` содержит ровно recipientInn/recipientAccount/recipientName/purpose. Для status/recall changes отсутствует; для details — непустое подмножество непустых строк. Форматная ФЛК остаётся P3.01.
- `SubmissionResultOutDTO`: ACCEPTED/REJECTED/UNCERTAIN/NOT_SENT, corrRequestId (обязателен только для ACCEPTED), errorCode. ACCEPTED означает только приём, никаких paymentStatus/smsDelivered.
- `CallContextDTO`: correlationId, Deadline, attempt (начиная с 1). testScenarioId не берётся из сообщения/LLM: сценарии определяются доверенной fixture-конфигурацией по связке ID; не добавлять его в chat или банковский wire.

Проверки выполняет адаптер/конфигурация, не DTO. Неверные локальные аргументы дают понятную IllegalArgumentException до вызова; корректный envelope с исчерпанным deadline даёт NOT_SENT. Операционный результат не меняет FSM и не формирует пользовательский текст.

### 3.3. Прототипный HTTP-контракт (Q-01)

Три POST: `/api/v1/permissions/check`, `/api/v1/organizations/resolve`, `/api/v1/submissions`. JSON UTF-8; имена полей camelCase, type — status/recall/details. Вход первых двух соответствует query выше. Ответ permissions содержит outcome и привязку operationId/version; organizations — outcome и organization. Submit body соответствует envelope, плюс заголовок `Idempotency-Key`, равный ключу в body. Все запросы передают `X-Correlation-ID` и `X-Attempt`; deadline остаётся локальным ограничением, не доверенным полем внешнего ответа.

Пример успешного submit-ответа: `{"outcome":"ACCEPTED","corrRequestId":"corr-demo-1"}`. HTTP 200 допускает только валидный типизированный исход сервиса. Для submissions допустимы ACCEPTED или REJECTED с кодом бизнес-отказа; NOT_SENT/UNCERTAIN формирует локальный адаптер. HTTP 409 с `{"code":"IDEMPOTENCY_CONFLICT"}` означает отказ текущему отличающемуся payload и сохраняет ранее принятый запрос. Прочие 4xx/5xx, неожиданный redirect, неизвестный outcome, пустой ID ACCEPTED и ошибки JSON — техническая ошибка для lookup/permissions и UNCERTAIN для начатого submit. Сам HTTP-код не доказывает отсутствие приёма. Неизвестные поля и несовместимые комбинации ответа отклоняются.

Пример status-envelope использует приведённые поля без changes, policy `SMS_900_PERSONAL_PHONE_V1`; ключ/ID/hash создаёт вызывающая сторона. Адаптер не генерирует новый ключ и не пересчитывает молча hash при повторе. Реальное подключение банка потребует его спецификации, авторизации и повторного mapping; этот контракт не объявляется подтверждённым банком.

### 3.4. Canonical payload и изменяемые DTO (Q-03)

Предлагается `corr-payload-v1`: UTF-8 компактный JSON, ключи объектов лексикографически сортируются на всех уровнях, целая summaryVersion без преобразования в floating point, null optional fields отсутствуют; строки не trim/normalize, нули реквизитов сохраняются. Хеш — `sha256:` + lowercase hex SHA-256. Хешируется весь envelope, кроме payloadHash, с обязательным notificationPolicy. Служебные correlationId/attempt/deadline не входят.

Перед передачей адаптер строит собственный снимок wire DTO и canonical bytes, проверяет заявленный hash. Stub хранит отдельно эти bytes/hash/type/key и стабильный corrRequestId; сравнивает bytes, а не только заявленный hash. Атомарный compute/put-if-absent обеспечивает один приём при конкурентных одинаковых запросах. Мутация исходного DTO после завершения не меняет сохранённый приём; same-key/different-payload становится конфликтом. Конкурентно менять DTO во время вызова запрещено контрактом вызывающей стороны. Это не возвращает defensive copy в сами DTO. Надёжное сохранение неизменяемого envelope до отправки и повторное чтение относятся к P1.07.

### 3.5. Режимы, deadline и ошибки (Q-02)

`integrations.invest-corr.stub` — строго enabled/disabled, без default в Properties; application.yaml явно задаёт enabled. `.url` обязателен при disabled: абсолютный HTTP(S), host, без userinfo/fragment. В enabled пустой URL допустим и не используется. Неизвестный/отсутствующий режим — понятная ошибка старта. Conditional-конфигурации не создают Feign/transport в stub-режиме. Ошибка вызова не включает stub.

Один вызов клиента — ровно одна попытка. Feign Retryer.NEVER_RETRY; OkHttp retryOnConnectionFailure=false, redirects отключены. `DeadlineFeignClient` непосредственно перед сетью заново определяет остаток и задаёт native callTimeout плюс connect/read ограничения не больше остатка. По согласованному Q-04 hostname разрешён, но синхронный DNS исключён из жёсткой временной гарантии и ограничивается timeout ОС: отмена native call не гарантирует своевременный возврат из lookup. Собственный bounded resolver не добавляется. Истёкший до вызова deadline не открывает соединение. Возврат результата после deadline не превращает неизвестный submit в ACCEPTED. Полный deadline хода, резерв на commit, число попыток и backoff вводятся оркестратором в последующих шагах; сумма connect/read не выдаётся за полный timeout. Ограничение DNS должно учитываться при последующей проверке полного SLA; Future timeout не доказывает остановку транспорта.

Stub проверяет deadline до приёма; задержка fixture ограничивается остатком, без фоновых задач и эффекта после завершения. ACCEPT_THEN_TIMEOUT фиксирует приём в памяти до возврата UNCERTAIN; повтор возвращает прежний ID. ACCEPT_THEN_ALWAYS_TIMEOUT скрывает ответ каждого повтора при одном приёме. Клиент не запускает retries сам.

**Основание принятого Q-04.** Разработчик проверил OkHttp 4.12.0: при `callTimeout=100ms` и блокирующем `Dns.lookup` на 700 мс получен `InterruptedIOException` через 783 мс. Probe: `target/p103-diagnostic/DnsTimeoutProbe.java`, exit 0; это диагностика ограничения транспорта, не business RED и не GREEN AC-03-06. В `RouteSelector.kt` DNS вызывается синхронно; отмена `RealCall` не прерывает lookup. Пользователь выбрал A: «Для MVP разрешить hostname; явно исключить DNS из жёсткого deadline, оставив его тайм-аут ОС (рекомендуется).» Ревизия 2 фиксирует это решение; зависимая HTTP-часть CP-04/T-06 разрешена без повторного согласования.

### 3.6. Минимальные fixtures

YAML связывает scenarioId с синтетической доверенной связкой epkId/digitalUserId/organizationRef/paymentRef; unknown mapping не получает ALLOWED/FOUND/ACCEPTED. Выбор сценария только конфигурацией. Для permission: ALLOWED, DENIED, ERROR_THEN_ALLOWED, ALWAYS_ERROR, STALE_VERSION. Для organization: FOUND, NOT_FOUND, AMBIGUOUS, WRONG_BINDING, ERROR, SLOW. Для каждого submit type: ACCEPTED, REJECTED, TIMEOUT_BEFORE_ACCEPT (наблюдаемый UNCERTAIN), ACCEPT_THEN_TIMEOUT, ACCEPT_THEN_ALWAYS_TIMEOUT. Повтор/конфликт/конкуренция проверяются поверх реестра. Запоздалый permission возвращает старую версию, но её бизнес-отбрасывание проверяется позднее с оркестратором.

## 4. Тестовый дизайн до кода

| ID | AC | Уровень / вход | Ожидаемый RED → доказательство GREEN |
|---|---|---|---|
| T-01 | 01,03 | unit: InvestCorrAdapterTest, все типы mapping и некорректные ответы | Нет mapping/проверки → доменные результаты, правильная привязка, unknown не разрешён |
| T-02 | 04 | unit: SubmissionCanonicalizerTest; разный порядок полей, нули, изменённый payload/hash | Нет стабильного представления/контроля → одинаковые bytes/hash, изменение выявлено до сети |
| T-03 | 03,04,06 | unit: StubInvestCorrClientTest; параметризация types/scenarios, fake time, concurrent same key | Нет реестра/исходов → один приём, прежний ID, конфликт, корректные timeout и отсутствие позднего приёма |
| T-04 | 02,07 | Spring context: InvestCorrConfigurationTest; оба режима, missing/invalid values | Нет регистрации/валидации → ровно один bean; при enabled нет transport и ни одного HTTP-запроса |
| T-05 | 01,05 | HTTP: InvestCorrHttpContractTest; Feign + loopback server с записью requests | Нет wire реализации → точные routes/body/headers; один запрос при 500/обрыве, без fallback; типизированные исходы всех сервисов |
| T-06 | 04,05,06 | HTTP: тот же стенд, same key payload, delayed headers/slow body/disconnect, без задержек DNS | Неверные retries/timeout → стабильные bytes, одна попытка, native callTimeout ограничивает чтение, pre-expired не приходит на сервер; DNS не входит в жёсткую гарантию по Q-04 |
| T-07 | 07 | Существующий BootstrapContextTest/BootstrapJarIT и clean verify | Нет main import → corr доступен в bootstrap; все обязательные существующие проверки GREEN |

HTTP-стенд локальный, динамический порт, без Docker/банка; допускается JDK HttpServer, без нового внешнего сервера. Для T-06 реальные задержки до 1 с, deadline 100–300 мс, разумный допуск планировщика, без assertions «ровно N мс». Проверка native callTimeout использует медленно поступающее тело, а не только молчащий сервер; локальный IP исключает влияние DNS в этом измерении. Результат не доказывает жёсткий предел для hostname вместе с DNS; это согласованное ограничение Q-04. Unit fake-clock не считается HTTP-доказательством. Tests этого шага в agent-api-out `src/test/java/.../investcorr`; не создавать по тесту на каждый getter или искусственные архитектурные fixtures.

Команды implement: выборочно `mvn -pl agent-api-out -am test -Dtest=<набор> -Dsurefire.failIfNoSpecifiedTests=false`; итог `mvn clean verify`. Для негативной проверки missing property использовать изолированный context без application.yaml, иначе default маскирует отсутствие. E2E чата/БД/настоящая Ultra неприменимы: соответствующего пути ещё нет. Все T-01–T-07 обязательны, не запускались на design.

## 5. Порядок implement

1. После согласования — отдельный Java-разработчик. Добавить минимальный test harness/сигнатуры, получить исполнимый RED T-01/T-02, затем DTO/порты/mapping/canonicalizer → GREEN.
2. T-03 RED → stub и in-memory реестр → GREEN; рефакторинг без изменения AC.
3. T-04 RED → typed properties и обе условные ветви; T-05/T-06 RED → OpenAPI generation, Feign/transport и error mapping → GREEN. Недоступная зависимость не считается RED.
4. T-07: main import/YAML, bootstrap, итоговая регрессия. Не повторять уже успешные наборы без изменения/проблемы.
5. Review соответствия AC, краткие факты RED/GREEN в task, README, history/WBS/handoff; `Implement на проверке` и остановка. Git commit/push не входит.

## 6. Вопросы и развилки

| ID | Варианты и последствия | Рекомендация | Ответ / статус |
|---|---|---|---|
| Q-01 | Принять три прототипных POST и wire-схему §3.3; либо ждать спецификацию банка, блокируя Feign AC | Прототипная OpenAPI + локальный HTTP; банк не требуется | Рекомендация согласована 28.09.2026 / закрыт |
| Q-02 | Выполнить одну попытку с native whole-call timeout сейчас; retry-оркестрацию/устойчивый стенд оставить P1.07/P1.12. Перенос всего deadline на P1.12 ослабит AC текущего шага | Граница §3.5, Feign/OkHttp, без собственной фоновой очереди | Рекомендация согласована 28.09.2026 / закрыт |
| Q-03 | Зафиксировать canonical JSON v1 §3.4 и снимок на границе; либо передавать opaque payload/hash от будущего оркестратора, отложив содержательную проверку стабильности | Canonical JSON v1, простые DTO сохраняются | Рекомендация согласована 28.09.2026 / закрыт |
| Q-04 | A: разрешить hostname, DNS вне жёсткого deadline (timeout ОС), явно ограничить гарантию AC-03-06/T-06. B: только IP и прямое соединение без proxy/DNS, сохранить строгий deadline, сузить допустимые URL. C: добавить отдельный bounded resolver, расширив объём и проверки управления ресурсами | A для MVP; ограничение DNS явно документировать, не считать полным SLA 10 с | Пользователь явно выбрал A, 28.09.2026 / закрыт |

28.09.2026 пользователь ответил «согласовано» после представления r1, рекомендованных вариантов и вводного абзаца. Q-01–03 закрыты; implement разрешён сразу. Затем пользователь явно выбрал Q-04 A; r2 фиксирует принятое исключение DNS в AC, транспорте и тестах без новых решений. Зависимая HTTP-часть разрешена без повторной команды.

## 7. Риски и воспроизведение

Прототипный HTTP доказывает наш контракт, не промышленную совместимость. Mutable DTO требуют проверки снимка; hash не заменяет авторизацию. In-memory реестр не переживает restart и не доказывает durable exactly-once. UNKNOWN не превращается в отказ, Q-05 остаётся отложенным. Lookup имени/полномочия не доказывают привязку произвольного paymentId из чата. По принятому Q-04 DNS для hostname ограничивается timeout ОС и может превысить deadline приложения. Full-turn 10 секунд и настоящее SMS этим шагом не проверяются.

После implement воспроизведение: `mvn clean verify`; `java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=bootstrap` проверяет только автономный старт с corr stub, без готового чата. Конкретный тестовый HTTP URL назначается автоматически. На design выполнена только содержательная сверка документов/кода, сборка не запускалась.

## 8. Согласование

| Ревизия | Изменение | Открытые вопросы | Решение пользователя |
|---|---|---|---|
| 1 | Первичный design P1.03 с редакционным вводным абзацем | Нет; Q-01–Q-03 закрыты | «согласовано», 28.09.2026 |
| 2 | Техническая фиксация принятого Q-04 A: hostname разрешён, DNS вне жёсткого deadline, timeout ОС | Нет; Q-04 закрыт | Явный выбор A, 28.09.2026 |

**Согласованная ревизия:** 2. **Implement:** разрешён полностью в пределах шага, включая HTTP-часть. Q-01–04 закрыты; повторное согласование технической фиксации принятого ответа не требуется.
