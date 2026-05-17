# SDD Gates

Gates нужны, чтобы остановить работу до того, как неопределенность попадет в код.

## Spec Gate

Проходные условия:

- проблема, пользователи, цели и non-goals описаны;
- требования имеют ID `REQ-*`;
- acceptance criteria имеют ID `AC-*`;
- критические требования не содержат незакрытых уточнений;
- edge cases и NFR зафиксированы;
- scope изменения явно ограничен.

## Plan Gate

Проходные условия:

- перечислены затронутые модули и файлы;
- описаны публичные контракты и обратная совместимость;
- указана стратегия тестирования;
- риски и trade-offs записаны;
- ADR создан, если решение значимое;
- команды проверки понятны.

## Task Gate

Проходные условия:

- каждая задача ссылается на requirement IDs;
- ownership scope ограничен файлами или модулями;
- зависимости между задачами понятны;
- есть критерий завершения и проверка;
- задачи можно выполнять малыми партиями.

## Implementation Gate

Проходные условия:

- изменены только файлы в согласованном scope;
- тесты добавлены или обновлены там, где меняется поведение;
- релевантные команды проверки запущены;
- результат проверок записан в `work-log.md`;
- остаточные риски явно перечислены.

## Review Gate

Проходные условия:

- reviewer проверил соответствие spec, tests и code;
- security/privacy gaps отсутствуют или приняты явно;
- traceability matrix закрыта для критических требований;
- findings исправлены или записаны как follow-up;
- `handoff.md` не содержит скрытых блокеров.

## Diff Intake Gate

Проходные условия:

- base branch и analyst branch/MR известны;
- diff воспроизводим командой или ссылкой на MR;
- измененные файлы классифицированы как markdown, openapi, dbml или other;
- несвязанные изменения отделены в `diff-map.md`;
- scope не расширяется за пределы business request и analyst diff.

## Source Context Gate

Проходные условия:

- для каждого source snippet есть точная ссылка на файл, section, OpenAPI path/schema или DBML entity;
- в `source-context.md` нет больших копий документации без необходимости;
- indirect context обоснован через impact map;
- секреты и приватные данные не перенесены в SDD-артефакты.

## Impact Gate

Проходные условия:

- direct impacts перечислены для каждого значимого changed semantic block;
- indirect impacts имеют объяснение, почему они затронуты;
- OpenAPI и DBML impacts вынесены отдельно;
- requirement candidates связаны с источниками.

## Contract/Data Gate

Проходные условия:

- изменения endpoints, schemas, responses и security отражены в `contracts/openapi-diff.md`;
- изменения tables, columns, refs и enums отражены в `contracts/dbml-diff.md`;
- contract/data questions явно записаны;
- compatibility и migration risks отмечены.

## Synthesis Gate

Проходные условия:

- каждый `REQ-*` связан с business requirement или source diff;
- каждый `AC-*` связан с `REQ-*`;
- `spec.md` содержит только change-context;
- open questions не блокируют планирование или явно остановили работу на clarification gate.

## Drift Gate

Проходные условия:

- synthesized spec не содержит поведения, которого нет в business requirements, analyst diff или source context;
- новые предположения записаны как open questions;
- conflict между Markdown, OpenAPI и DBML вынесен в `impact-map.md` или `requirements.md`.
