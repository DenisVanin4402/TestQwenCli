# Design: P1.01 — Maven reactor и проверяемые границы модулей

**Роль:** архитектор. **Стадия:** design. **Ревизия:** 2. **Статус:** Design согласован; реализация принята пользователем с поправкой MVP.

## Поправка по прямому решению пользователя — MVP

ArchUnit, T-03, автоматические проверки архитектурных границ/графа и их Good/Bad fixtures исключены. Соответствующая автоматизация AC-04/CP-03 и требования к fixtures ниже больше не применяются; архитектурные ограничения сохраняются для реализации/review. Остальные build/bootstrap проверки сохраняются. Это явно запрошенное упрощение согласованной ревизии 2.

## 1. Привязка и объём

Phase 1, [WBS P1.01](../plan_phase1.md), требования R01/R02. Источники: [архитектура](../../final/architecture.md), [модули](../../final/modules.md), [ADR-001](../../adr/0001-outbound-clients-and-stubs.md), [ADR-002](../../adr/0002-liquibase-xml.md). Предыдущих шагов нет; зависимость отсутствует.

Исходное состояние: корневой `pom.xml` — одиночный `org.example:invest-pro:1.0-SNAPSHOT`, Java source/target 21; `src/main/java/org/example/Main.java` — демонстрационный main. Reactor, Boot-приложение и тесты отсутствуют. Пользовательские staged/untracked документы сохраняются. Координатор проверил наличие Temurin 21.0.10 и Maven 3.9.12; сообщение `Access is denied.` перед Maven требует выяснения при запуске implement. Сборка проекта не проверялась.

Результат шага: родительский POM, восемь модулей, один исполняемый Boot JAR, фиксированный стек, автоматические проверки структуры/границ и ограниченный smoke запуска каркаса. Никаких доменных портов/DTO, обработчиков HTTP, клиентов/stub, repositories, FSM-переходов или схемы БД в этом шаге. MapStruct-конфигурация и контракты — P1.02; клиенты — P1.03/P1.04; PostgreSQL/Liquibase и SSM persistence — P1.05/P1.06. Свойства ADR-001 не считаются реализованными самим наличием зависимостей.

## 2. Критерии приёмки

| AC | Условие / вход | Проверяемый результат | Требование |
|---|---|---|---|
| AC-01 | Сборка из корня на JDK 21 | Parent `pom`, ровно восемь заданных jar-модулей, единая версия, без циклов; compiler release 21 | R01/R02 |
| AC-02 | Effective POM и resolved tree | Однозначные фиксированные версии выбранного стека, нет Boot 4 / AI 2 / Flyway; стек разрешается и проходит smoke | R01, ADR-001/002 |
| AC-03 | `package` и инспекция JAR | Только `agent-main` содержит Boot launcher/BOOT-INF и Start-Class; остальные — обычные JAR, старый main отсутствует | R02 |
| AC-04 | Проверки graph + bytecode | Разрешены только зависимости таблицы ниже; integration/UI не импортируют DB/JPA/JDBC, service не импортирует транспорт/SSM; отрицательные fixtures доказанно отклоняются | R02 |
| AC-05 | Явный профиль `bootstrap` без secrets/БД | Каркас Boot стартует и корректно завершается в bounded smoke без бизнес-beans, DataSource, EntityManagerFactory, Liquibase и реальных клиентов | R01, ADR-001/002 |
| AC-06 | Полная регрессия | `mvn clean verify` выполняет unit и обязательные smoke IT; зафиксированы RED/GREEN, результаты, effective POM/tree; нет пропущенных обязательных тестов | Процесс TDD |

## 3. Детальный дизайн реализации

### 3.1. Parent и зависимости

Предлагается сохранить Maven coordinates `org.example:invest-pro:1.0-SNAPSHOT`, чтобы не расширять шаг переименованием публикации; Java-пакеты строго `ru.sberbank.pprb.agent`. Parent наследует `spring-boot-starter-parent:3.5.15` (тем самым Boot BOM/plugin defaults), `packaging=pom`. В dependencyManagement импортируются Spring Cloud BOM, затем Spring AI BOM; явно управляются SSM core/data-jpa и GigaChat starter. При пересечении BOM итог определяется effective POM; непредусмотренное изменение версии — блокирующий результат, а не повод автоматически добавлять exclusions. Module versions управляются parent, внутренние зависимости используют `${project.version}`.

По ответу пользователя на Q-01 выбран указанный ниже точный стек; его совместимость по-прежнему должна быть проверена при implement. Ответ и время записи приведены в §6.

