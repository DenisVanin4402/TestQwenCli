# C4 и последовательности текущего POC

Срез 05.10.2026: рабочее дерево с изменениями task01/task02, прямое использование Spring StateMachine по [ADR-021](../../adr/0021-direct-spring-statemachine.md). Диаграммы описывают код, а не завершённую приёмку: автоматические проверки и [независимая валидация task02](../../tasks/task02/validation.md) выполнены, ручной UI smoke открыт. Область и ограничения: [требования](../requirements_current.md), [техническая спецификация](../technical_specification_current.md).

Каждый файл — самостоятельный Markdown с Mermaid. Для просмотра нужен Markdown viewer с поддержкой Mermaid. Структурные диаграммы используют стабильный flowchart-синтаксис и уровни/границы C4, без зависимости от расширения C4 или внешних include. Sequence и FSM дополняют C4 динамикой; это не отдельные уровни C4. Уровень 4 с полным графом классов не добавлен: конкретные классы и методы перечислены в спецификации.

| Файл | Содержание |
|---|---|
| [01 — контекст](01_context.md) | Пользователь, invest-pro, тестовый ACL-клиент и внешний corr; настоящий GA отмечен как не подключённый. |
| [02 — контейнеры](02_containers.md) | Браузер, одна JVM backend, выбранная БД; stub и real как альтернативы. |
| [03 — компоненты](03_components.md) | Прямое исполнение SSM в service, хранение в db, отдельные guards/actions, подготовка ответа и исходящий адаптер. |
| [04 — FSM](04_state_machine.md) | Четыре перехода, guards и различие отказа guard / отсутствующего перехода. |
| [05 — подготовка](05_sequence_prepare.md) | Валидация, отдельный ensure, рабочая транзакция, create/restore и propose после commit. |
| [06 — подтверждение](06_sequence_confirm.md) | Согласие, один corr, persist/stop/commit, финальный ответ. |
| [07 — отказ и устаревшее согласие](07_sequence_cancel_and_stale.md) | CANCEL, новая подготовка с новым номером, guard-отказ на старый номер. |
| [08 — ошибки и rollback](08_sequence_failures.md) | Отказ guard, ошибка Action/БД, неопределённый внешний эффект и сбой ответа после commit. |
| [09 — конкуренция](09_sequence_concurrency.md) | NOWAIT, busy, постоянный семафор и восстановление после сбоя инициализации. |
| [10 — внутреннее чтение](10_sequence_read.md) | FOR SHARE NOWAIT, SessionSnapshotDTO без renderer, создания и записи; HTTP GET сессии нет. |
| [11 — тестовый чат](11_sequence_test_ui.md) | Fixtures, ACL-зеркало, эхо state, корреляция и отсутствие автоматических повторов. |

На C4 сплошные стрелки обозначают реализованные связи, пунктир к GA — только будущую интеграцию. Конфигурация и исполнение SSM находятся в agent-service, штатный persister и репозитории — в agent-db; зависимости db → service нет. На sequence пунктирные стрелки обозначают возврат ответа.

Основные опорные исходники: [SessionStateMachineConfiguration](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionStateMachineConfiguration.java), [SessionExecutionService](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionExecutionService.java), [SessionExecutionContext](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionExecutionContext.java), [NativePersistenceConfiguration](../../../agent-db/src/main/java/ru/sberbank/pprb/agent/db/config/NativePersistenceConfiguration.java), [AclMapper](../../../agent-api-in/src/main/java/ru/sberbank/pprb/agent/gigaassistant/mapper/AclMapper.java), [TestSession](../../../agent-ui/src/main/resources/test-ui/session.js).
