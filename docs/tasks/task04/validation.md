# Task04. Независимая архитектурная валидация

**Дата:** 05.10.2026. Проверку выполнил отдельный агент-валидатор после реализации и автоматических проверок. Сопоставлены [архитектура](arch_design.md), [дизайн реализации](impl_design.md), [задачи](tasks.md), ADR-007/020, код и результаты в target.

**Вывод:** существенных расхождений реализации с согласованным вариантом A не найдено. Архитектурная проверка пройдена; полная приёмка остаётся открытой из-за визуального UI smoke (P01). Статус «Завершена» пока неприменим.

## Подтверждено кодом и проверками

- [AclMapper](../../../agent-api-in/src/main/java/ru/sberbank/pprb/agent/gigaassistant/mapper/AclMapper.java) сохраняет приоритет управляющего performative и action_code над текстом; неизвестный явный код не запускает классификацию. Оба HTTP-входа используют общий путь.
- [SessionTurnOrchestrator](../../../agent-service/src/main/java/ru/sberbank/pprb/agent/service/turn/SessionTurnOrchestrator.java) классифицирует до семафора/транзакции, проверяет код через общий каталог и формирует прежний SELECT. Валидный null возвращает шаблонный inform без обращения к исполнителю FSM; техническая ошибка отдельно возвращает failure. LLM не создаёт CONFIRM/CANCEL. Guards/actions изменены только для явных типов; правила подтверждения и persistence сохранены.
- [GigaChatMessageClassifier](../../../agent-api-out/src/main/java/ru/sberbank/pprb/agent/gigachat/GigaChatMessageClassifier.java) проверяет строгий JSON модели, запрещает лишние/дублирующиеся поля, обёртки, trailing JSON и неизвестные коды. Максимум три технические попытки с одинаковыми промптами, после успеха/null повторов нет. Полные system/user-шаблоны находятся в [YAML](../../../agent-api-out/src/main/resources/config/gigachat-prompts.yaml), ответы — в существующем каталоге properties. Новых FSM, таблиц, endpoints, RAG или неподдерживаемых операций не добавлено.
- [GigaChatResponseAdvisor](../../../agent-api-out/src/main/java/ru/sberbank/pprb/agent/gigachat/GigaChatResponseAdvisor.java) пишет INFO с полными SYSTEM/USER и текстом ответа, связывая их callId. Это подтверждено реальным логом eval; авторизационные данные в эти записи не включаются. Поиск по src/main/java не выявил var.
- [TestSession](../../../agent-ui/src/main/resources/test-ui/session.js) доставляет текст через ACL, блокирует ввод при busy/propose/final, разрешает после уточнения/отказа и отсекает поздние ответы. [Прогон классификации](../../../agent-main/src/test/java/ru/sberbank/pprb/agent/main/GigaChatClassificationEvaluation.java) использует реальный HTTP-путь и отдельные сессии, считает все строки, отделяет ошибки от матрицы и сохраняет отчёт до итоговых assertions; WorkflowManager не вызывается.

## Фактические результаты и ограничения

- В target/task04-verify.log подтверждены **102 теста**, без ошибок/пропусков; исходная упаковка остановилась на занятом JAR. Исполнитель сообщил **8 успешных Node-тестов**; их сценарии просмотрены, повторный запуск не выполнялся.
- agent-main/target/gigachat-classification/report.json и target/task04-eval.log: реальный GigaChat-3-Ultra, **TP=5, TN=5, FP=0, FN=0, errors=0, total=10**. Это результат контрольного набора, не оценка статистической точности модели.
- target/task04-build/task04-package.log: **1 успешный JAR smoke** с загрузкой YAML и штатным подтверждением. Найденное затем форматирование исправлено; target/task04-build/task04-final-check.log подтверждает **BUILD SUCCESS** для verify с уже проверенными тестами, пропущенными через -DskipTests -DskipITs. Полный единый успешный запуск verify не заявляется: проверки выполнены последовательно без необоснованного повтора.
- **Открыто P01:** визуальные сценарии «текст → propose», «отказ → новый ввод», «неизвестное действие → уточнение». По данным исполнителя, CUA не предоставляет браузер; Chrome/iab возвращают Browser is not available. HTTP и Node покрывают поведение, но не заменяют этот согласованный UI smoke. До его выполнения задача остаётся на валидации.

Производственный код и тесты валидатор не изменял; повторные тесты без обнаруженного дефекта не запускал. Логи и XML в документацию не копировались.