| Компонент | Выбранный pin / управление | Где используется |
|---|---|---|
| JDK | 21; compiler `release=21`, UTF-8 | Все модули; Enforcer требует JDK `[21,22)` |
| Maven | Проверка `[3.9,4)`; исходно доступен 3.9.12 | Parent |
| Spring Boot | 3.5.15 | Parent; starter в main; test starter test-scope |
| Spring Cloud | `org.springframework.cloud:spring-cloud-dependencies:2025.0.3` | Parent BOM |
| OpenFeign | `spring-cloud-starter-openfeign`, managed 4.3.3 | api-out |
| Spring AI | `org.springframework.ai:spring-ai-bom:1.1.2` | Parent BOM |
| GigaChat | `chat.giga:spring-ai-starter-model-gigachat:1.1.2` | api-out |
| Spring Statemachine | `spring-statemachine-core` и `spring-statemachine-data-jpa`: 4.0.2 | db |
| Liquibase | `org.liquibase:liquibase-core`, Boot-managed 4.31.1 | db |
| PostgreSQL / JPA | driver runtime + `spring-boot-starter-data-jpa`, версии Boot BOM | db; только Jakarta Persistence API в model |
| Compiler / Surefire / Failsafe | 3.14.1 / 3.5.6 / 3.5.6 из Boot parent | Compiler общий; Surefire unit; Failsafe IT в main |
| Enforcer / Dependency / Help | 3.5.0 / 3.8.1 / 3.5.1 из Boot parent | Validate среды; фиксированные команды диагностики |
| Boot Maven plugin | 3.5.15 | Execution `repackage` только main |
| ArchUnit | `com.tngtech.archunit:archunit-junit5:1.4.1` | Только test-scope main |

SSM/GigaChat не объявляются самостоятельными BOM, если их артефакт — обычная библиотека. Maven dependencyManagement не добавляет библиотеки во все модули. Не подключать MVC/HTTP endpoint заранее ради сборки. MapStruct/его processor и OpenAPI generator не нужны до P1.02; их pin определяется в дизайне P1.02 до появления mapper/generated-кода. Не добавлять Lombok, дополнительный движок FSM или заменяющий GigaChat SDK.

T-02 разделяется на T-02A (unit, декларации source POM) и T-02B (Failsafe IT, фактический resolved stack). Только в main к `prepare-package` привязать Help `effective-pom` с output `target/effective-pom.xml` и Dependency `tree` с verbose/output `target/dependency-tree.txt`; явные версии выше. При `mvn clean verify` зависимости reactor к этому моменту уже упакованы. T-02B читает эти файлы на integration-test, проверяет их наличие и итоговые версии всех важных runtime-компонентов. Обычный `mvn test` запускает только T-02A и не требует этих отчётов. Тесты не запускают вложенную Maven-сборку. Для RED T-02B harness сначала генерирует отчёты на ещё неполном stack; assertion о недостающей зависимости является ожидаемой причиной, failure разрешения artifact — нет. Настройка экспорта отчётов допустима как harness до реализации stack; включение целевых библиотек выполняется после этого RED.

Первичные источники проверены 28.09.2026; это документальный кандидат, **не доказанная совместимость нашей сборкой**:

