# Стандартная генерация API

API и DTO генерируются стандартным Spring-генератором OpenAPI Generator 7.25.0, закреплённым в parent POM. Собственных Mustache-шаблонов и `templateDirectory` нет. Описания задаются через `summary`/`description` в OpenAPI YAML; используется стандартное расположение документации в generated-коде. Файлы в `target` вручную не правятся.

Для corr генерируется интерфейс `InvestCorrFeignApi` и модели (`interfaceOnly=true`). Обычный `InvestCorrHttpApi` с `@FeignClient` наследует этот контракт без ручного дублирования методов. Имя `integrations.invest-corr.name` и адрес `integrations.invest-corr.url` задаются в `agent-main/src/main/resources/application.yaml`. Тайм-ауты задаются стандартными свойствами `spring.cloud.openfeign.client.config`; индивидуальный ключ должен совпадать с настроенным именем клиента. `CorrFeignConfiguration` подключается только к этому клиенту и сохраняет проверку тайм-аутов и запрет retry.

После изменения настроек генерации проверяется чистая сборка: старые generated-файлы не должны оставаться дополнительным источником классов.
