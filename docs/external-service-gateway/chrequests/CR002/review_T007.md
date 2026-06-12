# CR002-T007: senior architect review

## Итог

Блокирующих архитектурных замечаний нет. CR002-T007 соответствует `work-items.md`, `plan_T007.md`, ADR-012 и текущей карте архитектурной документации.

Фактический diff `test-qwen-cli-app/pom.xml` ограничен build-time подключением `org.openapitools:openapi-generator-maven-plugin` версии `7.22.0` и тремя execution для sync, async и callback OpenAPI YAML. Runtime-контроллеры, DTO, сервисы, persistence, callback flow и публичное HTTP-поведение не изменены.

Проверка учтена: `mvn -pl test-qwen-cli-app -am test` прошел, 116 tests, failures/errors/skipped 0. Предупреждения генератора non-blocking и не ломают компиляцию.

## Соответствие плану

Этап реализует выбранный в `plan_T007.md` подход: генерация подключена только как build-time artifact, без преждевременного refactoring production-контроллеров до CR002-T008.

Подтверждено:

- input specs берутся из `test-qwen-cli-app/src/main/resources/openapi`, что соответствует ADR-012 и `architecture/README.md`;
- generated output направлен в `target/generated-sources/openapi/{sync,async,callback}`;
- sync, async и callback используют разные package names под `com.example.testqwencli.generated.openapi.*`, что снижает риск конфликтов одноименных моделей вроде `ErrorResponse` и `AsyncTaskStatus`;
- включены `spring`, `spring-boot`, `useSpringBoot3`, `interfaceOnly`, `skipDefaultInterface`;
- generated sources компилируются в обычном Maven lifecycle;
- сгенерированный код не коммитится и остается build artifact.

## Производительность

Runtime overhead отсутствует: новые generated классы не подключены к ручным контроллерам и не участвуют в обработке запросов.

Основное влияние - увеличение времени сборки и новая build-time dependency на OpenAPI Generator. Это ожидаемое изменение T007 и оно зафиксировано в плане. Фиксация версии `7.22.0` снижает риск невоспроизводимых генераций.

Non-blocking warnings генератора:

- OpenAPI 3.1 support отмечен как beta;
- free-form `ResultMap` не генерируется как отдельная модель;
- complex example в request body игнорируется.

Эти предупреждения не являются blocker для T007, потому что задача этапа - компилируемая генерация, а не использование generated моделей в runtime. Их нужно учитывать при CR002-T008 и CR002-T009.

## Безопасность

Публичный API не расширен. Новые runtime endpoints, controller beans, callback URLs, authentication/scope rules или SSRF-поверхности не добавлены.

Generated API interfaces содержат Spring mapping-аннотации, но не являются Spring beans: нет `@Controller`, `@RestController`, `@Component` или отдельной регистрации controller classes. Поэтому текущие runtime mappings остаются за ручными контроллерами.

Security-риск T007 ограничен supply-chain аспектом build-time plugin. Версия plugin зафиксирована, новых runtime dependencies в `pom.xml` не добавлено.

## Архитектурные приемы

Решение хорошо удерживает границы этапа:

- OpenAPI generation отделена от domain/persistence/service слоев;
- generated packages изолированы от ручных `gateway.model.*`;
- controller refactoring явно оставлен для CR002-T008;
- ADR-012 соблюден: контроллеры и DTO остаются источником истины, публичное поведение API не меняется ради генерации;
- `architecture/README.md` уже фиксирует рабочий OpenAPI-вход сборки и документационное зеркало, дополнительных архитектурных правок на T007 не требуется.

Выбранный прием с отдельными package names для каждой спецификации оправдан: он предотвращает коллизии моделей между sync, async и callback контрактами и сохраняет возможность поэтапной миграции.

## Замечания

1. severity: note
   ссылка на источник: `docs/external-service-gateway/chrequests/CR002/execution-progress.md`, пункт 27; output OpenAPI Generator; `plan_T007.md`, раздел "Риски"
   риск: предупреждения OpenAPI Generator по OpenAPI 3.1 beta, free-form `ResultMap` и ignored complex example могут стать значимыми на CR002-T008, если generated models будут использоваться как runtime DTO без отдельной сверки.
   предлагаемое действие: не блокировать CR002-T007; при CR002-T008 явно проверить покрытие generated models перед выбором роли generated-кода, а при CR002-T009 добавить стабильные contract checks для частей, где генератор может терять детализацию.
   статус human approval: deferred до CR002-T008/CR002-T009

2. severity: note
   ссылка на источник: `test-qwen-cli-app/pom.xml`, executions `generate-openapi-*-contract`; generated interfaces `V1Api` и `InternalApi` в `target/generated-sources/openapi`
   риск: сейчас generated interfaces не являются controller beans, но при CR002-T008 наивное подключение через `implements` или adapter может создать дублирование mappings или незаметно изменить request mapping metadata.
   предлагаемое действие: не менять T007; в CR002-T008 выбрать один явный способ подключения generated interfaces и подтвердить отсутствие duplicate mappings через controller/OpenAPI contract tests.
   статус human approval: deferred до CR002-T008

## Рекомендация

Закрыть CR002-T007 после human approval без дополнительных правок в коде или документации. Замечания note-level не блокируют этап и должны быть учтены при CR002-T008/CR002-T009 или явно отложены решением человека.

Следующий этап CR002-T008 начинать только после явной команды пользователя.

## Human approval

Статус review: approved.

Решение человека от 2026-06-12: закрыть CR002-T007 без дополнительных правок в коде или документации. Оба note-level замечания не блокируют этап и должны быть учтены при CR002-T008/CR002-T009.
