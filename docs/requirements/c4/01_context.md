# C4 L1 — контекст системы

Текущий локальный POC готовит и передаёт запрос статуса. Банковский статус и последующий результат corr в агент не возвращаются. Настоящая поверхность GA пока не подключена.

```mermaid
flowchart LR
    user["Пользователь / тестировщик<br/>Person"]
    client["Тестовый ACL-клиент<br/>External Software System<br/>HTTP-инструмент или тестовый стенд"]
    agent["invest-pro<br/>Software System<br/>Подготовка, согласие, отправка STATUS"]
    corr["Invest corr<br/>External Software System<br/>Временный HTTP-контракт принятия запроса"]
    ga["ГигаАссистент<br/>External Software System<br/>Интеграция отложена"]

    user -->|"Выбирает, подтверждает или отклоняет через встроенный чат"| agent
    user -->|"Запускает сценарий"| client
    client -->|"Локальный профиль ACL 1.6 / HTTP JSON"| agent
    agent -->|"Один STATUS, ответ о принятии / HTTP JSON в real-режиме"| corr
    ga -.->|"Будущий канал ACL; сейчас не подключён"| agent

    classDef system fill:#dcecff,stroke:#245b91,color:#132b42
    classDef external fill:#eeeeee,stroke:#777777,color:#222222
    classDef future fill:#ffffff,stroke:#999999,stroke-dasharray:5 5,color:#555555
    class agent system
    class client,corr external
    class ga future
```

В режиме stub сетевой corr заменяет внутрипроцессная заглушка. Проверка идентичности metadata не является банковской авторизацией. См. [границы требований](../requirements_current.md#1-назначение-и-границы).
