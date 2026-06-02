# Примеры для change.md

Этот файл — каталог примеров заполнения для каждого из 13 разделов `change.md`. Примеры построены на сквозном сценарии «добавить систему промокодов в `order-service`».

**Как использовать**: при создании `change.md` не копируй примеры в результат. Используй их как ориентир формата и полноты. Все реальные значения должны быть взяты из контекста конкретного изменения. Полный готовый пример без плейсхолдеров лежит в `example-change.md` (рядом с этим файлом).

---

## 1. Предложение

### Цель изменения

Добавить систему промокодов в `order-service`, чтобы покупатели могли применять скидочные коды при оформлении заказа. Это позволит маркетингу запускать акции и увеличить конверсию оформления заказов на 15%.

### Инициатор

Команда маркетинга, тикет SHOP-1234. Проблема: нет механизма применения промокодов — акции приходится проводить вручную через изменение цен в каталоге.

### Затронутые компоненты

- [x] `order-service` — применение промокода при оформлении заказа, пересчёт суммы
- [x] `payment-service` — передача итоговой суммы со скидкой при оплате
- [ ] `notification-service` — отправка письма с деталями скидки (отдельный CR)

---

## 2. Бизнес-логика — правила применения промокода

1. При оформлении заказа клиент МОЖЕТ передать поле `promoCode` в запросе.

2. **ЕСЛИ** поле `promoCode` не передано или пустое, **ТОГДА** заказ оформляется по обычным ценам без скидки.

3. **ЕСЛИ** `promoCode` передан, **ТОГДА** `order-service` проверяет промокод в таблице `promo_codes`:
   - **ЕСЛИ** промокод не найден, **ТОГДА** вернуть ошибку `400` с сообщением «Промокод не найден».
   - **ЕСЛИ** промокод найден, но `valid_until < now()`, **ТОГДА** вернуть ошибку `400` «Срок действия промокода истёк».
   - **ЕСЛИ** промокод найден, но `usage_count >= max_usages`, **ТОГДА** вернуть ошибку `400` «Промокод больше не действует».
   - **ЕСЛИ** промокод валиден, **ТОГДА** применить скидку (см. правила расчёта ниже).

4. **ЕСЛИ** `discount_type = 'PERCENT'`, **ТОГДА** скидка = `total_amount * discount_value / 100`.
   **ЕСЛИ** `discount_type = 'FIXED'`, **ТОГДА** скидка = `discount_value` (в рублях).

5. **ЕСЛИ** рассчитанная скидка больше суммы заказа, **ТОГДА** скидка ограничивается суммой заказа (итого = 0, но не отрицательное).

6. После успешного применения:
   - `usage_count` в `promo_codes` увеличивается на 1;
   - в заказе сохраняется `promo_code_id` и `discount_amount`;
   - событие `ORDERS.CREATED` в Kafka содержит поля `promoCode` и `discountAmount`.

---

## 6. Модели данных — ADDED

### Вариант A: API / JSON-схема

| Сущность / Поле | Тип данных | Обязательность | По умолчанию | Пример | Обоснование |
|-----------------|-----------|----------------|--------------|--------|-------------|
| `Order.promoCode` | string | optional | null | `"SUMMER2026"` | Промокод, применённый к заказу |
| `Order.discountAmount` | decimal | optional | 0.00 | 150.00 | Сумма скидки по промокоду (в рублях) |
| `Order.finalAmount` | decimal | required | — | 1350.00 | Итоговая сумма заказа после скидки |

### Вариант B: таблица БД (PostgreSQL DDL)

```sql
CREATE TABLE promo_codes (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(50)   NOT NULL UNIQUE,
    discount_type  VARCHAR(10)   NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
    discount_value NUMERIC(10,2) NOT NULL,
    valid_from     TIMESTAMP     NOT NULL DEFAULT now(),
    valid_until    TIMESTAMP     NOT NULL,
    max_usages     INT           NOT NULL DEFAULT 1000,
    usage_count    INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_promo_codes_code ON promo_codes (code);
```

