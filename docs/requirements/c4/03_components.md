# C4 L3 — компоненты backend

Показаны компоненты одного Java-контейнера и их основные связи. Подписи Maven-модулей задают размещение кода, а не сетевые границы.

```mermaid
flowchart TB
    client["ACL-клиент / браузер<br/>Внешний участник контейнера"]
    corr["Invest corr<br/>Внешняя система"]
    db[("Выбранная БД<br/>agent_session + state_machine")]
    subgraph backend["Backend invest-pro — один контейнер Spring Boot"]
        inbound["AclController / LocalAclController<br/>agent-api-in / agent-ui-back<br/>Два HTTP-входа"]
        adapter["AclInputAdapter + AclMapper<br/>agent-api-in<br/>Валидация, нормализация, ACL-ответ"]
        helper["LocalHelperController + FixtureCatalog<br/>agent-ui-back<br/>Синтетические metadata и suggestions"]
        orchestrator["SessionTurnOrchestrator<br/>agent-service<br/>SessionTurnUseCase / SessionQueryUseCase"]
        renderer["SessionResponseRenderer<br/>agent-service<br/>TurnOutDTO после транзакции по снимку"]
        definition["SessionStateMachineConfiguration<br/>agent-service<br/>SSM builder, переходы и OperationCatalog"]
        guards["Отдельные Guard-классы<br/>agent-service / guard.common<br/>Последовательные предметные проверки"]
        actions["Prepare / Execute / Cancel / Report actions<br/>agent-service<br/>Предметные данные и исход"]
        execution["SessionExecutionService<br/>agent-service<br/>Транзакции, блокировки, lifecycle SSM"]
        semaphore["SessionSemaphore + SessionRepository<br/>agent-db<br/>REQUIRES_NEW и NOWAIT"]
        validator["StoredSnapshotValidator<br/>agent-service<br/>Проверка восстановленных данных"]
        machine["StateMachine<br/>Spring Statemachine<br/>Один временный экземпляр на ход"]
        persister["Native JPA persister + repository<br/>agent-db / Spring Statemachine<br/>Kryo serialization"]
        workflow["InvestCorrAdapter : WorkflowManager<br/>agent-api-out<br/>Проверяет принятие STATUS"]
        outbound["InvestCorrClient<br/>agent-api-out<br/>Feign либо внутрипроцессный stub"]
    end
    client -->|"POST / HTTP JSON"| inbound
    client -->|"GET fixtures / HTTP JSON"| helper
    inbound -->|"handle"| adapter
    adapter -->|"handle DTO"| orchestrator
    adapter -->|"resolve action_code"| definition
    helper -->|"choices"| definition
    orchestrator -->|"ensureSemaphore, process или find"| execution
    orchestrator -->|"render после commit / failure после ошибки"| renderer
    renderer -->|"Схема и доступные операции"| definition
    execution -->|"ensure / lock"| semaphore
    semaphore -->|"JPA"| db
    execution -->|"create"| definition
    definition -->|"Создаёт через штатный builder, связывает guards/actions"| machine
    execution -->|"Проверить восстановленный snapshot"| validator
    validator -->|"Схема сохранённой операции"| definition
    execution -->|"start / sendEvent / await complete / stop"| machine
    machine -->|"Guard.evaluate через композицию конфигурации"| guards
    machine -->|"Action.execute через контроль завершения и ошибок"| actions
    execution -->|"restore / persist того же экземпляра SSM"| persister
    persister -->|"JPA в рабочей транзакции"| db
    actions -->|"Один callOperation через порт WorkflowManager"| workflow
    workflow -->|"submit"| outbound
    outbound -->|"Feign HTTP только в real-режиме"| corr
```

Исполнение напрямую использует API SSM; собственной абстракции движка и зависимости компиляции db → service нет. SessionExecutionContext передаёт данные одного события через message header и не сохраняется. В рамках одного хода один экземпляр FSM восстанавливается, обрабатывает событие, сохраняется и останавливается до commit. Внутренний find возвращает SessionSnapshotDTO без renderer. Тексты и HTTP-поля не входят в actions или native snapshot. Детали — [§3–7 спецификации](../technical_specification_current.md).
