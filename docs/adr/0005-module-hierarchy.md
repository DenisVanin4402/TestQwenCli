# ADR-005. Модули и направление зависимостей

**Статус:** Принят. **Редакция:** 05.10.2026. Task02 согласована 05.10.2026; прикладные guards/actions сохраняются отдельными классами.

## Решение

**Уточнение 05.10.2026:** [ADR-021](0021-direct-spring-statemachine.md) снимает требование заменяемого движка и изоляции SSM только в agent-db. Распределение ниже соответствует согласованной [task02](../tasks/task02/impl_design.md). Остальные границы API, модели и WorkflowManager сохраняются.

Проект использует Java 21 и Maven reactor с общим parent. Только agent-main собирается в исполняемый JAR. Сохраняются следующие границы компиляции:

| Модуль | Допустимые зависимости проекта | Ответственность |
|---|---|---|
| agent-common | — | Действительно общие технические типы и временный маркер идемпотентности. |
| agent-model | common | Внутренние DTO, перечисления, модели persistence; без generated API. |
| agent-db | model, common | Spring Data repositories, семафор, native JPA persister и сериализация SSM. |
| agent-service | db, model, common | Оркестратор, прямое исполнение SSM и рабочая транзакция, отдельные guards/actions, порты операций и подготовка ответа. |
| agent-api-in | service, model, common | Входной ACL API, controller/adapter, транспортная валидация, mapping. |
| agent-api-out | service, model, common | WorkflowManager-адаптер, generated corr/Feign, stub и последующие внешние интеграции. |
| agent-ui-back | api-in, service, model, common | Загрузка fixtures, локальное зеркало ACL и раздача экрана по [ADR-019](0019-test-ui-acl-mirror.md). |
| agent-ui | — | HTML/CSS/JavaScript тестового экрана и клиентские тесты. |
| agent-main | Все необходимые модули | Сборка приложения, wiring и профили. |

SessionExecutionService напрямую использует штатную SSM и persister; SessionStateMachineConfiguration задаёт переходы штатным builder. FlowDefinition/FsmEngine и собственный callback-протокол удалены. agent-db не импортирует agent-service. SSM-типы используются в service.fsm и инфраструктуре хранения, но не в agent-model, входных use cases и WorkflowManager.

По [task01](../tasks/task01/arch_design.md) прикладная логика guards и actions целиком находится в определяющих их классах agent-service, в разных пакетах service.fsm.guard и service.fsm.action. Общие guards находятся в guard.common. Перенос этих алгоритмов в FSM, конфигурацию или внешние бизнес-сервисы не допускается. Разрешены технические зависимости и чистое копирование mapper-ами. Action вызывает порт WorkflowManager, реализация находится в agent-api-out; обратной зависимости сервиса на адаптер нет. Guards/actions реализуют штатные Guard/Action SSM; алгоритмы не выносятся в конфигурацию.

JPA entities не передаются контроллерам и бизнес-логике. Транзакционная граница исполнения находится у SessionExecutionService в agent-service; оркестратор получает результат после её завершения и формирует содержание ответа по [ADR-004](0004-api-domain-mapstruct.md).

## Проверка

Границы проверяются компиляцией и review реальных импортов и ответственности. ArchUnit, искусственные Good/Bad fixtures и дополнительные модули ради контроля графа не нужны. Отдельный модуль UI добавлен по решению пользователя 05.10.2026; прежнее ограничение agent-ui-back только GET заменено ADR-019.
