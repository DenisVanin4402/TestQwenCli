# CR002-T006: план переноса OpenAPI-спецификаций в resources

## Цель этапа

Сделать синхронизированные OpenAPI YAML рабочим входом Maven-модуля `test-qwen-cli-app` через каталог `test-qwen-cli-app/src/main/resources/openapi`.

## Выбранный подход

Этап выполняется как перенос файлов и настройка проверки синхронности без изменения production-кода, публичного HTTP-поведения и Maven lifecycle.

Выбран вариант с двумя копиями спецификаций:

- `test-qwen-cli-app/src/main/resources/openapi` становится рабочим входом Maven-сборки и будущего `openapi-generator-maven-plugin`;
- `docs/external-service-gateway/openapi` остается человекочитаемым зеркалом для архитектурной и проектной документации;
- `ExternalGatewayOpenApiContractTest` читает рабочие спецификации из resources и отдельной проверкой сравнивает их с `docs/external-service-gateway/openapi` byte-for-byte.

Такой вариант согласован с ADR-012: генератор получает стабильный вход внутри Maven-модуля, а сохранение копии в `docs` допустимо только при наличии тестовой защиты от drift.

## Затронутые файлы и границы компонентов

Планируемые изменения:

- создать `test-qwen-cli-app/src/main/resources/openapi`;
- скопировать туда `external-gateway-sync.yaml`, `external-gateway-async.yaml`, `external-gateway-callback.yaml` из `docs/external-service-gateway/openapi`;
- обновить `ExternalGatewayOpenApiContractTest`, чтобы основным каталогом OpenAPI был `test-qwen-cli-app/src/main/resources/openapi`;
- добавить в `ExternalGatewayOpenApiContractTest` проверку синхронности resources-копий и docs-копий;
- обновить карту архитектурных документов в `docs/external-service-gateway/architecture/README.md`;
- обновить `execution-progress.md`;
- создать `review_T006.md` после senior architect review.

Не планируются изменения:

- контроллеры, DTO, сервисы, repositories и callback client;
- `pom.xml` и настройка генерации OpenAPI, потому что это scope CR002-T007;
- сами OpenAPI-схемы по содержанию, кроме механического переноса уже синхронизированных YAML.

Границы gateway, dashboard, persistence, callback delivery и async queue не меняются.

## Публичные контракты

Публичное HTTP-поведение не меняется. Содержимое YAML должно остаться идентичным синхронизированным спецификациям из T003-T005.

После этапа путь `test-qwen-cli-app/src/main/resources/openapi` считается рабочим входом для сборки и будущей генерации. Путь `docs/external-service-gateway/openapi` остается документационным зеркалом, которое не может расходиться с resources без падения теста.

## Data, State, Deployment, Operations

Data/state модель не меняется: таблицы, статусы, callback delivery и async task flow не затрагиваются.

Deployment и operations не меняются на runtime-уровне. Maven по умолчанию включает `src/main/resources` в classpath и packaged artifact, поэтому дополнительных build plugin настроек на T006 не требуется. Подключение генератора и влияние на время сборки рассматриваются в T007.

## Тестовая стратегия

После переноса выполнить точечную проверку:

```text
mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Проверка должна подтвердить:

- generated `/v3/api-docs` сверяется с рабочими YAML из `src/main/resources/openapi`;
- resources-копии и docs-копии всех трех YAML совпадают byte-for-byte;
- отсутствие legacy fallback на `docs/openapi`.

Если точечная проверка выявит связанный drift, исправлять нужно либо resources YAML и docs mirror вместе, либо тестовый resolver, не меняя runtime-код.

## Риски

- Риск создать две независимые копии YAML без защиты от расхождения.
- Риск оставить contract test на старом каталоге `docs/external-service-gateway/openapi`, из-за чего будущий генератор будет читать не то, что проверяет тест.
- Риск смешать перенос с подключением генератора и преждевременно изменить Maven lifecycle.
- Риск обновить документационные ссылки так, что будет непонятно, какой каталог является рабочим входом сборки.

Снижение рисков: тест читает resources как primary и проверяет docs mirror; Maven-настройки не трогаются до T007; в архитектурной карте явно указана роль обоих каталогов.

## Критерии отката

Откат этапа означает удаление `test-qwen-cli-app/src/main/resources/openapi`, возврат `ExternalGatewayOpenApiContractTest` к чтению `docs/external-service-gateway/openapi`, откат правки `architecture/README.md`, удаление `plan_T006.md`, `review_T006.md` и записей T006 из `execution-progress.md`. Production-код на этапе не меняется.
