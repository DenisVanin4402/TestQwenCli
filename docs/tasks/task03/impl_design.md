# Task03. Дизайн реализации

**05.10.2026. Статус: Завершена.** Пользователь явно согласовал этот документ и [список задач](tasks.md): «подтверждаю, можно релаизовывать». Код реализован, обязательные проверки выполнены; независимая [валидация](validation.md) пройдена. Основание — [arch_design.md](arch_design.md).

Дополнительно пользователь выбрал OAuth по Authorization Key из `.env`. Поле AccessToken в файле не используется; SDK управляет токеном в памяти. Файлы окружения, gitignore и инструкция были подготовлены до согласования кода; после согласования реализована загрузка и клиент.

## 1. Проверенные возможности библиотеки

Исследованы metadata и bytecode установленных GigaChat/Spring AI 1.1.2. Версии POM остаются прежними.

- `GigaChatAutoConfiguration` целиком включается при `spring.ai.model.chat=gigachat`; отсутствие свойства тоже включает её. Поэтому выключенное значение задаётся явно: `none`.
- Изображения отключаются `spring.ai.model.image=none`. **Уточнение по Spring-проверке:** у `gigaChatEmbeddingModel` в SDK 1.1.2 нет условия по свойствам, поэтому `embedding.enabled=false` недостаточно. Статический `disableUnusedEmbeddingModel()` в GigaChatConfiguration удаляет только это определение через штатный `BeanDefinitionRegistryPostProcessor` до создания beans. Chat/OAuth остаются автоконфигурируемыми; отдельный клиент или замена библиотеки не вводятся.
- Автоконфигурация создаёт `GigaChatModel`, API и библиотечную авторизацию. `GigaChatBearerAuthApi` хранит и обновляет access token по времени при обращении; отдельный кэш или планировщик не нужны.
- `internal.connect-timeout/read-timeout` применяются библиотекой и к OAuth; read timeout по умолчанию отсутствует. `spring.ai.retry.max-attempts` по умолчанию равен 10, поэтому задаётся 1.
- `CallAdvisor.adviseCall(ChatClientRequest, CallAdvisorChain)` позволяет проверить `ChatClientResponse.chatResponse()` в существующем клиенте.

Статические выводы проверены тестами реального HTTP-обмена и запуска; особенность embedding-bean уточнена по результатам теста. Результаты приёмки приведены в tasks.md.

## 2. Изменения модулей и контракт

| Файл/класс | Изменение |
|---|---|
| `agent-api-out/.../gigachat/config/GigaChatConfiguration.java` | `@Configuration(proxyBeanMethods=false)`, условие `spring.ai.model.chat=gigachat`. Метод `gigaChatClient(ChatClient.Builder)` создаёт один именованный bean `gigaChatClient` с `GigaChatResponseAdvisor`. API, модель, OAuth и RetryTemplate остаются библиотечными. |
| `agent-api-out/.../gigachat/GigaChatResponseAdvisor.java` | Реализует только синхронный `CallAdvisor`: один `chain.nextCall(request)`, затем проверка наличия chatResponse, generation, output и непустого `getText()`. При отсутствии — `IllegalStateException` с постоянным сообщением; повторов и записи тела в лог нет. Реализует обязательные `getName/getOrder`. |
| `agent-api-out/.../gigachat/package-info.java` | Описать реальный синхронный клиент и его текущие границы. |
| `agent-main/.../PocConfiguration.java` | Добавить пакет `ru.sberbank.pprb.agent.gigachat` в существующий scan, действующий вне bootstrap. |
| `agent-main/.../GigaChatValidationConfiguration.java` | Статический `validateGigaChatConfiguration(Environment)` возвращает `BeanFactoryPostProcessor` и проверяет настройки до создания клиентских beans. Использует существующие свойства, без дублирующего properties DTO. |
| `agent-main/src/main/resources/application.yaml` | Добавить `spring.config.import: optional:file:.env[.properties]`, убрать глобальное исключение `GigaChatAutoConfiguration`, добавить явный выключенный режим и настройки ниже. |
| `application-bootstrap.yaml` | Сохранить исключение GigaChat. Валидатор запрещает включение real вместе с bootstrap, чтобы не возникало молчаливого игнорирования выбранного режима. |
| `agent-main/pom.xml` | Добавить профиль `gigachat-smoke`, который выбирает только отдельный live smoke средствами существующего Failsafe. |

`agent-service`, модель данных, ACL, FSM, БД и UI не изменяются. Прикладной порт, собственные request/response DTO, HTTP endpoint и stub LLM не создаются.

Java-контракт для потребителя — стандартный `ChatClient`:

