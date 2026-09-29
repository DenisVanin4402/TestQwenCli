# ADR-002. Миграции PostgreSQL через Liquibase в XML

**Дата:** 28.09.2026. **Статус:** принято по указанию заказчика. **Реализация:** ещё не начата. Решение заменяет прежний выбор Flyway в архитектурных документах.

Связанные документы: [общий план](../plan/plans.md), [фаза 1](../plan/plan_phase1.md), [архитектура](../final/architecture.md), [хранение FSM](../final/state-machine.md), [ADR-001](./0001-outbound-clients-and-stubs.md).

## Решение и владение

Единственный механизм версионируемого создания и изменения схемы PostgreSQL — **Liquibase**. Master changelog и все подключаемые changelog-файлы имеют формат **XML**. Это относится к доменным таблицам, индексам/ограничениям, справочным данным и технической схеме Spring Statemachine.

Миграции, их конфигурация и проверка принадлежат `agent-db`; главная конфигурация запуска находится в `agent-main`. Доменные и SSM-специфичные миграции находятся в отдельных XML-файлах общего каталога `changeset`; отдельных master-файлов и подкаталогов по подсистемам нет. При замене движка его новые миграции меняются вместе с адаптером; уже применённая история не переписывается и не удаляется из учёта.

Прежние Flyway-зависимости, каталог `db/migration` и миграции `V...sql` не являются целевыми. Отдельный путь изменения схемы через Hibernate `update/create` или `schema.sql` не вводится. В репозитории пока нет реализованной базы, поэтому этот ADR меняет проектный выбор; перенос существующей промышленной Flyway-истории не заявляется выполненным.

## Структура будущих файлов

```text
agent-db/src/main/resources/db/changelog/
  db.changelog-master.xml
  changeset/
    0001_createtable_upd.xml
    0002_alter_table_xxx.xml
    0003_create_table_ssm.xml
```

В каталоге `db/changelog` один `db.changelog-master.xml`. Все файлы миграций лежат непосредственно в `changeset`, имеют формат `NNNN_описание.xml` и общую последовательную нумерацию `0001`, `0002`, …, включая SSM. Master содержит явные `include` в порядке этой нумерации. Пример структуры, а не уже созданный рабочий changelog:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
    <include file="changeset/0001_createtable_upd.xml" relativeToChangelogFile="true"/>
    <include file="changeset/0002_alter_table_xxx.xml" relativeToChangelogFile="true"/>
    <include file="changeset/0003_create_table_ssm.xml" relativeToChangelogFile="true"/>
</databaseChangeLog>
```

Для changeset задаются стабильные `id` и `author`; пути и порядок включения закрепляются при принятии дизайна. Применённый changeset не редактируется: исправление добавляется новым changeset. Основные изменения описываются XML change types. Если для PostgreSQL требуется нативный SQL, он допускается внутри XML `sql` с обоснованием в `design.md`; отдельные SQL/YAML/JSON changelog-файлы не создаются. XML является поддерживаемым форматом Liquibase. [Официальная документация XML changelog](https://docs.liquibase.com/concepts/changelogs/xml-format.html).

## Подключение приложения

Для выбранной линии Spring Boot 3.5 используется `org.liquibase:liquibase-core`; версия согласуется с BOM на P1.01. Путь XML задаётся явно, поскольку формат по умолчанию у Boot другой. [Spring Boot 3.5: Liquibase](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html#howto.data-initialization.migration-tool.liquibase).

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
  jpa:
    hibernate:
      ddl-auto: validate
  sql:
    init:
      mode: never
```

Liquibase использует выбранную БД приложения: PostgreSQL либо H2. По [ADR-003](./0003-h2-persistence-and-runtime.md) DB-тесты и локальное окружение могут работать на H2 с настоящей persistence и той же XML-цепочкой; Hibernate-автогенерация схемы не используется. Для test fixtures используется отдельная тестовая инициализация через сервисный слой либо тестовый XML changelog вне production master. Прикладные repository по-прежнему вызывают только сервисы `agent-service`; запуск миграций является инфраструктурной инициализацией `agent-db`, а не доступом API/UI к данным.

## Проверки и TDD

На шаге P1.05 архитектор описывает ожидаемую схему и критерии. В `implement` разработчик сначала добавляет проверки, демонстрирует RED из-за отсутствующего требуемого поведения схемы, затем пишет XML changeset и получает GREEN.

По ADR-003 обязательны проверки на H2 с настоящей persistence: развёртывание пустой базы; повторный запуск без повторного применения изменений; обновление с предыдущего принятого набора changeset; ограничения уникальности/связей; соответствие JPA mapping. Для первой версии, пока предыдущей схемы нет, сценарий upgrade помечается неприменимым с причиной, а не фиктивно успешным. На P1.06 дополнительно проверяются native SSM mapping и фактическое хранение LOB/контекста; тип колонки не угадывается по названию Java-поля.

Rollback либо стратегия исправления новым changeset описывается в дизайне каждого изменения. Проверяемый rollback запускается только на одноразовой тестовой базе; автоматический destructive rollback рабочего контура не является частью старта приложения. Ошибка migration не игнорируется и не приводит к запуску приложения с частичной схемой. Фактические команды, RED/GREEN и результаты фиксируются в `task.md` шага и корневом `history.md`.
