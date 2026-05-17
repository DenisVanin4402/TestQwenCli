# Правила репозитория

## Язык документации и общения

В этом проекте вся документация, комментарии к задачам, обсуждения, инструкции и ответы агентов ведутся на русском языке. Английский допустим только для имен классов, методов, пакетов, команд, API, логов и других технических идентификаторов, где перевод ухудшит точность.

## Структура проекта и модулей

Это небольшое Java 21 приложение на Spring Boot, собираемое через Maven. Основной код находится в `src/main/java/com/example/testqwencli`: точка входа приложения — `TestQwenCliApplication.java`, текущий веб-эндпоинт — `HomeController.java`. Runtime-настройки размещаются в `src/main/resources/application.properties`. Тесты повторяют основной пакет в `src/test/java/com/example/testqwencli`. Проектная документация и планы лежат в `docs/`. Каталог `target/` создается Maven и не коммитится.

## Команды сборки, тестов и запуска

В репозитории нет Maven Wrapper, поэтому используйте системный Maven.

- `mvn test`: запускает тесты JUnit и Spring Boot.
- `mvn spring-boot:run`: запускает приложение локально, обычно на порту `8080`.
- `mvn package`: компилирует проект, запускает тесты и создает артефакт в `target/`.

Все команды выполняйте из корня репозитория, где расположен `pom.xml`.

## Specification-Driven Development

Существенные изменения ведите через SDD-артефакты. Источник истины для задачи должен находиться в `docs/specs/<id>-<slug>/`, а общий процесс описан в `docs/sdd/`.

Минимальный pipeline:

```text
specification -> clarification -> plan -> tasks -> tests/contracts -> implementation -> verification -> spec/memory update
```

Перед реализацией проверьте:

- `spec.md` содержит требования `REQ-*` и acceptance criteria `AC-*`;
- критические требования не имеют незакрытых уточнений;
- `plan.md` перечисляет affected files, контракты, риски и проверки;
- `tasks.md` связывает каждую задачу с requirement IDs и ownership scope;
- `test-plan.md` описывает проверки или явные gaps.

Для новой задачи используйте шаблон `docs/specs/_template/`. Для bootstrap-примера используйте `docs/specs/0001-sdd-bootstrap/`.

### Режимы входа

- `spec-first`: используйте, когда входом является бизнес-запрос или issue.
- `diff-driven`: используйте, когда системный аналитик уже внес изменения в Markdown-документацию, OpenAPI или DBML в отдельном MR.

В `diff-driven` режиме сначала создаются `intake.md`, `diff-map.md`, `impact-map.md`, `source-context.md`, `contracts/openapi-diff.md` и `contracts/dbml-diff.md`. Только после этого синтезируется `spec.md`.

### Gates

- Spec gate: требования, критерии приемки, edge cases и NFR понятны.
- Plan gate: технический путь, контракты, тесты, rollback и ADR-необходимость зафиксированы.
- Task gate: задачи малы, проверяемы и имеют ownership boundaries.
- Implementation gate: изменены только согласованные файлы, проверки запущены, `work-log.md` обновлен.
- Review gate: spec, code и tests согласованы; findings исправлены или приняты явно.

Если код, тесты и спецификация расходятся, остановитесь и зафиксируйте расхождение в `work-log.md` или `handoff.md`.

## Qwen CLI SDD entrypoints

Qwen-specific команды находятся в `.qwen/commands/sdd/`:

- `/sdd:specify`
- `/sdd:clarify`
- `/sdd:intake-diff`
- `/sdd:impact-map`
- `/sdd:synthesize-spec`
- `/sdd:plan`
- `/sdd:tasks`
- `/sdd:implement`
- `/sdd:review`

Skills находятся в `.qwen/skills/`, agent role definitions — в `.qwen/agents/`. Reviewer и security reviewer по умолчанию read-only.

## Стиль кода и соглашения об именах

Следуйте текущему стилю Spring Boot. Пакеты называйте строчными буквами и держите код приложения внутри `com.example.testqwencli`. Классы называйте по ответственности: например, `HomeController`, `OrderService`, `UserRepository`. В Java-файлах сейчас используется табуляция; сохраняйте этот стиль при изменении существующего кода. Для новых Spring-компонентов предпочитайте constructor injection. Нетривиальную бизнес-логику выносите из контроллеров в сервисы.

## Правила тестирования

Тесты используют JUnit 5 из `spring-boot-starter-test`. Имена тестовых классов должны заканчиваться на `*Tests`, а методы — описывать проверяемое поведение, например `rootEndpointReturnsStatusMessage`. Для проверки контекста используйте `@SpringBootTest`, для HTTP-эндпоинтов — `MockMvc`. При изменении контроллеров, конфигурации или поведения старта приложения обновляйте тесты. Порог покрытия не настроен, поэтому важнее осмысленные проверки измененного поведения.

При завершении SDD-задачи запишите результат проверок в `docs/specs/<id>-<slug>/work-log.md`.

## Коммиты и Pull Request

История проекта использует короткие imperative-сообщения в нижнем регистре, например `add research and plan for sdd`. Для новых коммитов пишите краткий subject до 72 символов и объединяйте связанные изменения.

В Pull Request указывайте краткое описание, команды проверки и связанные issue или документы из `docs/`. Скриншоты нужны только для пользовательских UI-изменений. Отдельно отмечайте новые настройки, эндпоинты и шаги миграции.

## Безопасность и конфигурация

Не коммитьте секреты, локальные учетные данные, сгенерированные логи и содержимое `target/`. Настройки окружения храните вне репозитория, если это не безопасные значения по умолчанию для всех участников.

Секреты также нельзя записывать в спецификации, work logs, handoff, Qwen memory, skills или agent definitions.