```java
String text = gigaChatClient.prompt()
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .content();
```

Поддерживается синхронный текстовый вызов без памяти, инструментов и дополнительных advisors. Streaming не входит в контракт task03. Операционные действия приложения пока не вызывают этот bean.

## 3. Конфигурация и ранняя проверка

| Свойство | Значение / источник |
|---|---|
| `spring.ai.model.chat` | `${GIGACHAT_MODE:none}`; разрешены только `none` и `gigachat`. |
| `spring.ai.model.image` | `none`. |
| `spring.ai.gigachat.embedding.enabled` | `false`. |
| `spring.ai.gigachat.auth.bearer.api-key` | `${GIGACHAT_API_KEY:}` — Authorization Key для обмена на access token. |
| `spring.ai.gigachat.auth.scope` | `${GIGACHAT_SCOPE:GIGACHAT_API_PERS}`; допустимые значения enum SDK. |
| `spring.ai.gigachat.auth.bearer.url` | `${GIGACHAT_AUTH_URL:https://ngw.devices.sberbank.ru:9443/api/v2/oauth}`. |
| `spring.ai.gigachat.base-url` | `${GIGACHAT_BASE_URL:https://api.giga.chat/v1}`. SDK добавляет `/chat/completions` или `/models`; путь `/v1` уточнён пользователем. |
| `spring.ai.gigachat.auth.unsafe-ssl` | `false`; включение небезопасного TLS отклоняется. |
| `spring.ai.gigachat.auth.certs.ca-certs` | Не задаётся пустым значением. При необходимости внешний resource, например через `SPRING_AI_GIGACHAT_AUTH_CERTS_CA_CERTS=file:/.../ca.pem`; иначе системное доверие JVM. |
| `spring.ai.gigachat.chat.options.model` | `${GIGACHAT_MODEL:GigaChat-3-Ultra}`; доступность проверяется на стенде. |
| `spring.ai.gigachat.chat.options.max-tokens` | `${GIGACHAT_MAX_TOKENS:512}`. |
| `spring.ai.gigachat.chat.options.internal-tool-execution-enabled` | `false`; callbacks и tools не регистрируются. |
| `spring.ai.gigachat.internal.connect-timeout` | `${GIGACHAT_CONNECT_TIMEOUT:5s}`. |
| `spring.ai.gigachat.internal.read-timeout` | `${GIGACHAT_READ_TIMEOUT:30s}`. |
| `spring.ai.retry.max-attempts` | `1`, относится к Spring AI, не к Feign/corr. |

