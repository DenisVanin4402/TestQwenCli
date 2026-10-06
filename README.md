# invest-pro

Первый этап — POC подготовки запроса статуса платежа через усечённый ACL 1.6. Пользователь проверяет сводку и отдельно подтверждает отправку. SSM выполняет один вызов WorkflowManager; успешный commit завершает агентскую сессию.

Java 21, Maven 3.9.x, Spring Boot 3.5.15. Целевая БД — PostgreSQL; для локального запуска доступна файловая H2. Схемой управляет Liquibase XML, состояние сохраняет штатный JPA persister Spring Statemachine. API генерируются из OpenAPI, структурное преобразование выполняет MapStruct.

## Проверка и запуск

```powershell
mvn clean verify
java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=poc-local,h2
```

Обычная проверка включает H2, настоящий HTTP, corr-стенд и перезапуск собранного JAR. Полный профиль проверяет PostgreSQL native LOB/rollback/NOWAIT и весь путь ACL → SSM → corr по HTTP, включая отказ, потерю ответа, timeout и сбои до/после commit:

```powershell
$env:POC_TEST_POSTGRES_URL = 'jdbc:postgresql://127.0.0.1:5432/invest_pro_test'
$env:POC_TEST_POSTGRES_USER = 'invest_test'
$env:POC_TEST_POSTGRES_PASSWORD = '<пароль тестовой БД>'
mvn clean verify -Pintegration-tests
```

Тесты требуют отдельную БД. Для рабочего профиля `poc-local,postgres` используются `POC_POSTGRES_URL`, `POC_POSTGRES_USER`, `POC_POSTGRES_PASSWORD`. Локальная H2 хранится в `data/poc.mv.db` и рассчитана на один процесс. После перезапуска восстанавливается последняя сохранённая подготовка. Несовместимые старые checkpoint отклоняются; перенос старых данных не входит в рефакторинг.

Форматирование Java: `mvn spotless:apply`; проверка формата входит в `verify`. Отчёты остаются в `target`. Профиль `bootstrap` запускает каркас без рабочего persistence и не совмещается с профилями БД.

## Пройти сценарий

