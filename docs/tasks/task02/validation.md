# task02. Независимая архитектурная валидация

**Дата:** 05.10.2026. Проверено отдельным агентом-валидатором по AGENTS.md, без изменения production-кода и тестов.

**Вывод:** реализация соответствует принятому направлению и дизайну. Существенных дефектов кода или возврата удалённой абстракции под новыми именами не обнаружено. Документационные замечания исправлены и перепроверены. Полная приёмка остаётся открытой до ручного UI smoke.

- [SessionStateMachineConfiguration](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionStateMachineConfiguration.java) регистрирует четыре штатных перехода. Семь отдельных guards и четыре actions сохранены вместе с алгоритмами. Конфигурация соединяет callbacks, выбирает зарегистрированные обработчики и передаёт ошибки; правила реквизитов и согласия в неё не перенесены. Общие guards идут перед дополнительными и останавливаются при первом отказе. Три состояния различают допустимые действия; скрытой замены состояний флагами нет.
- [SessionExecutionService](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/fsm/SessionExecutionService.java) выполняет lock/restore/send/complete/persist/stop в рабочей транзакции. Ошибки guard/action возвращаются из callbacks на транзакционную границу; отказ guard не сохраняет снимок. Ошибка stop откатывает транзакцию либо добавляется к основной ошибке. [Оркестратор](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/turn/SessionTurnOrchestrator.java) вызывает renderer после возврата из transactional proxy; commit failure не превращается в успех. Автоматического retry corr нет.
- FsmEngine, NativeMachineStore, собственные планы переходов, четыре DTO и TurnCommand удалены. SessionExecutionContext хранит данные одного события в header, не является вторым движком и не попадает в ExtendedState. OperationDefinition сохраняет только необходимую программную регистрацию схемы/actions/guards. Входные порты, WorkflowManager и предметные DTO не зависят от SSM; зависимости db → service нет.
- status-v3, persisted-типы, ключи ExtendedState и Kryo ID сохранены. [SessionPersistenceTest](../../../agent-main/src/test/java/ru/sberbank/pprb/agent/main/SessionPersistenceTest.java) восстанавливает прежний native fixture и подтверждает предложение; проверяет повреждения, отказ старого формата, rollback callback/persist/stop и отсутствие записи при guard denial.

Проверены исходники тестов и актуальные XML в `target`: **103 проверки, 0 failures/errors/skips**, включая PostgreSQL NOWAIT/LOB, реальный отказ commit, оба ACL-входа и JAR restart. Повторный запуск не требовался. JS **7/7** — результат запуска основного исполнителя; код тестов сохраняет точное эхо state, корреляцию и один POST.

## Сверка task01 и оставшаяся приёмка

AC1–AC8 task01 подтверждены кодом и проверками: [OperationFlowTest](../../../agent-service/src/test/java/ru/sberbank/pprb/agent/service/fsm/OperationFlowTest.java), [SessionStateMachineTest](../../../agent-service/src/test/java/ru/sberbank/pprb/agent/service/fsm/SessionStateMachineTest.java), [PocHttpIntegrationTest](../../../agent-main/src/test/java/ru/sberbank/pprb/agent/main/PocHttpIntegrationTest.java), SessionPersistenceTest и интеграционные проверки. AC9 подтверждён автоматизированной частью; ручной сценарий UI ещё не выполнен. Согласования, независимая валидация и документационная сверка AC10 выполнены; остаётся обязательная ручная приёмка.

Повторная проверка документов: task01 отмечает фактически выполненное поведение со ссылкой на результаты task02, удалённый DSL обозначен заменённым ADR-021; исторический RED не заявляется. Команды в §3 технической спецификации и внутреннее чтение в ADR-009/017 приведены к коду. Статусы реестра task01/task02 — «Валидация», ручной smoke оставлен открытым.

Дополнительная перепроверка: §3 технической спецификации корректно описывает preparationNo/confirmationSnapshot и отдельный requestId; §8 различает эту проверку согласия и отложенную входную идемпотентность. Обе строки статуса task01/tasks.md согласованы с валидацией. Открытых документационных замечаний нет.

**Ограничение:** браузер пока недоступен; пользователь сообщил, что подключит его. Сценарий `/test-ui/`: выбор → предложение → отказ → новая подготовка → согласие остаётся обязательным и не выдан за выполненный. Финальный статус нужно обновить после его фактического выполнения.
