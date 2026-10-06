# Task03. Независимая валидация

05.10.2026 отдельный агент `task03_arch_validator` сопоставил архитектуру, дизайн реализации, задачи, код и результаты проверок. **Валидация пройдена; AC 1–6 закрыты, существенных расхождений нет.** Устаревшие формулировки о ещё не выполненной реализации в документации исправлены.

- Штатные Spring AI/OAuth и точечное отключение embedding-bean SDK 1.1.2 соответствуют выбранному варианту A: [GigaChatConfiguration](../../../agent-api-out/src/main/java/ru/sberbank/pprb/agent/gigachat/config/GigaChatConfiguration.java).
- Пустой ответ отклоняется после одного вызова: [GigaChatResponseAdvisor](../../../agent-api-out/src/main/java/ru/sberbank/pprb/agent/gigachat/GigaChatResponseAdvisor.java). Настройки проверяются до создания клиента: [GigaChatValidationConfiguration](../../../agent-main/src/main/java/ru/sberbank/pprb/agent/main/GigaChatValidationConfiguration.java).
- ACL/FSM не обращаются к модели; это контролирует [PocHttpIntegrationTest](../../../agent-main/src/test/java/ru/sberbank/pprb/agent/main/PocHttpIntegrationTest.java). Обычные тесты изолированы от пользовательского `.env`; [live smoke](../../../agent-main/src/test/java/ru/sberbank/pprb/agent/main/GigaChatLiveSmoke.java) выбирается отдельно.

Подтверждены 77 unit/HTTP-проверок, включая 3 HTTP/OAuth и 17 конфигурационных, плюс 1 JAR smoke и 1 реальный GigaChat smoke: **79 успешных проверок без пропусков**. Реальный smoke получил список моделей через `/v1/models` и непустой ответ `GigaChat-3-Ultra`; TLS проверялся с локальным официальным CA. Секреты в отчёт не включены.

Непрерывный `verify` остановился на блокировке JAR работающим POC после успешных unit/HTTP-тестов. После освобождения файла сборка package, оставшаяся проверка форматирования и JAR smoke завершены раздельно; POC восстановлен. Команды и результаты отражены в [tasks.md](tasks.md), стандартные отчёты остаются в `target`. Валидатор повторно тесты не запускал.

PostgreSQL-набор не повторялся: persistence не менялся. Текстовый диалог, RAG и подключение модели к UI/FSM остаются вне согласованного объёма task03. Незакрытых обязательных задач в этом объёме нет.
