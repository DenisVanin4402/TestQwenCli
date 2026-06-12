# CR002-T005: план синхронизации callback YAML

## Цель этапа

Привести `docs/external-service-gateway/openapi/external-gateway-callback.yaml` к фактическому контракту callback-доставки, которую gateway отправляет в сервис-клиент после финального статуса async-задачи.

## Выбранный подход

Этап выполняется как точечная синхронизация OpenAPI YAML без изменения production-кода, DTO, Maven-настроек и общей тестовой логики.

Источники истины:

- `CallbackPayload` для сериализуемых полей callback body и допустимых финальных статусов async-задачи;
- `HttpCallbackClient` для HTTP-метода, заголовков и обработки ответов сервиса-клиента;
- `CallbackClientResponse`, `CallbackDeliveryDispatcherImpl`, `CallbackDeliveryPlannerImpl` и callback repository contracts для retry/backoff semantics;
- `HttpCallbackClientTest`, `CallbackDeliveryFlowTest` и `ExternalGatewayOpenApiContractTest` как быстрые проверки callback-сценариев.

Если YAML обещает поведение, которого gateway не реализует, YAML ослабляется до фактического контракта. В частности, gateway считает успешным любой HTTP 2xx, игнорирует body ответа callback endpoint, не использует `Retry-After` и применяет собственные `retryBackoffMs`/`maxAttempts` для любой transport/runtime ошибки или non-2xx ситуации.

## Затронутые файлы и границы компонентов

Планируемые изменения:

- `docs/external-service-gateway/openapi/external-gateway-callback.yaml`;
- `docs/external-service-gateway/chrequests/CR002/execution-progress.md`;
- `docs/external-service-gateway/chrequests/CR002/review_T005.md` после senior architect review.

Не планируются изменения:

- `CallbackPayload`, `HttpCallbackClient`, callback dispatcher/planner и repositories;
- sync/async YAML;
- `pom.xml`, generated sources и перенос YAML в resources;
- архитектурные документы.

Границы gateway, async queue, callback delivery queue и dashboard не меняются. YAML по-прежнему описывает endpoint сервиса-клиента, а не endpoint самого gateway.

## Публичные контракты

Публичное HTTP-поведение gateway не меняется. Callback YAML должен описывать фактический исходящий вызов:

- `POST /internal/external-gateway/callbacks` как рекомендуемый allow-listed endpoint сервиса-клиента;
- обязательный `X-Callback-Attempt`;
- optional `X-Request-Id`, который gateway отправляет только если request id не `null` и не blank;
- JSON body `ExternalGatewayCallback` с полями `eventId`, `taskId`, `externalId`, `clientService`, `status`, `result`, `error`, `finishedAt`;
- допустимые значения `status`: `DONE`, `FAILED`, `DEAD`, `CANCELLED`;
- `result` присутствует для `DONE`, `error` присутствует для остальных финальных статусов;
- `finishedAt` соответствует фактической JSON-сериализации `Instant` в callback HTTP-клиенте.

Ответ callback endpoint интерпретируется gateway так:

- любой HTTP 2xx считается успешной доставкой;
- body и headers ответа не используются;
- любая runtime/transport ошибка или non-2xx приводит к retry по внутренним настройкам callback delivery, пока не исчерпан `maxAttempts`.

## Data, State, Deployment, Operations

Этап не меняет data/state модель: `ext_callback_delivery`, статусы callback-доставки, claim/retry/dead flow и связь с async task остаются прежними.

Deployment, operations и Maven lifecycle не меняются. Перенос YAML в resources и генерация кода выполняются позже в T006-T007.

## Тестовая стратегия

После YAML-правки выполнить точечную проверку OpenAPI и callback-сценариев:

```text
mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest,HttpCallbackClientTest,CallbackDeliveryFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Если точечная проверка покажет drift в общих gateway контрактах, дополнительно запустить более широкий `mvn -pl test-qwen-cli-app test`.

## Риски

- Риск оставить `finishedAt` как `date-time`, хотя текущий HTTP callback client сериализует `Instant` числом.
- Риск пообещать, что gateway читает `CallbackAck`, `ErrorResponse` или `Retry-After`, хотя фактически body и headers ответа не используются.
- Риск описать retry только для отдельных HTTP-кодов, хотя dispatcher одинаково обрабатывает non-2xx/exception на уровне доставки.
- Риск смешать callback endpoint сервиса-клиента с gateway endpoints из sync/async YAML.

Снижение рисков: сверять YAML с `CallbackPayload`, `HttpCallbackClient` и callback flow tests, а после правки запускать точечные тесты.

## Критерии отката

Откат этапа означает возврат изменений в `external-gateway-callback.yaml`, удаление `plan_T005.md`, `review_T005.md` и записей T005 из `execution-progress.md`. Production-код на этапе не меняется.
