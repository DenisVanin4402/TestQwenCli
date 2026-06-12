# CR002-T004: senior architect review

## Итог

Этап CR002-T004 в целом соответствует `work-items.md`, `plan_T004.md` и ADR-012. Изменение ограничено синхронизацией `docs/external-service-gateway/openapi/external-gateway-async.yaml` с `ExternalAsyncController` и async DTO без production-кода.

Блокирующих замечаний нет. Есть два note-level замечания, которые требуют human approval и не расширяют scope T004 автоматически.

## Соответствие плану

Проверены `work-items.md`, `plan_T004.md`, `execution-progress.md`, ADR-007, ADR-008, ADR-009, ADR-011, ADR-012, фактический diff и затронутые Java-классы:

- `ExternalAsyncController`;
- `ExternalAsyncRequest`;
- `AsyncSubmitResponse`;
- `AsyncTask`;
- `TaskError`;
- `AsyncDeliveryMode`, `AsyncPriority`, `AsyncTaskStatus`, `CallbackDeliveryStatus`;
- `ExternalGatewayExceptionHandler` и `ErrorResponse`.

Реализация соответствует выбранному подходу:

- изменен async YAML, production-код не менялся;
- `POST /v1/external/async`, `GET /v1/external/async/{taskId}`, `DELETE /v1/external/async/{taskId}`, `GET /v1/external/async/by-external-id/{externalId}` и `POST /v1/external/async/{taskId}/retry` сохранены с фактическими `operationId`;
- optional headers `X-Request-Id` и `X-Client-Service` отражены в YAML, включая временный характер `X-Client-Service` и отсутствие client scope при непереданном header;
- response statuses `200`, `202`, `400`, `404`, `409` соответствуют контроллеру и обработанным gateway exceptions;
- `ExternalAsyncRequest.payload` и `ResultMap` описаны как открытые JSON object/map, что соответствует `Map<String, Object>` в async DTO;
- для submit API выделена `ExternalAsyncDeliveryMode` только с `CALLBACK` и `POLLING`, поэтому `SYNC` не документируется как допустимое входное значение async request;
- `callbackDeliveryStatus`, `statusUrl`, `AsyncTask.clientService`, `TaskError.code` и `TaskError.message` приведены к constructor/nullability constraints.

В `execution-progress.md` зафиксирован запуск:

```text
mvn -pl test-qwen-cli-app -am "-Dtest=ExternalGatewayOpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Результат по журналу: 2 теста выполнены успешно, failures/errors/skipped нет. В рамках review команда не перезапускалась, чтобы не создавать дополнительные артефакты за пределами `review_T004.md`.

## Производительность

Этап меняет только OpenAPI YAML. Runtime path submit/read/cancel/retry, dispatcher, queue claim, callback delivery, persistence queries, locks и polling не затронуты.

Maven lifecycle, generated sources и code generation не изменены, поэтому runtime overhead и рост времени сборки на T004 отсутствуют. Остаточный performance-риск по полноте contract checks и генерации кода остается на T006-T009.

## Безопасность

Публичные endpoints и runtime validation не расширены. Удаление `additionalProperties: false` и перевод `payload/result` к `Map<String, Object>` не меняют поведение приложения, а фиксируют фактическую Java-модель.

`X-Client-Service` описан как временный optional scope доступа в соответствии с ADR-007: если header передан, read/cancel/retry ограничиваются client scope; если не передан, текущая реализация lookup не ограничивает. Это сохраняет известный security debt до service-to-service identity и не маскирует его в контракте.

SSRF-защита через отсутствие произвольного `callbackUrl` в async request сохранена: YAML не добавляет callback URL в body, а callback endpoint остается allow-list решением по ADR-006. Секреты и локальные credential values в diff не обнаружены.

## Архитектурные приемы

Решение согласовано с ADR-012: при расхождении YAML с фактическими контроллерами и DTO исправляется YAML, а не runtime-код. Разделение OpenAPI на sync, async и callback по ADR-009 и REST/OpenAPI v1 по ADR-011 сохраняются.

Границы gateway, dashboard и callback-компонентов не меняются. Data/state модель, deployment/operations, Maven lifecycle и production-инварианты T004 не затрагивает, поэтому отдельные архитектурные правки на этом этапе не обязательны.

Остаточные архитектурные риски:

- `ExternalGatewayOpenApiContractTest` пока не проверяет `additionalProperties`, property-level enum split и все nested schema constraints; это уже находится в scope CR002-T009.
- ADR-008 описывает успешный async fallback GET result как `Map<String, String>`, тогда как текущая read-модель `AsyncTask.result` и синхронизированный YAML фиксируют `Map<String, Object>`. Это не блокирует T004 из-за ADR-012, но требует явного решения на T010 или в отдельном human-approved follow-up.

## Замечания

1. severity: `note`
   ссылка: `docs/external-service-gateway/architecture/decisions.md`, ADR-008; `docs/external-service-gateway/openapi/external-gateway-async.yaml`, schema `ResultMap`; `AsyncTask.result`
   риск: архитектурная документация ADR-008 по-прежнему говорит о `Map<String, String>` для async fallback GET, а T004 фиксирует фактический `Map<String, Object>`. Для потребителей это может выглядеть как конфликт между ADR и OpenAPI.
   предлагаемое действие: после human approval решить на CR002-T010, нужно ли обновить ADR/architecture docs под фактическую модель или запланировать отдельное изменение production-кода для нормализации async result к `Map<String, String>`.
   статус human approval: `pending`

2. severity: `note`
   ссылка: `docs/external-service-gateway/openapi/external-gateway-async.yaml`, schema `AsyncDeliveryMode`; `PostgresAsyncTaskRepository.findStoredByTaskId`; `MemoryAsyncTaskRepository.findStoredByTaskId`
   риск: read-схема `AsyncTask.deliveryMode` допускает `SYNC`, потому что Java enum и общая read-модель содержат sync trace mode. При этом async endpoints фактически фильтруют `SYNC` trace-строки и возвращают только `CALLBACK`/`POLLING`. Документированный response enum шире наблюдаемого поведения внешнего async API.
   предлагаемое действие: не менять T004 без решения человека. На T009 или T010 решить, оставлять ли общий `AsyncDeliveryMode` в OpenAPI ради соответствия DTO/generated schema или ввести отдельную response-схему для внешних async endpoints с `CALLBACK`/`POLLING`.
   статус human approval: `pending`

## Рекомендация

Рекомендую закрыть CR002-T004 после human approval без production-правок. Note-level замечания перенести в решение T009/T010 или отдельный follow-up; они не требуют немедленной доработки async YAML в рамках T004.

После закрытия T004 остановиться и не начинать CR002-T005 до явной команды пользователя.

## Human approval

Ожидается решение человека:

- закрыть CR002-T004 с двумя note-level замечаниями;
- принять, отклонить или отложить каждое замечание;
- отдельно подтвердить, когда можно переходить к CR002-T005.
