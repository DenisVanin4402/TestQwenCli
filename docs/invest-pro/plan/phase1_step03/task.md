# Task: P1.03 — Invest corr: порты, stub и Feign

**Стадия:** implement. **Design revision:** 2, согласована. **Статус:** Implement на проверке. [Design](./design.md).

## 1. Согласование и запуск

- [x] Архитектор изучил принятые шаги, ADR, архитектурные требования и существующий код.
- [x] Описаны AC-03-01–07, T-01–07, файлы, границы, вопросы Q-01–03.
- [x] Пользователь принял рекомендованные Q-01–03; решения внесены без новых условий.
- [x] Пользователь явно согласовал design/task r1: «согласовано», 28.09.2026.
- [x] По согласованию запущен отдельный Java-разработчик.
- [x] Q-04 A согласован: hostname разрешён; синхронный DNS ограничен ОС и исключён из жёсткого deadline. Архитектор зафиксировал r2.

После представления design/task r1 и редакционного вводного абзаца пользователь ответил «согласовано». Ответ разрешает немедленный запуск Java-разработчика; техническая фиксация согласования не меняет r1 и не требует повторного разрешения.

## 2. Checkpoints реализации

| ID | Результат | AC / тесты | Состояние |
|---|---|---|---|
| CP-01 | Unit RED → DTO/порты/mapping/canonical payload → GREEN | 01,03,04 / T-01,T-02 | Закрыт |
| CP-02 | Unit RED → fixtures/stub/атомарный реестр/deadline → GREEN | 03,04,06 / T-03 | Закрыт |
| CP-03 | Context RED → ровно один клиент, строгая конфигурация → GREEN | 02 / T-04 | Закрыт |
| CP-04 | HTTP RED → OpenAPI/Feign, ошибки, без retries, timeout по r2 → GREEN | 01,04,05,06 / T-05,T-06 | Закрыт |
| CP-05 | Main/YAML/README, bootstrap и clean verify | 07 / T-07 и регрессия | Закрыт |
| CP-06 | Review, task/history/WBS/handoff; Implement на проверке; остановка | Все AC | Закрыт: review и handoff сохранены; остановка на приёмке |

## 3. Свидетельства TDD и тестов

| Набор | Команда | RED | GREEN / exit / tests-failures-errors-skips |
|---|---|---|---|
| Deadline | `mvn -pl agent-model -am test -Dtest=DeadlineTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 1; 3/3/0/0, нет расчёта остатка/проверки бюджета | exit 0; 3/0/0/0 |
| T-02 | `mvn -pl agent-api-out -am test -Dtest=SubmissionCanonicalizerTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 1; 3/3/0/0, пустые canonical bytes/hash | exit 0; 3/0/0/0 |
| T-01 | `mvn -pl agent-api-out -am test -Dtest=InvestCorrAdapterTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 1; 9/1/8/0, минимальный adapter возвращал null | exit 0; 9/0/0/0; затем regression errorCode |
| T-03 + regression T-01 | `mvn -pl agent-api-out -am test -Dtest=StubInvestCorrClientTest,InvestCorrAdapterTest -Dsurefire.failIfNoSpecifiedTests=false` | выборочно stub + technicalCode regression: exit 1, 9 тестов; fixtures/приём отсутствовали, технический код ошибочно становился отказом | exit 0; 18/0/0/0 |
| T-04–T-06 | `mvn -pl agent-api-out -am test -Dtest=InvestCorrConfigurationTest,InvestCorrHttpContractTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 1; 26/26/0/0, конфигурация не регистрировала/не проверяла клиентов | exit 0; 28/0/0/0, включая 503/408 |
| T-07 bootstrap | `mvn -pl agent-main -am test -Dtest=BootstrapContextTest -Dsurefire.failIfNoSpecifiedTests=false` | exit 1; 1/1/0/0, corr beans отсутствовали | exit 0; 1/0/0/0 в финальном verify |
| T-07 + регрессия | `mvn clean verify` | Не требуется отдельный RED регрессии | 28.09.2026 16:27:59 +03:00, exit 0; **195/0/0/0** (192 unit/HTTP/context + 3 IT); corr 50 + Deadline 3 |

Проверки выполнены 28.09.2026. Стандартные отчёты остаются в target. Отдельный диагностический probe OkHttp 4.12.0: callTimeout 100 мс при блокируемом DNS 700 мс завершился через 783 мс, InterruptedIOException, exit 0; это доказательство ограничения Q-04, не RED бизнес-AC. Полное тело HTTP читается под native timeout. One-shot POST запрещает повторные follow-up отправки, включая 503/421; самостоятельных retries нет.

## 4. Вопросы и ограничения

| ID | Решение, необходимое для продолжения | Статус |
|---|---|---|
| Q-01 | Прототипный wire-контракт трёх POST | Согласовано / закрыт |
| Q-02 | Deadline одной попытки сейчас; orchestration/устойчивый стенд позднее | Согласовано / закрыт |
| Q-03 | Canonical payload v1 и проверка снимка/hash вне простых DTO | Согласовано / закрыт |
| Q-04 | Вариант A: hostname разрешён; синхронный DNS вне жёсткого deadline, timeout ОС | Согласовано / закрыт, r2 |

Новая существенная развилка останавливает зависимую реализацию; архитектор фиксирует ответ, явное согласование разрешает продолжение без повторной команды. БД, FSM, чат e2e, модель и настоящий corr не входят в проверки P1.03. In-memory stub не доказывает сохранность после падения процесса.

## 5. Передача и приёмка

- Реализованы DTO/порты, MapStruct, canonical payload, fixtures/реестр, strict config, прототипная OpenAPI и Feign с ограничением r2; main подключает corr stub.
- Review: технический errorCode больше не становится бизнес-отказом; generated descriptions перенесены через стандартный allOf; one-shot тело запрещает follow-up повторы транспорта.
- Все T-01–07 и регрессия прошли; `git diff --check` — exit 0. Следующее действие: проверка и приёмка пользователем. Приёмка пользователя не получена; следующий шаг не запускался.
- Разработка готова к проверке: да. Пользователь принял шаг: нет. WBS/history/plans обновлены; P1.04 не запускался.
