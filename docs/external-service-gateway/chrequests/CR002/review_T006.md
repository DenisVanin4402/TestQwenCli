# CR002-T006: senior architect review

## Итог

Этап CR002-T006 соответствует `work-items.md`, `plan_T006.md` и ADR-012. Синхронизированные OpenAPI YAML перенесены в `test-qwen-cli-app/src/main/resources/openapi`, а `docs/external-service-gateway/openapi` оставлен как документационное зеркало с тестовой защитой от drift.

Блокирующих замечаний нет. Production-код, `pom.xml`, runtime flow, persistence state и публичное HTTP-поведение на этапе не менялись.

## Соответствие плану

Проверены `work-items.md`, `plan_T006.md`, `execution-progress.md`, ADR-009, ADR-012, `docs/external-service-gateway/architecture/README.md`, фактический diff и затронутые файлы:

- `test-qwen-cli-app/src/main/resources/openapi/external-gateway-sync.yaml`;
- `test-qwen-cli-app/src/main/resources/openapi/external-gateway-async.yaml`;
- `test-qwen-cli-app/src/main/resources/openapi/external-gateway-callback.yaml`;
- `test-qwen-cli-app/src/test/java/com/example/testqwencli/gateway/controller/ExternalGatewayOpenApiContractTest.java`;
- `docs/external-service-gateway/architecture/README.md`;
- `docs/external-service-gateway/chrequests/CR002/execution-progress.md`.

Реализация соответствует выбранному подходу:

- resources-каталог создан внутри Maven-модуля `test-qwen-cli-app`;
- все три синхронизированные спецификации перенесены в `src/main/resources/openapi`;
- `ExternalGatewayOpenApiContractTest` читает рабочий OpenAPI-вход из `test-qwen-cli-app/src/main/resources/openapi` при запуске из корня репозитория и из модуля;
- legacy fallback на `docs/openapi` не возвращен;
- добавлен тест `resourceOpenApiDocumentsMatchDocumentationMirror`, который сравнивает resources-копии и docs-копии byte-for-byte;
- архитектурная карта явно различает рабочий вход сборки и документационное зеркало.

В `execution-progress.md` зафиксированы проверки:

```text
mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn test
```

Результат по журналу: точечный OpenAPI-тест выполнил 3 теста, полный `mvn test` выполнил 116 тестов; failures/errors/skipped нет. В рамках review команды повторно не запускались.

## Производительность

Runtime overhead не появляется: приложение не меняет контроллеры, сервисы, repositories, schedulers или callback flow.

Build-time влияние минимальное и ожидаемое для этапа: Maven копирует дополнительные YAML из `src/main/resources` в `target/classes`. Генерация кода, рост generated sources и влияние на время компиляции остаются в scope CR002-T007, на T006 они не подключены.

Тестовая нагрузка увеличилась на один быстрый файловый drift-check в существующем OpenAPI contract test. Проверка линейна по трем YAML-файлам и не добавляет сетевых, Docker или БД-зависимостей.

## Безопасность

Публичный API gateway не расширен. OpenAPI-файлы перенесены как статические ресурсы Maven-модуля, но новые controller mappings или endpoints не добавлены.

Callback SSRF-инвариант ADR-006 не затронут: callback URL по-прежнему не появляется в payload и выбирается runtime-кодом по allow-list. Scope доступа `X-Client-Service`, error contract и service-to-service authentication не менялись.

Секреты, локальные credentials и machine-specific пути в новых YAML/resources и документации не обнаружены.

## Архитектурные приемы

Решение согласовано с ADR-012: рабочий вход для будущей генерации находится внутри Maven-модуля, а наличие второй копии в `docs` компенсировано тестом синхронности.

Разделение contract/domain/persistence сохранено. Этап затрагивает только contract artifacts и contract test, не смешивая перенос YAML с подключением `openapi-generator-maven-plugin` или рефакторингом контроллеров на generated sources.

Обновление `architecture/README.md` уместно: оно фиксирует источник OpenAPI-входа сборки и не меняет C4-границы, sequence flow, data/state модель, deployment model или production-инварианты. Дополнительный ADR не требуется, потому что ADR-012 уже принял это решение.

Остаточные риски остаются в запланированных этапах CR002:

- CR002-T007 должен подключить генератор к тому же resources-каталогу;
- CR002-T009 должен усилить stable contract checks, включая pending note из `review_T005.md`;
- CR002-T010 должен финально проверить, нужны ли еще архитектурные правки после выбора роли generated OpenAPI-кода.

## Замечания

Блокирующих, high/medium/low или note-level замечаний по CR002-T006 нет.

## Рекомендация

Рекомендую закрыть CR002-T006 после human approval без дополнительных правок и переходить к CR002-T007 только по отдельной явной команде пользователя.

## Human approval

Ожидается решение человека:

- закрыть CR002-T006 без дополнительных production-правок;
- подтвердить, что вариант `resources` как рабочий вход и `docs` как проверяемое зеркало принят;
- отдельно подтвердить, когда можно переходить к CR002-T007.