- [Релиз SSM 4.0.2](https://github.com/spring-attic/spring-statemachine/releases/tag/v4.0.2): обновление до Boot 3.5.15; SSM архивирован.
- [POM GigaChat 1.1.2](https://raw.githubusercontent.com/ai-forever/spring-ai-gigachat/v1.1.2/pom.xml): Boot 3.5.10, AI 1.1.2; совместимость с Boot 3.5.15 ещё проверяется.
- [Spring Cloud matrix](https://spring.io/projects/spring-cloud/), [BOM 2025.0.3](https://raw.githubusercontent.com/spring-cloud/spring-cloud-release/v2025.0.3/spring-cloud-dependencies/pom.xml): линия для Boot 3.5, OpenFeign 4.3.3. Ограничение поддержки линии сохраняется.
- [Boot 3.5.15 dependencies source](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.5.15/spring-boot-project/spring-boot-dependencies/build.gradle): Liquibase и версии плагинов из таблицы. Effective POM — обязательная повторная проверка при implement.
- [ArchUnit 1.4.1](https://github.com/TNG/ArchUnit/releases/tag/v1.4.1): фиксированная версия средства архитектурных проверок.

### 3.2. Модули, пакеты и граф

| Модуль | Пакеты / ответственность | Разрешённые прямые project dependencies |
|---|---|---|
| agent-common | `.common`; пока документация пакета | Нет |
| agent-model | `.model`; доменные/persistence подпакеты описаны, типы появятся P1.02/P1.05 | common |
| agent-db | `.db`; будущие `.api`, `.config`, `.fsm.spring` | model, common |
| agent-service | `.service`; будущие порты/usecase/policy/fsm | db, model, common |
| agent-api-in | `.gigaassistant` | service, model, common |
| agent-api-out | `.investcorr`, `.gigachat` | service, model, common |
| agent-ui-back | `.chatui` | service, model, common |
| agent-main | `.main.AgentApplication`, `.main.config` | Остальные семь модулей |

Все пакеты с префиксом `ru.sberbank.pprb.agent`. Создаются root package-info с описанием ответственности, но не фиктивные сервисы/интерфейсы. Пустые подкаталоги и placeholder-классы не требуются.

`agent-main` — композиция, не место для use cases; доступ к `.db.config` для wiring разрешён. Прикладные вызовы `.db.api` разрешены из `.service`; `.db.repository`, `.db.service`, `.db.fsm.spring` закрыты от внешних модулей. Вызовы repositories внутри db остаются допустимы. Транзитивная видимость db через service не является разрешением импорта.

Architecture tests в main проверяют project POM edges и production bytecode отдельно. Запрещаются зависимости API/UI от `.db..`, `.model.entity.persistence..`, `jakarta.persistence`, `javax.sql`, `java.sql`, Spring JDBC/Data repositories; service запрещены external integration packages, ORM entities/API, Feign, Spring AI/GigaChat и SSM. Доменным пакетам model запрещены зависимости на persistence и внешние framework-типы. SSM допустим только в `.db.fsm.spring..`, а Spring AI/GigaChat — `.gigachat..`. Конфигурационные исключения узкие, не разрешение всему main обращаться к данным.

Для проверки использовать ArchUnit с импортом compiled production paths восьми модулей. Правила задаются один раз и применяются к production и test fixtures через `evaluate` с проверкой ожидаемых нарушений. Test fixtures лежат отдельно и не включаются в production scan. Не ограничиваться grep imports: fully qualified ссылки и транзитивный classpath должны проверяться. Не писать собственный bytecode parser.

Защита от пустого успеха: structural test требует все восемь POM, package-info source и единственный AgentApplication; scanner обязан найти AgentApplication.class. Пока policy/service классов нет, тесты явно сообщают число production-классов на модуль (ноль допустим только для библиотек каркаса), а каждое запретительное правило дополнительно проверяется на violating fixture и на допустимой паре. Fixtures покрывают запрещённые прямые edges/cycle, обращение integration → db.api, service → ORM/SSM, external DTO leakage, main → db.repository и дублирование package ownership. Проверка механизма не выдаётся за наличие будущего бизнес-кода.

### 3.3. Запуск и границы smoke

Выбранный пользователем вариант Q-02: `AgentApplication` запускает обычный Spring Boot; профиль `bootstrap` предназначен **только для каркаса**. `application-bootstrap.yaml` задаёт non-web режим и ограниченный список исключений автоконфигурации DataSource/JPA/Liquibase/GigaChat. Точные имена GigaChat auto-config берутся из metadata выбранного JAR при implement и покрываются smoke, а не угадываются. При наличии иных auto-config, создающих сеть/credentials, сначала определить причину; общий запрет всех автоконфигураций неприемлем.

Профиль не называется `stub`: клиентов ещё нет. Не добавлять `integrations.*.stub` без реализаций и не объявлять проверку ADR-001 выполненной. Не изменять production-default на `ddl-auto=none` и не создавать фиктивный XML master. Default runtime до P1.05 не является готовым приложением; воспроизводимый P1.01 запуск явно требует bootstrap. Позже профиль сохраняется как ограниченный тест каркаса либо удаляется отдельным согласованным шагом. В документации запуска это оговаривается.

Smoke IT в main запускает реальный repackaged JAR через ProcessBuilder на Java из текущего `java.home`, с `--spring.profiles.active=bootstrap --spring.main.web-application-type=none`. Timeout 30 секунд; при превышении дочерний процесс завершается в finally, вывод сохраняется. Проверяются exit 0 и успешное завершение context startup. Отдельный in-process context test на том же профиле проверяет отсутствие DataSource/EntityManagerFactory/Liquibase/GigaChat client beans и отсутствие non-daemon ресурсов после close. Зависимости присутствуют в classpath, но интеграционная совместимость JPA/SSM persistence/реального LLM этим не доказывается.

### 3.4. Файлы и неприменимые части

Создать: восемь `<module>/pom.xml`, root `package-info.java` по таблице, `agent-main/src/main/java/ru/sberbank/pprb/agent/main/AgentApplication.java`, `agent-main/src/main/resources/application-bootstrap.yaml`; тесты/harness/fixtures в `agent-main/src/test`; README с командами и ограничениями. Изменить root pom; удалить только проверенный исходный демонстрационный Main после его замены. До удаления повторно проверить отсутствие пользовательских правок.

Временный тестовый harness до reactor: профиль `p101-harness` в существующем root pom, JUnit и Surefire, исходники тестов в `build-tests/src/test/java`; он читает файлы/POM как данные, не требует существования будущих классов. После первого GREEN переносится в main tests, временный профиль/каталог удаляется и регрессия повторяется. Никакого девятого Maven-модуля в итоговом reactor.

Бизнес JSON, mapping, команды/события/guards, банковские ответы: Н/П — функциональных контрактов нет. Данные, транзакции, гонки, recovery, changesets: Н/П — слой хранения не реализуется. Liquibase только зависимость; реальные PostgreSQL тесты обязательны начиная с P1.05. HTTP-контракты, retries/deadline клиента, stub/real переключение: Н/П до появления клиентов; 30 секунд выше — timeout тестового процесса, не SLA хода. Q-05 и SMS-поведение не меняются.

## 4. Тестовый дизайн до кода

| Test ID | AC | Уровень | Вход / fixtures | Ожидаемый RED | GREEN доказывает |
|---|---|---|---|---|---|
| T-01 ReactorStructureTest | 01 | unit/build | Исходный root POM и список восьми модулей | packaging jar / modules отсутствуют | Граф структуры, coordinates, release21, отсутствие циклов |
| T-02A StackPolicyTest | 02 | unit/build | Source POM management | Нет фиксированного BOM/стека | Декларации pins соответствуют design |
| T-02B ResolvedStackIT | 02 | integration/build | Lifecycle-generated effective POM/tree | Неполный resolved stack, assertion недостающей библиотеки | Реальные resolved versions совпадают; нет запрещённых линий/Flyway |
| T-03 ArchitecturePolicyTest | 04 | unit | Violation/allowed fixtures для каждой границы | До реализации правила violating fixture не отклоняется | Scanner/rules детектируют каждое нарушение, production scan выполняется |
| T-04 BootstrapContextTest | 05 | unit/context | Reflection lookup main; bootstrap, без credentials | Main отсутствует, затем ограниченная конфигурация ещё отсутствует | Контекст создаётся/закрывается без infrastructure beans |
| T-05 PackagingIT | 03 | integration/build | Результат package всех восьми модулей | Нет main Boot JAR/repackage либо лишний Boot JAR | Ровно один executable; библиотеки обычные; Main заменён |
| T-06 BootstrapJarIT | 02/05 | integration/process | Actual JAR, JDK21, bootstrap, timeout30s | Приложение ещё не стартует по утверждённому контракту | Classpath linkage и executable startup проходят в ограниченном режиме |
| T-07 FullVerify | 06 | regression | Clean reactor, весь набор T-01–06 | Ранее известные незакрытые AC | Все обязательные проверки исполнены, zero failures/errors/skips |

T-01/02 первоначально читают файлы как XML, используют Java API и JUnit без ссылок на будущие классы: отсутствие модулей вызывает assertion, не compile error. T-04 первоначально использует reflection; отсутствие Main — утверждение AC, отсутствие JDK/Maven/artifact в repository — ошибка среды, не RED. T-05/06 добавляются до соответствующей настройки packaging/startup и сначала запускаются в фазе verify с пока обычным JAR, чтобы получить предметный RED. Каждый новый функциональный фрагмент build/harness имеет свой RED; нельзя собрать готовый reactor со всеми features и задним числом написать smoke.

Здесь исходное чтение XML относится к T-02A; T-02B добавляется после минимального reactor/harness, до полного подключения runtime stack. T-03 проверяет поведение защитных правил: в RED правило ещё не выявляет нарушающий fixture. Намеренные нарушения production-кода не создаются; GREEN production scan каркаса честно сообщает небольшой фактический объём классов.

T-03 negative fixtures остаются частью регрессии; не вносят недопустимые зависимости в production. Ни одно обязательное правило не отключается глобальным `allowEmptyShould`; количество/состав scanned paths проверяется явно. Для ещё пустого исходного пакета отдельно фиксируется отсутствие production-классов и выполняется проверка того же правила на fixtures. В будущем появление исходного класса автоматически включает его production-проверку.

Команды приведены в task; в design они не выполнялись. HTTP/PostgreSQL integration и бизнес-e2e: Н/П, соответствующего поведения нет. Реальный GigaChat: Н/П, P1.13; ни fixture, ни loading класса не оценивают Ultra. Регрессии предыдущих шагов нет.

## 5. Порядок implement

1. После согласования новой актуальной ревизии и отдельной команды implement Java-разработчик проверяет git status/JDK/Maven, сохраняет исходное состояние; CP-01.
2. Минимальный harness → T-01/02A RED → parent/module skeleton → GREEN; затем harness экспорта отчётов и T-02B RED на неполном stack → целевые зависимости → GREEN. Не писать AgentApplication и profile до их tests. CP-02.
3. T-03 fixtures → RED → минимальные rules/scanner → GREEN; проверить реальные classpath paths, добавить package ownership. CP-03.
4. T-04 RED → AgentApplication и согласованный bootstrap → GREEN. CP-04.
5. T-05/06 RED → repackage только main / требуемая настройка startup → GREEN. Уточнить ordering так, чтобы каждый отсутствующий результат был проверен до реализации. CP-05.
6. Перенести harness в main, убрать временный профиль, refactor; clean verify, effective POM/tree, отчёты; CP-06.
7. Координатор обновляет WBS/history/handoff до `Implement на проверке`, остановка без следующего шага; CP-07.

## 6. Вопросы и развилки

| Q-ID | Варианты / последствия | Рекомендация | Ответ пользователя | Статус |
|---|---|---|---|---|
| Q-01 | Принять точный кандидат Boot 3.5.15 / SSM 4.0.2 / AI 1.1.2 / GigaChat 1.1.2 / Cloud 2025.0.3 / Liquibase 4.31.1 с обязательным smoke; либо указать другой patch-набор внутри принятых линий и заново проверить BOM | Первый вариант; изменение major/движка вне шага | «подтвержда.» — выбран первый вариант; записано 2026-09-28T01:25:52+03:00 (Europe/Moscow) | Закрыт в ревизии 2 |
| Q-02 | Ограниченный явный профиль bootstrap без БД/клиентов сейчас; либо только сборка JAR, а проверку запуска перенести на P1.05, изменив AC-05/06 и объём приёмки P1.01 | Bootstrap с явно ограниченными гарантиями и без имитации stub | «подтвержда.» — выбран bootstrap в P1.01; записано 2026-09-28T01:25:52+03:00 (Europe/Moscow) | Закрыт в ревизии 2 |

Ответ «подтвержда.» относится к обоим рекомендованным вариантам Q-01/Q-02. Указано фактическое время записи ответа в документы, а не неизвестное точное время пользовательского сообщения. Открытых вопросов нет; технический объём не изменён. По AGENTS.md ответы внесены в ревизию 2, после чего выполняется повторная остановка. Ответы не означают согласование ещё не предъявленной ревизии 2 или разрешение implement.

## 7. Риски, ограничения и воспроизведение

SSM архивирован, линия Cloud/GigaChat ограничена сопровождением; обновление major не выполняется автоматически. Расхождения transitive dependencies, BOM и autoconfiguration выявляются effective POM/tree и smoke; при новой существенной несовместимости зависимая работа останавливается. Dependency resolution требует repository access; отсутствие сети не считается ни RED, ни исключением обязательной проверки. Архитектурные тесты каркаса дают механическую защиту будущих границ, а не подтверждение ещё отсутствующих use cases.

Команды воспроизведения после implement: `mvn clean verify`, затем `java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=bootstrap --spring.main.web-application-type=none`. Ожидаются успешная сборка всех модулей и завершение ограниченного bootstrap с кодом 0. Фактические RED/GREEN и проверенные команды реализации приведены в [task.md](./task.md). Секреты/реальные данные не требуются.

## 8. Согласование ревизии

| Ревизия | Что изменено | Вопросы | Решение пользователя |
|---|---|---|---|
| 1 | Первичный пакет AC, reactor/stack, границы и TDD harness, bootstrap scope | Q-01, Q-02 | Не согласован |
| 2 | Зафиксирован выбор точного стека и bootstrap по ответу «подтвержда.»; технический объём сохранён | Q-01/Q-02 закрыты; открытых нет | Ответы записаны 2026-09-28T01:25:52+03:00 (Europe/Moscow); ревизия 2 согласована пользователем; отдельная команда implement P1.01 получена |

**Согласованная ревизия:** 2. **Разрешение implement:** получено. Пользователь: «Согласовываю P1.01 ревизию 2». `implement P1.01`. Время записи: 2026-09-28T01:30:54.6403102+03:00. Техническая ревизия не меняется; запущен отдельный Java-разработчик. Реализация с поправкой MVP принята пользователем: «реализацию подтверждаю»; подробности в task.md.
