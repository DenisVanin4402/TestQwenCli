# CR002-T007: план подключения OpenAPI code generation к Maven-сборке

## Цель этапа

Подключить `org.openapitools:openapi-generator-maven-plugin` к Maven lifecycle модуля `test-qwen-cli-app`, чтобы синхронизированные OpenAPI YAML из `src/main/resources/openapi` генерировали компилируемые Java sources во время сборки.

## Выбранный подход

Этап выполняется как build-time интеграция генератора без изменения production-контроллеров, DTO, сервисов и публичного HTTP-поведения.

Выбран минимальный вариант:

- generator: `spring`, потому что CR002 готовит серверный OpenAPI contract для Spring Boot gateway;
- plugin version: `7.22.0`, актуальная версия из официальной документации OpenAPI Generator на момент этапа;
- generation phase: стандартная `generate-sources`;
- input specs: только рабочие YAML из `test-qwen-cli-app/src/main/resources/openapi`;
- output: `target/generated-sources/openapi/<spec-name>`, без коммита сгенерированного кода;
- package names: отдельные пакеты для sync, async и callback, чтобы одноименные модели вроде `ErrorResponse` не конфликтовали между спецификациями;
- generated role на этом этапе: компилируемые API interfaces и model-классы как build artifact. Их использование приложением остается scope CR002-T008.

Такой подход соответствует ADR-012: генератор подключается к сборке, но публичное поведение API не меняется ради генерации.

## Затронутые файлы и границы компонентов

Планируемые изменения:

- обновить `test-qwen-cli-app/pom.xml`;
- добавить три execution `openapi-generator-maven-plugin` для `external-gateway-sync.yaml`, `external-gateway-async.yaml`, `external-gateway-callback.yaml`;
- настроить отдельные `apiPackage`, `modelPackage`, `invokerPackage` и `configPackage` для каждого YAML;
- оставить generated sources в `target/generated-sources/openapi`;
- обновить `execution-progress.md`;
- создать `review_T007.md` после senior architect review.

Не планируются изменения:

- ручные контроллеры `ExternalSyncController` и `ExternalAsyncController`;
- DTO из `gateway.model.*`;
- сервисы, repositories, callback client и scheduler flow;
- OpenAPI YAML по содержанию, кроме случаев, когда генератор выявит формально некорректный контракт;
- architecture docs, если этап не изменит runtime-границы или операционные договоренности.

Границы gateway, dashboard, persistence, callback delivery и async queue не меняются.

## Публичные контракты

Публичное HTTP-поведение не меняется. Этап не подключает generated interfaces к контроллерам и не заменяет существующие DTO.

Рабочим входом генератора являются YAML из `test-qwen-cli-app/src/main/resources/openapi`; зеркальные копии в `docs/external-service-gateway/openapi` остаются под byte-for-byte проверкой из T006.

## Data, State, Deployment, Operations

Data/state модель не меняется: таблицы, статусы задач, callback delivery и lease-слоты не затрагиваются.

Runtime deployment не меняется. Build pipeline меняется: Maven должен загружать и выполнять `openapi-generator-maven-plugin` на фазе `generate-sources`, а generated sources должны компилироваться в рамках обычного `mvn test`.

Операционный риск состоит в увеличении времени сборки и в появлении новой build-time зависимости от OpenAPI Generator. Сгенерированный код не коммитится, поэтому воспроизводимость обеспечивается фиксированной версией plugin и входными YAML в resources.

## Тестовая стратегия

После изменения `pom.xml` выполнить:

```text
mvn -pl test-qwen-cli-app -am test
```

Проверка должна подтвердить:

- OpenAPI generation запускается до компиляции;
- generated sources компилируются на Java 21 и Spring Boot 3/Jakarta imports;
- существующие unit/contract tests продолжают проходить;
- ручные контроллеры не заменены generated-кодом преждевременно.

Если Maven не сможет скачать plugin из-за сетевого ограничения среды, повторить команду с запросом escalated-доступа по правилам sandbox.

## Риски

- Риск конфликтов одноименных generated моделей между sync, async и callback YAML.
- Риск получить generated-код с зависимостями, которых нет в classpath приложения.
- Риск, что OpenAPI 3.1 особенности YAML потребуют точечной корректировки генераторной настройки.
- Риск смешать подключение генератора с рефакторингом контроллеров, расширив scope T007.
- Риск включить generated controllers как Spring beans и изменить runtime mapping.

Снижение рисков: отдельные package names и output directories, `interfaceOnly`, `useSpringBoot3`, отсутствие refactoring-правок до T008, проверка через Maven lifecycle.

## Критерии отката

Откат этапа означает удалить конфигурацию `openapi-generator-maven-plugin` из `test-qwen-cli-app/pom.xml`, удалить `plan_T007.md`, `review_T007.md` и записи T007 из `execution-progress.md`. Сгенерированный каталог в `target/` является build artifact и может быть пересоздан или удален Maven clean без ручной поддержки.