По [документации GigaChat](https://developers.sber.ru/docs/ru/gigachat/models/updates) Ultra использует адрес `https://api.giga.chat/`; [идентификатор модели](https://developers.sber.ru/docs/ru/gigachat/guides/selecting-a-model) — `GigaChat-3-Ultra`. Доступность для конкретного аккаунта не предполагается по умолчанию: проверяется live smoke, без автоматической подмены другой моделью.

`.env` читается как UTF-8 properties из рабочего каталога, рекомендуемый каталог запуска — корень репозитория. Формат `KEY=value` без кавычек/`export`/inline-комментариев, пути с `/`. `GIGACHAT_API_KEY` разрешается существующим YAML placeholder. `GIGACHAT_ACCESS_TOKEN` никуда не привязывается, не подменяет ключ даже при его отсутствии и не записывается SDK обратно. Файл необязателен для `none`; в real отсутствие ключа после разрешения всех источников даёт ранний отказ. Настройки окружения/CLI имеют стандартный более высокий приоритет. Для другого расположения допускается явный `spring.config.import` при запуске. В тестах применять временный файл с синтетическими значениями или переопределять import, чтобы обычная сборка не подхватывала пользовательские секреты/real-режим из корневого `.env`.

При `none` проверяется только допустимость режима: credentials, CA и модель не требуются, beans SDK не создаются. При `gigachat` проверяются непустые key/model, допустимый scope, абсолютные HTTP(S) URL без user-info, положительные тайм-ауты/max-tokens, max-attempts=1, выключенные image/embedding/tools/unsafe-ssl и отсутствие bootstrap. HTTP нужен локальному контрактному серверу; реальные адреса в README и live smoke используют HTTPS. При явно заданном CA ресурс должен читаться; разбор сертификата выполняет SDK.

Ошибки конфигурации называют свойство без его значения. Значения ключа/токена и HTTP headers не логируются приложением. По прямому уточнению пользователя в [task04](../task04/impl_design.md) прежний запрет логирования содержимого заменён INFO-логами system/user-промптов и ответа модели с callId; OAuth и HTTP logging не включаются. При невалидном формате чисел/Duration сообщения проверки также не должны выводить секреты из соседних настроек. Сетевой запрос на старте не выполняется: проверка доступности сервиса отделена от проверки конфигурации.

## 4. Ошибки и число вызовов

Один вызов `call()` выполняет не более одного запроса генерации; отдельно возможен OAuth-запрос для получения/обновления токена. Прикладного retry нет, библиотечный бюджет — одна попытка. HTTP 4xx/5xx, ошибка авторизации, TLS, timeout и повреждённый JSON выходят штатными исключениями SDK/Spring; fallback отсутствует. Не вводить собственную иерархию ошибок без прикладного потребителя.

Отсутствующий/пустой текст отклоняет advisor. Он не изменяет успешный ответ и не запускает исправление JSON или повтор. Политика трёх попыток структурированного разбора ADR-007 сейчас не реализуется. При live smoke ошибка завершает проверку неуспешно; отчёт не печатает credentials или полный объект исключения с телом upstream.

## 5. Минимальные проверки

1. **`GigaChatClientTest` в agent-api-out**, локальный JDK `HttpServer` + настоящая автоконфигурация: OAuth с Basic key, scope и RqUID; генерация с Bearer token, model, system/user и max_tokens; текст ответа. Второй вызов повторно использует токен; отдельный ответ с истёкшим сроком проверяет его обновление. Счётчики OAuth и генерации раздельные.
2. В том же наборе: HTTP 401/429/503, отказ OAuth, некорректный JSON, пустой ответ и задержка больше read timeout дают отказ; запрос генерации не повторяется. При отказе OAuth генерация не отправляется. Нужны проверки timeout как генерации, так и OAuth, поскольку они используют отдельные HTTP-клиенты.
3. **`GigaChatConfigurationTest` в agent-main**: конфигурация из YAML; `none` без ключей не создаёт клиента, `gigachat` создаёт единственный ChatClient/chat-model без image/embedding и сетевых вызовов на старте. Неверный режим, отсутствие key, неположительный timeout, повторные попытки, небезопасный TLS и bootstrap+real отклоняются. Импорт временного `.env` проверяет загрузку ключа, отсутствие влияния AccessToken, приоритет внешней настройки и работу без файла в `none`. Наличие только AccessToken не позволяет запустить real. Проверки таблицей, без искусственных fixtures.
4. Существующая приёмка POC/упакованного JAR с выключенным клиентом. В имеющуюся HTTP-проверку добавить один сценарий с включённым клиентом и локальным счётчиком: явные ACL-действия не обращаются к LLM. Изменений persistence нет, полный PostgreSQL-набор только ради wiring не нужен.
5. **`GigaChatLiveSmoke` в agent-main**: JUnit-класс без суффиксов `Test`/`IT`, выбирается только профилем `gigachat-smoke`. Минимальный Spring-контекст загружает штатные YAML, автоконфигурации, валидатор и конфигурацию клиента без POC/БД; отправляет синтетический system/user запрос и требует непустой текст. Maven-профиль явно передаёт путь к `.env` корня reactor через `spring.config.import`, поскольку рабочий каталог Failsafe — модуль agent-main. Нет пропуска по отсутствующим credentials: явно выбранный smoke завершается ошибкой. Обычные `verify` и `-Pintegration-tests` его не выбирают.

План запуска после реализации: необходимые RED/GREEN тесты → `mvn -B verify` (включая стандартный JAR smoke и форматирование проекта) → отдельно `mvn -B -pl agent-main -am verify -Pgigachat-smoke -DskipTests` с внешним ключом, доступом и CA. Профиль явно задаёт Failsafe `skipTests=false`, поэтому `-DskipTests` здесь пропускает только уже выполненные unit-тесты. Не повторять успешные проверки без изменения кода/настроек. Нельзя заменить настоящий HTTP моками ChatClient.

## 6. Документы и завершение

README описывает автоматический импорт, включение `GIGACHAT_MODE=gigachat`, настройки и отдельную команду live smoke. `.env` не перезаписывать; изменения примера вносить в `.env.example`. В существующем `.env` исправлен только ранее подготовленный base-url на `/v1`, ключ не менялся. Smoke дополнительно проверяет выбранную модель через библиотечный `GigaChatApi.models()` по запросу пользователя. ADR-001/007 уточнены на уровне принятой архитектуры.

После проверок обязательна независимая валидация отдельным агентом с результатом в `validation.md`. Live smoke выполнен с ключом, заполненным пользователем: список моделей и генерация GigaChat-3-Ultra успешны после настройки локального CA. Ни ключ, ни access token, ни сырой ответ в документы не включаются. Задача завершена после независимой [валидации](validation.md).
