# CR002-T005: senior architect review

## Итог

Этап CR002-T005 соответствует `work-items.md`, `plan_T005.md` и ADR-012. Изменение ограничено синхронизацией `docs/external-service-gateway/openapi/external-gateway-callback.yaml` с фактическими `CallbackPayload`, `HttpCallbackClient` и callback delivery retry/backoff flow.

Блокирующих замечаний нет. Production-код, Maven-настройки, sync/async YAML и архитектурные документы на этапе не менялись.

Есть одно note-level замечание по проверяемости wire-format callback payload; оно не блокирует закрытие T005 и отложено за пределы текущего CR002 после решения пользователя пропустить CR002-T009.

## Соответствие плану

Проверены `work-items.md`, `plan_T005.md`, `execution-progress.md`, ADR-005, ADR-006, ADR-008, ADR-009, ADR-011, ADR-012, `docs/external-service-gateway/architecture/07-sequence-callback.md`, фактический diff `external-gateway-callback.yaml` и затронутые Java-классы:

- `CallbackPayload`;
- `TaskError`;
- `CallbackClientResponse`;
- `HttpCallbackClient`;
- `CallbackDeliveryDispatcherImpl`;
- `ExternalGatewayOpenApiContractTest`;
- `HttpCallbackClientTest`;
- `CallbackDeliveryFlowTest`.

Реализация соответствует выбранному подходу:

- изменен только callback OpenAPI YAML и CR-журнал/план, production-код не менялся;
- `POST /internal/external-gateway/callbacks` сохранен как контракт endpoint'а сервиса-клиента, а не gateway-контроллера;
- обязательный `X-Callback-Attempt` и optional `X-Request-Id` соответствуют `HttpCallbackClient`: attempt всегда передается, `X-Request-Id` не отправляется при `null` или blank;
- `X-Request-Id` описывает фактический dispatcher path, где в header передается `eventId` текущей callback-попытки;
- response contract заменен на `2XX`/`default`, что соответствует `CallbackClientResponse.successful()` и обработке non-2xx/exception через `markRetryOrDead`;
- из YAML удалены `CallbackAck`, response `ErrorResponse` и `Retry-After`, потому что `HttpCallbackClient` использует `toBodilessEntity()` и dispatcher не читает body/headers ответа;
- `finishedAt` описан как `number/double` Unix epoch seconds, что соответствует текущей проверке сериализации в `HttpCallbackClientTest`;
- `ResultMap` допускает `string` и `null` values, что соответствует `CallbackPayload.fromTask`: значения успешного результата преобразуются через `Objects.toString(value, null)`;
- `clientService`, `CallbackError.code` и `CallbackError.message` получили `minLength: 1`, что отражает constructor checks на non-blank на уровне минимального OpenAPI-ограничения;
- финальные значения `status` ограничены `DONE`, `FAILED`, `DEAD`, `CANCELLED`, как в `CallbackPayload.fromTask`.

В `execution-progress.md` зафиксированы проверки:

```text
mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest,HttpCallbackClientTest,CallbackDeliveryFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn test
```

Результат по журналу: 17 точечных тестов и 115 тестов полного `mvn test` выполнены успешно без failures/errors/skipped. В рамках review команды не перезапускались.

## Производительность

Этап меняет только OpenAPI YAML и CR-документы. Runtime path callback-доставки, batch dispatch, executor, retry/backoff, persistence, locks, polling и recovery не затронуты.

Maven lifecycle и generated sources не изменены, поэтому дополнительного runtime overhead, роста времени сборки или риска неограниченного generated code на T005 нет. Остаточный performance-риск по будущей генерации остается в scope T006-T009.

## Безопасность

Публичное HTTP-поведение gateway не расширено. YAML по-прежнему описывает исходящий callback-контракт сервиса-клиента, а не новый входящий endpoint gateway.

SSRF-инвариант ADR-006 сохранен: callback URL не появляется в payload и выбирается gateway по allow-list `clientService`. Service-to-service authentication и подпись callback остаются вне scope CR002, как указано в `work-items.md`.

Ослабление response contract до `2XX`/`default` не снижает безопасность gateway: оно фиксирует фактическое поведение, где любые non-2xx, transport error или runtime error считаются неуспешной попыткой и проходят через внутренний retry/dead flow. Удаление `Retry-After` также корректно, потому что gateway его не использует и не должен обещать клиентам управлять backoff через response header.

Секреты, локальные credentials и machine-specific пути в diff не обнаружены.

## Архитектурные приемы

Решение согласовано с ADR-012: при расхождении YAML с фактическим HTTP-контрактом исправляется YAML, а не runtime-код. Разделение sync/async/callback OpenAPI по ADR-009 сохранено.

Callback flow согласован с ADR-005 и `07-sequence-callback.md`: gateway выполняет at-least-once доставку, успешным считается HTTP 2xx, ошибки доставки не меняют upstream-статус async-задачи, а retry/backoff относится к `ext_callback_delivery`.

Границы компонентов, data/state модель, deployment/operations и production-инварианты T005 не меняет. Отдельные правки архитектурной документации на этом этапе не обязательны; финальная проверка архитектурных документов остается в scope CR002-T010.

Остаточные архитектурные риски:

- `ExternalGatewayOpenApiContractTest` для callback пока проверяет наличие required/properties у `ExternalGatewayCallback`, но не проверяет тип `finishedAt`, `ResultMap.additionalProperties`, response wildcard `2XX`/`default` и header constraints. Это ожидаемо для текущего этапа и должно закрываться в CR002-T009.
- В `review_T004.md` есть note по ADR-008 о `Map<String, String>` vs фактической async read-модели. На финальном закрытии CR002 он отложен за пределы текущего CR002; для callback YAML `string|null` values отражают текущий `CallbackPayload.fromTask`.

## Замечания

1. severity: `note`
   ссылка: `test-qwen-cli-app/src/test/java/com/example/testqwencli/gateway/controller/ExternalGatewayOpenApiContractTest.java`, тест `callbackOpenApiDocumentMatchesSerializedCallbackPayload`; `test-qwen-cli-app/src/test/java/com/example/testqwencli/gateway/client/HttpCallbackClientTest.java`; `docs/external-service-gateway/openapi/external-gateway-callback.yaml`, schema `ExternalGatewayCallback.finishedAt`
   риск: YAML теперь фиксирует `finishedAt` как `number/double` epoch seconds, но текущий OpenAPI contract test для callback проверяет только наличие полей, а не JSON-типы и nested constraints. Если сериализация `Instant` в Spring-context callback path изменится, drift может не быть пойман быстрым contract test.
   предлагаемое действие: после human approval учесть в CR002-T009: расширить callback contract checks на тип `finishedAt`, `ResultMap` values, headers `X-Callback-Attempt`/`X-Request-Id` и response keys `2XX`/`default`; production-код в рамках T005 не менять.
   статус human approval: `deferred` с 2026-06-13. Причина: CR002-T009 пропущен по решению пользователя, а усиление callback contract checks не выполняется в текущем CR002-проходе.

## Рекомендация

CR002-T005 закрыт без production-правок. Note-level замечание по проверяемости callback wire-format отложено за пределы текущего CR002, потому что CR002-T009 пропущен по решению пользователя.

После закрытия T005 остановиться и не начинать CR002-T006 до явной команды пользователя.

## Human approval

Статус review: approved.

Решение человека:

- CR002-T005 закрыт без дополнительных правок production-кода;
- note-level замечание по усилению callback contract checks отложено за пределы текущего CR002 с 2026-06-13;
- переход к следующим этапам CR002 выполнен по отдельным командам пользователя.
