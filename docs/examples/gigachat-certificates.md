# Пример запуска GigaChat с доверенным сертификатом

[Пример настроек](gigachat-certificates.properties.example) автоматически не загружается. Действующий `.env` менять не требуется.

Сертификат CA используется для проверки TLS сервера. Авторизация остаётся OAuth по Authorization Key: сертификат не заменяет `GIGACHAT_API_KEY`. Официальная [инструкция по сертификатам GigaChat](https://developers.sber.ru/docs/ru/gigachat/certificates).

Для будущего запуска:

1. Скопируйте пример за пределы репозитория, например в `C:/Users/deanv/gigachat-local.properties`.
2. Заполните `GIGACHAT_API_KEY` без префикса `Basic` и кавычек. Проверьте scope своего проекта и путь к доверенному сертификату в формате PEM.
3. Из корня репозитория запустите собранный JAR с явным импортом:

```powershell
java -jar agent-main/target/agent-main-1.0-SNAPSHOT.jar --spring.profiles.active=poc-local,h2 --server.port=8080 "--spring.config.import=classpath:config/gigachat-prompts.yaml,file:C:/Users/deanv/gigachat-local.properties"
```

Этот аргумент заменяет стандартный импорт `.env`, сохраняя YAML промптов. Настройки окружения и аргументы команд имеют более высокий приоритет; уберите прежнее `--spring.ai.model.chat=none`, если оно задано.

В IntelliJ IDEA используйте те же аргументы в **Program arguments**, а в **Working directory** укажите `C:/Users/deanv/IdeaProjects/invest-pro`.

После старта интерфейс доступен по адресу http://127.0.0.1:8080/test-ui/. Для локальной файловой H2 запускайте один экземпляр приложения. Остановка — Ctrl+C.

Пример подготовлен без запуска приложения, установки сертификатов и обращения к API GigaChat. Проверка реального соединения потребует заполненного ключа и доступного сертификата.