| Таблица / Колонка | Тип | PK / UNIQUE | Описание |
|-------------------|-----|-------------|----------|
| `promo_codes.id` | BIGSERIAL | PK | Идентификатор |
| `promo_codes.code` | VARCHAR(50) | UNIQUE | Текст промокода |
| `promo_codes.discount_type` | VARCHAR(10) | — | `PERCENT` / `FIXED` |
| `promo_codes.discount_value` | NUMERIC(10,2) | — | Значение скидки |
| `promo_codes.valid_until` | TIMESTAMP | — | Дата окончания действия |
| `promo_codes.max_usages` | INT | — | Максимум использований |
| `promo_codes.usage_count` | INT | — | Текущее количество |

### Вариант C: Avro-схема

```json
{
  "type": "record",
  "name": "OrderCreatedEvent",
  "namespace": "com.shop.orders",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "userId", "type": "string"},
    {"name": "totalAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}},
    {"name": "promoCode", "type": ["null", "string"], "default": null},
    {"name": "discountAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}},
    {"name": "createdAt", "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

### Вариант D: Protobuf

```protobuf
message PromoCodeValidation {
  string code = 1;
  string discount_type = 2;
  double discount_value = 3;
  bool   is_valid = 4;
  optional string rejection_reason = 5;
}
```

## 6. Модели данных — MODIFIED

### API

| Сущность / Поле | Было | Стало | Обоснование |
|-----------------|------|-------|-------------|
| `CreateOrderRequest.items` | array, required | array, required (без изменений) | — |
| `CreateOrderResponse.totalAmount` | decimal, required | переименовано в `originalAmount` | Различие между суммой до и после скидки |

### DDL

```sql
ALTER TABLE orders ADD COLUMN promo_code_id   BIGINT REFERENCES promo_codes(id);
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0;
```

| Таблица / Колонка | Было | Стало | Обоснование |
|-------------------|------|-------|-------------|
| `orders` | без `promo_code_id` | `+ promo_code_id BIGINT (FK → promo_codes)` | Связь заказа с промокодом |
| `orders` | без `discount_amount` | `+ discount_amount NUMERIC(10,2) DEFAULT 0` | Сумма скидки для отчётности |

## 6. Модели данных — REMOVED

| Сущность / Поле | Причина удаления | Миграция |
|-----------------|------------------|----------|
| `Order.manualDiscount` | Заменено системой промокодов | Прекратить использование; существующие заказы остаются как есть |
| `orders.legacy_coupon_code` (колонка) | Старая система купонов выведена из эксплуатации | `DROP COLUMN` после подтверждения, что отчёты не используют поле |

---

## 7. Интеграции — ADDED

### REST

#### `POST /api/v1/orders/{orderId}/apply-promo` — Применение промокода

- **Описание**: Проверяет промокод и применяет скидку к указанному заказу.
- **Request**:
  ```json
  { "promoCode": "SUMMER2026" }
  ```
- **Response (200)**:
  ```json
  {
    "orderId": "ord-123",
    "promoCode": "SUMMER2026",
    "discountType": "PERCENT",
    "discountValue": 10,
    "discountAmount": 150.00,
    "originalAmount": 1500.00,
    "finalAmount": 1350.00
  }
  ```
- **Таймаут**: 5000 мс
- **Retry**: нет (клиент повторяет вручную)

### Kafka

#### `ORDERS.CREATED` (обновлённый producer)

- **Описание**: Событие о создании заказа теперь включает информацию о промокоде.
- **Направление**: producer
- **Формат**: JSON (схема `OrderCreatedEvent`)
- **Таймаут**: 5000 мс (`delivery.timeout.ms`)
- **Retry**: встроенный retry Kafka producer

### gRPC

#### `PaymentService.CreatePayment`

```protobuf
service PaymentService {
  rpc CreatePayment (CreatePaymentRequest) returns (CreatePaymentResponse);
}