После запуска откройте [тестовый UI](http://127.0.0.1:8080/test-ui/): запросите статус, проверьте сводку, подтвердите или откажитесь. HTML/CSS/JS находятся в отдельном `agent-ui`; браузер отправляет ходы только зеркалу `POST /local-api/api/v1/ai/agents/invest-pro` в `agent-ui-back`. Действия приходят в suggestions, согласие запрашивается через propose. История живёт на странице; перезагрузка начинает новый диалог без POST. Поле ввода и «Отправить» пока сообщают, что свободный текст не поддерживается. Экран доступен только в `poc-local`.

Клиентские проверки без npm-зависимостей: `node --test agent-ui/src/test/js/session.test.mjs` (Node 22+). Сборка и запуск приложения Node не требуют. [Спецификация этапа UI](docs/stages/02_test_ui/technical_specification.md).

Для ручной проверки основного ACL каталог `GET /local-api/v1/fixtures` возвращает синтетический платёж и готовые metadata. Следующий пример вызывает основной `POST /api/v1/ai/agents/invest-pro`. Дополнительного API сессии нет.

```powershell
$server = 'http://127.0.0.1:8080'
$fixture = (Invoke-RestMethod "$server/local-api/v1/fixtures").fixtures[0]
$sessionId = [guid]::NewGuid().ToString()
$conversationId = [guid]::NewGuid().ToString()
$requestId = [guid]::NewGuid().ToString()
$headers = @{ 'Request-Id' = $requestId; 'Gigachat-Session-Id' = $sessionId }
$body = @{
    message = @{
        version = '1.6'; performative = 'request'
        sender = 'LOCAL_TEST'; receiver = 'invest-pro'
        conversation_id = $conversationId; reply_with = $requestId
        content = @{ action_code = 'status' }
    }
    metadata = $fixture.metadata
}
Invoke-RestMethod -Method Post -Uri "$server/api/v1/ai/agents/invest-pro" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 10)))

$requestId = [guid]::NewGuid().ToString()
$headers['Request-Id'] = $requestId
$body.message.reply_with = $requestId
$body.message.performative = 'accept_propose'
$body.message.content = @{}
$body.Remove('metadata')
Invoke-RestMethod -Method Post -Uri "$server/api/v1/ai/agents/invest-pro" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 10)))
```

`reject_propose` очищает подготовку и возвращает выбор. В AWAITING_CONFIRM допустимы только подтверждение и отказ: повторный `request/status` отклоняется без изменения подготовки, `restart_status` удалён. После отказа новый выбор создаёт новое предложение. Новая операция после COMPLETED требует новой сессии. Первый вход требует полного контекста, последующие могут его опускать; явно переданная подмена отклоняется.

## Настройки приложения

### Локальный файл .env

Для GigaChat подготовлен [.env.example](.env.example); локальный `.env` исключён из Git. В `GIGACHAT_API_KEY` записывается Authorization Key без префикса `Basic`. Авторизация выполняется через OAuth: SDK получает и обновляет access token в памяти. Поле `GIGACHAT_ACCESS_TOKEN` оставлено по запросу, но клиент его не использует; его можно оставить пустым.

Формат файла — Spring/Java properties: `KEY=value`, без `export`, кавычек и комментариев после значения. Пути записываются с `/`. Ключи не передаются аргументами команд. Существующий файл не перезаписывать; для нового checkout можно скопировать `.env.example` в `.env`.

Spring Boot автоматически читает `.env` при запуске **из корня репозитория**:

```powershell
java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=poc-local,h2
```

В IntelliJ IDEA Working directory — корень проекта. Для другого файла укажите в Program arguments `--spring.config.import=classpath:config/gigachat-prompts.yaml,file:/путь/.env[.properties]`. Сохраняйте импорт YAML промптов. Это импорт конфигурации Spring, а не выполнение файла и не изменение системных переменных. Переменные окружения и аргументы командной строки имеют приоритет над импортированными свойствами.

### Клиент GigaChat

В `.env` задайте `GIGACHAT_MODE=gigachat` и заполните `GIGACHAT_API_KEY`. По умолчанию режим `none`: модель не создаётся и ключ не нужен. Включение независимо от БД/corr; сочетание с `bootstrap` запрещено.

| Настройка `.env` | Значение по умолчанию |
|---|---|
| `GIGACHAT_MODEL` | `GigaChat-3-Ultra` |
| `GIGACHAT_BASE_URL` | `https://api.giga.chat/v1` — с префиксом `/v1` |
| `GIGACHAT_AUTH_URL` | `https://ngw.devices.sberbank.ru:9443/api/v2/oauth` |
| `GIGACHAT_SCOPE` | `GIGACHAT_API_PERS`; должен соответствовать проекту API |
| `GIGACHAT_CONNECT_TIMEOUT` / `GIGACHAT_READ_TIMEOUT` | `5s` / `30s`, для OAuth и генерации |
| `GIGACHAT_MAX_TOKENS` | `512` |

При необходимости доверенного CA добавьте в `.env` `spring.ai.gigachat.auth.certs.ca-certs=file:C:/certs/ca.pem`. Официальный сертификат и порядок настройки — в [инструкции GigaChat](https://developers.sber.ru/docs/ru/gigachat/certificates). Файл должен содержать доверенные сертификаты нужного сервиса. Проверка TLS включена; `unsafe-ssl=true` запрещён. Без явного CA используется системное доверие JVM. Настройки модели считываются при старте; после изменения `.env` приложение перезапускается.

Bean `gigaChatClient` предоставляет синхронный Spring AI `ChatClient` без автоматического retry/fallback; пустой ответ — ошибка. Текстовый ввод ACL/UI вызывает классификатор до транзакции: строгий JSON status создаёт обычный SELECT, null возвращает в чат шаблонное уточнение без обращения к FSM. Подтверждение и отказ — только специальные кнопки GA. Классификатор допускает максимум три технические попытки с одинаковым промптом; валидный null не повторяется. Явные ACL-действия обходят LLM. Изображения, embeddings, память и tools отключены.

System/user-шаблоны: `agent-api-out/src/main/resources/config/gigachat-prompts.yaml`; ответ-уточнение: `text.clarification` в `agent-service/src/main/resources/session-responses.properties` (каталог выбирается poc.responses.catalog). По запросу пользователя на INFO логируются полные system/user-промпты и текст ответа с общим callId. OAuth, ключи, токены и HTTP-заголовки не выводятся. При GIGACHAT_MODE=none текст возвращает технический отказ; саджесты работают.

Для SDK 1.1.2 неиспользуемое определение embedding-bean удаляется до инициализации, поскольку библиотека не учитывает его enabled-флаг.

После обычного `mvn -B verify` реальный smoke запускается отдельно из корня:

```powershell
mvn -B -pl agent-main -am verify -Pgigachat-smoke -DskipTests
```

В этом профиле `-DskipTests` пропускает уже выполненные unit-тесты, а Failsafe явно запускает **только** `GigaChatLiveSmoke`. Он читает корневой `.env`, принудительно включает клиент, получает `/v1/models` и выполняет одну синтетическую генерацию выбранной моделью. Печатаются доступные ID моделей, результат проверки и INFO-логи промптов/ответа с callId. Отсутствие ключа, выбранной модели, TLS-доверия или ответа приводит к ошибке, а не пропуску. Обычные тесты и `-Pintegration-tests` изолированы от `.env` и не запускают live smoke.

Если JAR уже используется работающим приложением, smoke можно выполнить без перепаковки:

```powershell
mvn -B -pl agent-main -am test-compile failsafe:integration-test failsafe:verify -Pgigachat-smoke '-Dit.test=GigaChatLiveSmoke' '-Dfailsafe.failIfNoSpecifiedTests=false'
```

### Общие параметры

Основные значения заданы в [application.yaml](agent-main/src/main/resources/application.yaml); особенности локального запуска и подключения БД — в соседних профильных YAML. Значения по умолчанию для этих параметров не дублируются в Java.

| Параметр | Переменная окружения | Значение по умолчанию |
|---|---|---|
| Порт HTTP | `SERVER_PORT` | `8080` |
| Адрес HTTP в poc-local | `SERVER_ADDRESS` | `127.0.0.1` |
| Код ACL-агента | `POC_ACL_AGENT_CODE` | `invest-pro` |
| Каталог текстов ответов | `POC_RESPONSES_CATALOG` | `session-responses` |
| Формат даты в сводке | `POC_PAYMENT_DATE_FORMAT` | `dd.MM.yyyy` |
| Имя Feign-клиента corr | `INVEST_CORR_NAME` | `invest-corr` |
| URL corr | `INVEST_CORR_URL` | Обязателен в реальном режиме |
| Connect/read timeout Feign, мс | `FEIGN_CONNECT_TIMEOUT` / `FEIGN_READ_TIMEOUT` | `2000` / `2000` |
| Режим и сценарий corr в poc-local | `INVEST_CORR_STUB` / `INVEST_CORR_STUB_SCENARIO` | `enabled` / `accepted` |
| URL, логин и пароль H2 | `POC_H2_URL` / `POC_H2_USER` / `POC_H2_PASSWORD` | Файловая `./data/poc`, `sa`, пустой пароль |
| URL, логин и пароль PostgreSQL | `POC_POSTGRES_URL` / `POC_POSTGRES_USER` / `POC_POSTGRES_PASSWORD` | Обязательны для профиля postgres |

Индивидуальные транспортные настройки задаются через `spring.cloud.openfeign.client.config.<имя клиента>`. При смене `INVEST_CORR_NAME` ключ должен соответствовать новому имени. Повторы corr запрещены; нулевые и отрицательные тайм-ауты отклоняются при запуске.

API и DTO генерируются стандартным OpenAPI Generator из YAML, без собственных Mustache-шаблонов. Описания задаются через `summary`/`description`. Версия ACL, имена полей сериализации, formatId и бизнес-события остаются частью контрактов кода.

## Corr и ограничения

Режим `integrations.invest-corr.stub=enabled` использует заглушку. Сценарии: `accepted`, `rejected`, `timeout`, `accepted-response-lost`. При `disabled` обязателен URL:

```powershell
java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=poc-local,h2 --integrations.invest-corr.stub=disabled --integrations.invest-corr.url=http://127.0.0.1:18081
```

Используется [временный corr-контракт](agent-api-out/src/main/resources/openapi/invest-corr-prototype.yaml), не подтверждённый банковской интеграцией. OperationId передаётся как clientRequestId и Idempotency-Key. Feign выполняет один запрос с connect/read timeout, по умолчанию 2000 мс, без retry и fallback.

Вызов corr происходит внутри Action до локального commit и ответа ACL. Ошибка откатывает локальные данные, но внешний эффект мог уже произойти. Автоматического повтора и гарантии exactly-once нет. `@Idempotent` пока только маркер входного ключа `sessionId:requestId`; библиотека replay не подключена.

## Модули

| Модуль | Назначение |
|---|---|
| agent-common | Маркер входной идемпотентности, настройка MapStruct и имена общего ACL-профиля |
| agent-model | Предметные DTO, подготовленные представления, enums и SessionEntity |
| agent-db | Семафор, транзакции, сборка SSM, native persistence, Liquibase |
| agent-service | Граф с guards/Actions, WorkflowManager-порт, оркестрация и тексты после commit |
| agent-api-in | Усечённый ACL 1.6 |
| agent-api-out | Временный WorkflowManager, Feign со сгенерированным контрактом и stub |
| agent-ui-back | Каталог fixtures, локальное зеркало ACL и раздача экрана |
| agent-ui | HTML/CSS/JS тестового экрана и клиентские тесты |
| agent-main | Wiring, профили, исполняемый JAR и сквозная приёмка |

[Требования](docs/stages/01_poc/old/requirements.md), [спецификация](docs/stages/01_poc/old/technical_specification.md), [план рефакторинга](docs/stages/01_poc/old/architecture_refactoring_plan.md), [архитектура](docs/stages/01_poc/old/c4_architecture.md), [ADR](docs/adr/README.md), [история](docs/stages/01_poc/old/history.md), [архив прежних вариантов](docs/stages/01_poc/old/README.md).

RECALL/DETAILS, текстовый разбор LLM/RAG, полная поверхность GA, входная библиотека идемпотентности и полноценный Outbox/УСС относятся к следующим задачам. Технический клиент GigaChat добавлен в [task03](docs/tasks/task03/tasks.md); результаты приёмки отмечаются отдельно.

### Оценка классификации сообщений

После обычного verify отдельный прогон реального GigaChat через HTTP-вход агента:

```powershell
mvn -B -pl agent-main -am verify -Pgigachat-classification-eval -DskipTests
```

По умолчанию используется `agent-main/src/test/resources/gigachat/classification-status.json`: 10 сообщений, 5 запросов статуса и 5 отрицательных примеров. Другой набор выбирается `-Dclassification.dataset=file:C:/path/dataset.json`. Формат записи: `id`, `text`, `expectedActionCode` (`status` либо null); размер — от 1 до 10, идентификаторы уникальны. Каждый пример получает отдельную сессию и синтетический платёж; подтверждения и банковские вызовы не выполняются.

Отчёт: `agent-main/target/gigachat-classification/report.json`. TP — статус распознан верно, TN — отсутствие команды распознано верно, FP — постороннее сообщение принято за статус, FN — запрос статуса не распознан. Технические ошибки учитываются отдельно в errors. После всех примеров прогон сохраняет отчёт и завершается неуспешно при FP/FN/errors; десять фраз не являются статистической оценкой качества на произвольных сообщениях. Обычный verify реальную модель не вызывает.