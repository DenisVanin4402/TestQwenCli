# C4 L2 — контейнеры

Контейнер C4 — единица выполнения или хранения, не Maven-модуль и не обязательно Docker-контейнер. Все backend-модули собраны в один исполняемый JAR.

```mermaid
flowchart LR
    user["Пользователь<br/>Person"]
    client["Тестовый ACL-клиент<br/>External System"]
    corr["Invest corr<br/>External System / HTTP JSON"]
    subgraph boundary["invest-pro — Software System"]
        ui["Тестовый чат<br/>Container: браузер, HTML/CSS/JS<br/>Ресурсы agent-ui"]
        backend["Backend приложения<br/>Container: Java 21 / Spring Boot<br/>agent-main executable JAR<br/>ACL, сценарий, SSM, renderer, corr adapter"]
        pg[("Хранилище сессий<br/>Container: PostgreSQL<br/>agent_session + state_machine")]
        h2[("Локальное хранилище<br/>Container: H2 file в JVM<br/>Альтернатива PostgreSQL")]
    end
    user -->|"Работает с чатом"| ui
    backend -->|"Раздаёт /test-ui/ и JS/CSS"| ui
    ui -->|"GET fixtures и POST ACL-зеркала / HTTP JSON"| backend
    client -->|"POST основного ACL / HTTP JSON"| backend
    backend -->|"JPA/JDBC, профиль postgres"| pg
    backend -->|"JPA/JDBC, профиль h2"| h2
    backend -->|"Один Feign POST, stub=disabled"| corr

    classDef container fill:#dcecff,stroke:#245b91,color:#132b42
    class ui,backend,pg,h2 container
```

Одновременно выбирается ровно одна БД. В `poc-local` по умолчанию активен StubInvestCorrClient внутри backend: отдельного stub-сервиса нет. Профиль bootstrap не поднимает web/БД и не демонстрирует этот сценарий. Настройки — [§8 спецификации](../technical_specification_current.md#8-конфигурация-и-запуск).