message CreatePaymentRequest {
  string order_id = 1;
  double amount   = 2;
  string currency = 3;
}

message CreatePaymentResponse {
  string payment_id   = 1;
  string status       = 2;
  string redirect_url = 3;
}
```

- **Таймаут**: 10 000 мс
- **Retry**: 2 попытки, backoff 1 с / 3 с

### SSE

#### `GET /api/v1/orders/{orderId}/status-stream`

- **Описание**: Клиент подписывается на обновления статуса заказа в реальном времени.
- **Request**: `GET ...` с `Accept: text/event-stream`
- **Response**:
  ```
  event: ORDER_STATUS_CHANGED
  id: evt-001
  data: {"orderId": "ord-123", "status": "PAID", "updatedAt": "2026-04-03T12:00:00Z"}

  event: ORDER_STATUS_CHANGED
  id: evt-002
  data: {"orderId": "ord-123", "status": "SHIPPED", "updatedAt": "2026-04-03T14:30:00Z"}
  ```
- **Таймаут**: 300 000 мс (5 минут, затем переподключение)
- **Reconnect**: клиент переподключается с `Last-Event-ID`

### Cron

#### `expired-promo-codes-cleanup` — деактивация просроченных промокодов

- **Описание**: периодическая пометка просроченных промокодов как неактивных.
- **Расписание**: `0 0 1 * * *` (ежедневно в 01:00)
- **Окно**: 60 секунд
- **Max retry**: 3 попытки на batch
- **Backoff**: 5000 мс между попытками

## 7. Интеграции — MODIFIED

#### `POST /api/v1/orders` — Создание заказа

- **Что изменилось**: в `request` добавлено опциональное поле `promoCode`, в `response` — поля `discountAmount` и `finalAmount`.
- **Request (было)**: `{ "items": [...], "deliveryAddress": "..." }`
- **Request (стало)**: `{ "items": [...], "deliveryAddress": "...", "promoCode": "SUMMER2026" }` — `promoCode` опциональное
- **Response (было)**: `{ "orderId": "...", "totalAmount": 1500.00, "status": "CREATED" }`
- **Response (стало)**: `{ "orderId": "...", "originalAmount": 1500.00, "discountAmount": 150.00, "finalAmount": 1350.00, "promoCode": "SUMMER2026", "status": "CREATED" }`

---

## 8. Обработка ошибок

| Код ошибки | HTTP-статус | errorCode | Ситуация | Действие клиента |
|-----------|-------------|-----------|----------|------------------|
| 1003 | 400 | `INVALID_PROMO_CODE` | Промокод не найден в базе | Проверить правильность введённого кода |
| 1003 | 400 | `PROMO_CODE_EXPIRED` | Срок действия промокода истёк | Использовать другой промокод |
| 1003 | 400 | `PROMO_CODE_LIMIT_REACHED` | Промокод использован максимум раз | Использовать другой промокод |
| 1003 | 400 | `INVALID_INPUT` | Некорректный формат запроса | Исправить тело запроса согласно схеме |
| 1001 | 500 | `INTERNAL_ERROR` | Ошибка БД или Kafka | Повторить запрос через несколько секунд |

Формат ответа об ошибке (RFC 7807):

```json
{
  "type": "https://api.shop.com/problems/bad-request",
  "title": "Промокод не найден",
  "detail": "Промокод WINTER2025 не существует или был удалён",
  "errorCode": "INVALID_PROMO_CODE",
  "status": 400,
  "instance": "/api/v1/orders/ord-123/apply-promo"
}
```

---

## 9. Хедеры и метаданные

### HTTP

| Операция | Транспорт | Имя | Направление | Обязательность | Формат | Пример | Назначение |
|----------|-----------|-----|-------------|----------------|--------|--------|-----------|
| ADDED | HTTP | `X-Promo-Applied` | response | optional | boolean | `true` | Признак применённого промокода |
| ADDED | HTTP | `X-Discount-Amount` | response | optional | decimal | `150.00` | Сумма скидки для клиентского логирования |
| MODIFIED | HTTP | `X-Request-ID` | request | required (было optional) | UUID | `550e8400-...` | Трассировка запросов |

### Kafka

| Операция | Транспорт | Имя | Направление | Обязательность | Формат | Пример | Назначение |
|----------|-----------|-----|-------------|----------------|--------|--------|-----------|
| ADDED | Kafka | `ORDER_SOURCE` | producer header | optional | string | `"WEB"` / `"MOBILE"` / `"API"` | Канал оформления заказа |
| ADDED | Kafka | `PROMO_APPLIED` | producer header | optional | string | `"true"` / `"false"` | Был ли применён промокод |

### gRPC metadata

| Операция | Транспорт | Имя | Направление | Обязательность | Формат | Пример | Назначение |
|----------|-----------|-----|-------------|----------------|--------|--------|-----------|
| ADDED | gRPC | `x-order-id` | request metadata | required | string | `"ord-123"` | Идентификатор заказа для платёжного сервиса |

### SSE event fields

| Операция | Транспорт | Имя | Направление | Обязательность | Формат | Пример | Назначение |
|----------|-----------|-----|-------------|----------------|--------|--------|-----------|
| ADDED | SSE | `event: ORDER_STATUS_CHANGED` | response | required | string | `ORDER_STATUS_CHANGED` | Событие изменения статуса |
| ADDED | SSE | `retry:` | response | optional | int (ms) | `5000` | Интервал переподключения клиента |

### MCP

| Операция | Транспорт | Имя | Направление | Обязательность | Формат | Пример | Назначение |
|----------|-----------|-----|-------------|----------------|--------|--------|-----------|
| ADDED | MCP/HTTP | `MCP-Protocol-Version` | request | required | string | `2025-06-18` | Версия протокола MCP |
| ADDED | MCP/HTTP | `x-agent-id` | request | required | string | `"order-agent"` | Идентификатор вызываемого агента |

---

## 10. Валидация входящих значений

| Поле (JSON path) | Тип валидации | Правило | Сообщение об ошибке | Код ошибки |
|------------------|---------------|---------|---------------------|-----------|
| `$.promoCode` | length | От 3 до 50 символов | «Промокод должен содержать от 3 до 50 символов» | 1003 |
| `$.promoCode` | regex | `^[A-Z0-9_-]+$` | «Только заглавные буквы, цифры, дефис и подчёркивание» | 1003 |
| `$.items` | length | От 1 до 100 элементов | «Заказ должен содержать от 1 до 100 товаров» | 1003 |
| `$.items[*].productId` | format | UUID v4 | «`productId` должен быть в формате UUID v4» | 1003 |
| `$.items[*].quantity` | range | От 1 до 999 | «Количество товара должно быть от 1 до 999» | 1003 |
| `$.deliveryAddress` | length | От 10 до 500 символов | «Адрес доставки должен содержать от 10 до 500 символов» | 1003 |

---

## 11. Влияние на безопасность

- **Авторизация/аутентификация**: без изменений — промокод применяется только для авторизованных пользователей (JWT обязателен).
- **Новые роли/права**: роль `promo-manager` — создание и деактивация промокодов через admin API.
- **Маскирование данных**: промокоды не содержат PII — маскирование не требуется. `$.deliveryAddress` ДОЛЖЕН маскироваться в логах (первые 10 символов + `...`).

---

## 12. Миграция

### Шаги миграции данных

1. Создать таблицу `promo_codes` (см. раздел 6).
2. Добавить колонки `promo_code_id` и `discount_amount` в `orders` (`ALTER TABLE`).
3. Заполнить `discount_amount = 0` для всех существующих заказов (batch `UPDATE`).
4. Загрузить начальный набор промокодов от команды маркетинга (`INSERT`).

### Обратная совместимость API

- Переходный период: 2 спринта (4 недели).
- Поле `promoCode` в запросе — опциональное, старые клиенты работают без изменений.
- Поле `totalAmount` в ответе сохраняется (равно `finalAmount`).
- После окончания переходного периода `totalAmount` помечается `deprecated`.

### План отката

1. Отключить feature flag `shop.promo.enabled=false`.
2. Перезапустить поды `order-service`.
3. Таблицу `promo_codes` и новые колонки в `orders` оставить — не мешают работе.

---

## 13. Логирование (новые / изменённые события)

| Уровень | Код | Событие | Формат сообщения |
|---------|-----|---------|------------------|
| ERROR | 1001 | Ошибка записи использования промокода в БД | `"Failed to increment promo usage for code={}: {}"` |
| WARN | — | Попытка использовать просроченный промокод | `"Expired promo code used: code={}, validUntil={}"` |
| INFO | 3000 | Промокод успешно применён к заказу | `"Promo code applied: orderId={}, code={}, discountAmount={}"` |
| DEBUG | — | Начало валидации промокода | `"Validating promo code: code={}, orderId={}"` |

---

## 14. Мониторинг

### Новая метрика: `shop.orders.promo_applied`

- **Тип**: Counter
- **Описание**: счётчик заказов, к которым успешно применён промокод.

| Тег | Обязательность | Описание | Пример значения |
|-----|----------------|----------|-----------------|
| `promoCode` | обязательно | Текст промокода | `"SUMMER2026"` |
| `discountType` | обязательно | Тип скидки | `"PERCENT"` / `"FIXED"` |

**Пример серии:**

```
shop_orders_promo_applied{promoCode="SUMMER2026", discountType="PERCENT"} 42
```

### Вычисляемая метрика

| Метрика | Описание | Формула (PromQL) |
|---------|----------|-------------------|
| `shop_promo_usage_rate` | % заказов с промокодом за 5 минут | `(sum(rate(shop_orders_promo_applied[5m])) / sum(rate(shop_orders_created_total[5m]))) * 100` |

---

## 15. Изменения конфигурации — ADDED

| Параметр | Обяз. | Тип | По умолчанию | Описание |
|----------|-------|-----|---------------|----------|
| `shop.promo.enabled` | нет | bool | `false` | Feature flag: включить систему промокодов |
| `shop.promo.max-discount-percent` | нет | int | `50` | Максимальный процент скидки (защита от ошибок ввода) |
| `shop.promo.cleanup-cron` | нет | string | `"0 0 1 * * *"` | Расписание очистки просроченных промокодов |

---

## 16. Рекомендации к критериям приёмки

| # | КОГДА | ТОГДА |
|---|-------|-------|
| 1 | Клиент оформляет заказ на 1500 руб. и передаёт валидный промокод `SUMMER2026` (скидка 10%) | Заказ создан, `discountAmount = 150.00`, `finalAmount = 1350.00`; в Kafka-событии `ORDERS.CREATED` присутствуют `promoCode` и `discountAmount` |
| 2 | Клиент оформляет заказ без промокода | Заказ создан по полной стоимости, `discountAmount = 0`, `finalAmount = totalAmount` |
| 3 | Клиент передаёт несуществующий промокод `FAKEPROMO` | Ответ `400`, `errorCode = INVALID_PROMO_CODE`, заказ НЕ создан |
| 4 | Клиент передаёт промокод с истёкшим `valid_until` | Ответ `400`, `errorCode = PROMO_CODE_EXPIRED`, заказ НЕ создан |
| 5 | Клиент передаёт промокод, у которого `usage_count >= max_usages` | Ответ `400`, `errorCode = PROMO_CODE_LIMIT_REACHED`, заказ НЕ создан |
| 6 | Скидка `FIXED = 2000` руб. больше суммы заказа `1500` руб. | Скидка ограничена суммой заказа: `discountAmount = 1500.00`, `finalAmount = 0.00` |
| 7 | Feature flag `shop.promo.enabled=false` | Поле `promoCode` в запросе игнорируется, заказ создаётся без скидки |
