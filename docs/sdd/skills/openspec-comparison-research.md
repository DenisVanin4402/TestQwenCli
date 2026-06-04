# Сравнение SDD/OpenSpec с альтернативами

Дата исследования: 2026-06-04.

Документ сравнивает OpenSpec-подход к Spec-Driven Development (SDD) с другими известными и набирающими популярность вариантами. Под SDD здесь понимается не OASIS Solution Deployment Descriptor, а разработка, где спецификация, контракт, сценарий или другой формализованный артефакт управляет реализацией, проверкой и ревью.

## 1. Краткий вывод

Для текущего проекта наиболее сильная позиция у локального folder-based SDD/OpenSpec workflow, который уже описан в `docs/sdd/SDD_DETAILS.md`: он позволяет использовать существующую папку документации как master specification, не требует переписывать все знание в один огромный файл и подходит для brownfield-сервисов с большим объемом документов.

Официальный OpenSpec от Fission-AI близок по философии: легкий Markdown workflow, change folder, proposal/specs/design/tasks, tool-agnostic slash commands. Но официальный вариант более продуктовый и CLI-ориентированный, а локальный вариант проекта жестче адаптирован под enterprise-документацию, manifest/navigation/coverage и сценарий, где аналитик меняет master-spec документы в отдельной ветке.

GitHub Spec Kit сильнее как стандартизированный, расширяемый и массовый SDD toolkit: у него больше комьюнити, интеграций, пресетов и фазовых проверок. Его минус для нашего кейса - больше церемоний и сильнее выраженная генерация SDD-артефактов с нуля, тогда как нам важнее подключать уже существующую документацию.

Kiro силен как IDE-first реализация SDD: requirements/design/tasks, steering, hooks, параллельное выполнение задач. Но это не переносимый репозиторный стандарт, а продуктовая среда; часть ценности находится внутри IDE, модели, биллинга и UX Kiro.

BMAD Method полезен, когда нужен широкий AI-driven agile process: роли PM/Architect/Developer/UX/Test Architect, PRD, architecture, story-centric implementation, guided workflows. Для строгого управления master specification сервиса он тяжелее OpenSpec и требует дисциплины вокруг большого количества ролей и артефактов.

BDD, OpenAPI/AsyncAPI, Pact, ADR и C4 не являются прямыми заменами OpenSpec. Их лучше рассматривать как специализированные слои внутри master specification: BDD для исполняемых бизнес-сценариев, OpenAPI/AsyncAPI для API/event contracts, Pact для consumer-driven contract testing, ADR для решений, C4 для архитектурных представлений.

## 2. Что сравнивалось

### 2.1. Локальный SDD/OpenSpec framework проекта

Текущая модель проекта:

- source of truth сервиса - folder-based master specification `openspec/<service-name>/`;
- служебный навигационный слой - `openspec/<service-name>/_sdd/manifest.yaml`, `navigation.md`, `coverage.md`, `stale-files.md`;
- изменения оформляются как `openspec/changes/<change-name>/change.md`;
- сложные изменения дополняются `design.md` и `tasks.md`;
- есть branch-diff workflow: `change.md` можно получить из git diff двух локальных refs по master-spec папке;
- OpenSpec CLI не обязателен, workflow работает через skills и файлы репозитория.

Главная идея: не заставлять команду переписывать существующую документацию в один большой Markdown. Вместо этого нужно индексировать и связывать уже существующие документы.

### 2.2. Официальный OpenSpec

Официальный OpenSpec позиционируется как легкий SDD framework для AI coding assistants. Он строится вокруг согласования намерения до кода, change folders и persistent context рядом с кодом. Документация указывает workflow: создать change, сгенерировать proposal/specs/design/tasks, реализовать и архивировать. OpenSpec заявляет поддержку 20+ AI assistants и установку через `npm install -g @fission-ai/openspec@latest`.

По данным GitHub на дату исследования: около 52.7k stars, MIT, TypeScript, последний релиз 2026-06-03.

Источники: [openspec.pro](https://openspec.pro/), [Fission-AI/OpenSpec](https://github.com/Fission-AI/OpenSpec).

### 2.3. GitHub Spec Kit

Spec Kit - toolkit для SDD от GitHub. Базовый процесс: Spec -> Plan -> Tasks -> Implement. Документация описывает работу через `/speckit.specify`, `/speckit.plan`, `/speckit.tasks`, `/speckit.implement`, а также дополнительные команды `/speckit.clarify`, `/speckit.analyze`, `/speckit.checklist`. Spec Kit поддерживает 30+ AI coding agent integrations, extensions, presets, workflows и project-local overrides.

По данным GitHub на дату исследования: около 108k stars, MIT, Python, последний релиз 2026-06-03. Официальный сайт называет 105+ extensions, 22 presets и 200+ contributors.

Источники: [Spec Kit docs](https://github.github.com/spec-kit/), [github/spec-kit](https://github.com/github/spec-kit).

### 2.4. Kiro

Kiro - agentic coding service/IDE от AWS. Он превращает prompt в specs, code, docs и tests, построен на Amazon Bedrock и использует несколько foundation models. В Kiro specs состоят из `requirements.md` или `bugfix.md`, `design.md`, `tasks.md`; workflow поддерживает feature specs, bugfix specs, requirements-first/design-first/quick plan, task execution UI и параллельное выполнение независимых задач. Steering files дают постоянное знание о проекте через Markdown-файлы, hooks автоматизируют действия агента по событиям.

Источники: [AWS Kiro documentation overview](https://aws.amazon.com/documentation-overview/kiro/), [Kiro Specs](https://kiro.dev/docs/specs/), [Kiro Steering](https://kiro.dev/docs/steering/), [Kiro Models](https://kiro.dev/docs/models/).

### 2.5. BMAD Method

BMAD Method - AI-driven agile development framework с большим набором specialized agents и workflows. В README акцент на scale-adaptive planning, agile workflows, 12+ domain experts, full lifecycle from brainstorming to deployment. BMAD полезен для формирования PRD, architecture, UX, test strategy и story-centric implementation.

По данным GitHub на дату исследования: около 48.6k stars, MIT, 36 releases, последний релиз 2026-05-25.

Источники: [bmad-code-org/BMAD-METHOD](https://github.com/bmad-code-org/BMAD-METHOD), [BMAD docs](https://docs.bmad-method.org/).

### 2.6. BDD/Gherkin/Cucumber

Cucumber поддерживает Behaviour-Driven Development через plain-text executable specifications. Gherkin делает текст достаточно структурированным для исполнения, `.feature` файлы обычно версионируются вместе с кодом, а step definitions связывают шаги сценария с программным кодом.

Это не полноценный SDD framework для всего жизненного цикла фичи, но сильный инструмент для исполняемых acceptance scenarios.

Источник: [Cucumber docs](https://cucumber.io/docs/).

### 2.7. OpenAPI, AsyncAPI и Pact

OpenAPI описывает HTTP API в машинно-читаемом формате: endpoints, methods, request/response, schemas, security, callbacks, webhooks и примеры.

AsyncAPI решает похожую задачу для message-driven APIs: протокол-агностичное описание каналов, messages, operations, servers и bindings.

Pact - code-first consumer-driven contract testing. В отличие от статической схемы, Pact contract создается из consumer tests и проверяется через provider verification; это contract by example.

Источники: [OpenAPI learning docs](https://learn.openapis.org/specification/), [AsyncAPI Specification 3.1.0](https://www.asyncapi.com/docs/reference/specification/latest), [Pact docs](https://docs.pact.io/), [How Pact works](https://docs.pact.io/getting_started/how_pact_works).

### 2.8. ADR, C4 и design docs

ADR фиксирует архитектурное решение, мотивацию, trade-offs и consequences. Набор ADR образует decision log.

C4 model дает иерархические архитектурные диаграммы: context, containers, components, code. Официальная документация подчеркивает, что не обязательно использовать все 4 уровня; context и container часто достаточны.

Это не SDD workflow сами по себе. Но они критически полезны как содержимое master specification и как материал для AI-агентов.

Источники: [ADR GitHub organization](https://adr.github.io/), [C4 model diagrams](https://c4model.com/diagrams).

### 2.9. Plandex и автономные coding agents с planning mode

Plandex - терминальный AI coding agent для больших задач, который умеет планировать и выполнять многошаговые изменения, работать с большими контекстами, держать AI-generated changes в review sandbox и давать настраиваемую автономность. Это не SDD framework в строгом смысле: основной центр тяжести - агентное планирование и применение code changes, а не репозиторный lifecycle спецификаций.

Источник: [plandex-ai/plandex](https://github.com/plandex-ai/plandex).

## 3. Сводная матрица

Оценки качественные: `++` сильная сторона, `+` подходит, `0` нейтрально/зависит от внедрения, `-` слабая сторона для данного критерия.

| Критерий | Локальный SDD/OpenSpec | Официальный OpenSpec | GitHub Spec Kit | Kiro | BMAD Method | BDD/Cucumber | OpenAPI/AsyncAPI/Pact | ADR/C4/design docs | Plandex/agents |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Brownfield и существующая документация | ++ | + | 0/+ | + | + | 0 | + | ++ | + |
| Master specification как папка | ++ | 0/+ | 0 | -/0 | 0 | - | 0/+ | ++ | - |
| Change lifecycle | ++ | ++ | + | + | + | - | - | - | 0 |
| Единый source of truth | + | + | + | 0/+ | 0/+ | 0 | + для контрактов | + для решений/архитектуры | - |
| Низкая церемониальность | + | ++ | 0 | + | - | 0 | 0 | + | + |
| Формальная строгость | + | 0/+ | ++ | + | + | ++ для сценариев | ++ для контрактов | + | 0 |
| Исполняемость/автопроверка | 0/+ | 0/+ | + | + | + | ++ | ++ | - | + |
| Трассируемость требований к реализации | ++ | + | ++ | + | + | + | + | + | 0 |
| AI-agent portability | ++ | ++ | ++ | -/0 | + | + | + | + | -/0 |
| Vendor lock-in | ++ | ++ | ++ | - | + | ++ | ++ | ++ | + |
| Интеграция с Git review | ++ | ++ | ++ | 0/+ | + | + | ++ | ++ | + |
| Поддержка слабых моделей | + | + | 0/+ | + за счет IDE UX | 0 | + | ++ | + | 0 |
| Поддержка больших сервисов | ++ при хорошем manifest | + | + | + | + | 0 | + | ++ | ++ |
| Простота старта | 0/+ | ++ | + | ++ | 0/- | + | 0 | + | + |
| Управление spec drift | ++ при refresh/coverage | + | + | 0/+ | 0/+ | 0 | ++ через CI | 0 | - |
| Безопасность и compliance | + | 0/+ | +/++ с extensions | + в enterprise | + с TEA/security | 0 | + | + | 0 |
| Командная масштабируемость | ++ | + | ++ | + | +/++ | + | ++ | + | 0 |
| Подходит для текущей цели проекта | ++ | + | + | 0/+ | 0/+ | дополняет | дополняет | дополняет | дополняет |

## 4. Детальное сравнение по критериям

### 4.1. Source of truth

Локальный SDD/OpenSpec делает source of truth не одним файлом, а папкой `openspec/<service-name>/`. Это важное отличие от многих SDD-подходов, где основной сценарий начинается с генерации нового spec file, PRD или набора шаблонных Markdown-артефактов.

Для enterprise/brownfield это практичнее: реальные сервисы часто уже имеют `workflow/`, `integrations/`, `api/`, `data/`, `security/`, OpenAPI, схемы, диаграммы, таблицы ошибок и эксплуатационные документы. Переписывать их в единый `service.md` дорого и опасно.

Spec Kit и официальный OpenSpec тоже хранят артефакты в репозитории, но их стандартный workflow сильнее ориентирован на SDD-generated artifacts. Kiro хранит specs и steering, но часть процесса привязана к Kiro UI. BDD/OpenAPI/AsyncAPI/Pact являются source of truth только для своих узких областей.

Вывод: для проекта, где уже есть документация по сервису, folder-based master specification является лучшим базовым решением.

### 4.2. Change lifecycle

OpenSpec-подход выигрывает там, где изменение должно проходить явные состояния:

1. сформулировать изменение;
2. согласовать смысл;
3. спроектировать реализацию;
4. выполнить задачи;
5. проверить, что код и спецификация не разошлись;
6. архивировать историю.

Официальный OpenSpec делает это через change folder и archive. Локальный workflow добавляет более строгую модель статусов и branch-diff сценарий. Spec Kit близок по дисциплине, но строит процесс вокруг фаз `specify/plan/tasks/implement`, а не вокруг master-spec delta от существующих документов. Kiro дает похожие фазы внутри IDE. BMAD переносит lifecycle в agile/story process.

Вывод: если основной объект ревью - "что именно поменялось в требованиях", OpenSpec-подход удобнее BMAD и обычных coding agents.

### 4.3. Работа с существующей документацией

Это главный критерий для текущего проекта.

Локальный workflow явно решает проблему: master spec может быть произвольной папкой, а `_sdd/manifest.yaml` и `navigation.md` дают агенту карту чтения. Это лучше масштабируется, чем "прочитай весь docs recursively", и лучше подходит слабым моделям, потому что агент получает index, priorities, links, stale warnings и coverage gaps.

Официальный OpenSpec хорошо подходит для incremental SDD, но не делает folder-based enterprise documentation главным продуктовым центром. Spec Kit настраивается через templates/presets/extensions, но потребует больше адаптации. Kiro steering поддерживает Markdown knowledge и file references, но это steering для IDE, а не нейтральный репозиторный manifest.

Вывод: локальная модель `openspec/<service>/_sdd` является обоснованным расширением относительно mainstream SDD tools.

### 4.4. Git и branch-diff

Локальный OpenSpec workflow имеет отдельное преимущество: аналитик может изменить master specification в своей ветке, а разработчик создает `change.md` из git diff между `base_ref` и `analyst_ref` без checkout веток.

Это ближе к реальному процессу, где аналитики и разработчики работают в разных ветках, а source-of-truth docs уже изменены до разработки. В официальных SDD tools чаще предполагается, что change/proposal создается до модификации spec или вместе с ней в одном workflow.

Риск такого подхода: `change.md` нельзя строить механически только из patch. Нужно подтягивать соседний контекст через manifest, иначе получится diff summary, а не аналитический change. Именно поэтому нужны `context-files.yaml`, `coverage.md`, `stale-files.md` и явный warning при устаревшем manifest.

Вывод: branch-diff workflow - сильная локальная инновация, но качество зависит от manifest и правил выбора контекста.

### 4.5. Формальная строгость и исполняемость

Ни один Markdown-first SDD framework сам по себе не заменяет исполняемые проверки. Здесь лучшие специализированные инструменты:

- BDD/Cucumber - для executable business scenarios;
- OpenAPI/AsyncAPI - для machine-readable API/event contracts;
- Pact - для consumer-driven integration contract tests;
- unit/integration/e2e tests - для подтверждения реализации;
- static analysis/security scanners - для non-functional constraints.

Spec Kit сильнее многих lightweight workflows за счет checklists, analyze, TDD ordering и validation phases. Kiro имеет встроенный task execution и hooks. Локальный OpenSpec должен явно требовать verification section в `tasks.md` и связывать acceptance criteria с тестами/контрактами.

Вывод: OpenSpec должен быть orchestration layer, а не заменой тестов и контрактов.

### 4.6. Vendor lock-in

Наименее привязаны к вендору:

- локальный SDD/OpenSpec на Markdown/skills;
- официальный OpenSpec;
- GitHub Spec Kit;
- BDD/OpenAPI/AsyncAPI/Pact/ADR/C4 как открытые или переносимые практики.

Kiro сильнее привязан к продуктовой среде, аккаунту, моделям, биллингу и UX. Это не плохо, если команда выбирает Kiro как IDE standard, но риск переносимости выше.

Plandex переносим как open-source CLI, но это не specification governance layer: при смене инструмента нужно отдельно сохранять и дисциплинировать планы.

Вывод: для репозиторного стандарта лучше Markdown/files-first, для скорости конкретной команды можно добавлять IDE/agent tooling поверх.

### 4.7. Поддержка слабых и сильных моделей

Слабым моделям вредят:

- огромные single-file specs;
- неструктурированное рекурсивное чтение всей документации;
- скрытые требования в длинной истории чата;
- отсутствие явных acceptance criteria;
- отсутствие карты релевантных файлов.

Локальный OpenSpec снижает этот риск через manifest/navigation/coverage. Spec Kit снижает риск через шаблоны, phases и checklists. Kiro снижает риск через IDE workflow, steering и model routing. BMAD снижает риск через роли и guided workflows, но может перегрузить слабую модель объемом артефактов.

Сильные модели лучше переносят объемный контекст, но все равно выигрывают от явной структуры. Исследование Spec Kit Agents указывает, что SDD-агенты в больших изменяющихся репозиториях могут оставаться "context blind"; grounding hooks и validation hooks улучшают качество и сохраняют совместимость тестов. Это подтверждает необходимость read-only research и контекстных проверок в локальном workflow.

Источник: [Spec Kit Agents: Context-Grounded Agentic Workflows](https://arxiv.org/abs/2604.05278).

### 4.8. Безопасность и compliance

Markdown SDD повышает шансы, что security constraints будут видны до реализации, но без enforcement это остается документацией. Нужны:

- security requirements в master spec;
- non-negotiable constraints в change/design/tasks;
- тесты и статические проверки;
- traceability от требования до кода;
- ревью spec delta до code diff.

Исследование Constitutional SDD предлагает явно кодировать security constraints в specification layer и показывает преимущество proactive security specification над reactive verification. Практический вывод для локального OpenSpec: security и compliance должны быть отдельными секциями в master spec и обязательными чеклистами в `change.md`/`tasks.md`.

Источник: [Constitutional Spec-Driven Development](https://arxiv.org/abs/2602.02584).

### 4.9. Стоимость церемоний

OpenSpec lightweight выигрывает у BMAD и тяжелого Spec Kit, когда:

- изменение небольшое;
- есть хороший существующий контекст;
- команда хочет быстро согласовать delta;
- не нужен полный PRD/UX/architecture cycle.

Spec Kit и BMAD выигрывают, когда:

- продуктовая неопределенность высокая;
- нужно прогнать discovery, PRD, architecture, UX, test strategy;
- команда большая и нужна одинаковая дисциплина для многих участников;
- нужно много quality gates.

Kiro выигрывает, когда команда готова принять IDE-first workflow и хочет UX для specs/tasks/hooks без самостоятельной сборки процесса.

Вывод: церемонии должны масштабироваться по риску. Для локального workflow это означает режимы `quick`, `medium`, `full` для propose/design/research.

### 4.10. Масштабирование на большие репозитории

Большие репозитории ломают SDD не только объемом текста. Главные проблемы:

- агент не знает, какие файлы релевантны;
- документация устаревает;
- локальное окружение сложно поднять;
- dependency resolution съедает время;
- требования не трассируются к тестам;
- модель путает существующий API с желаемым API.

Исследование GitTaskBench показывает, что реальные repo-aware coding tasks остаются сложными для агентов; существенная часть failures связана с окружением и зависимостями. Поэтому локальный SDD не должен ограничиваться `change.md`: нужны research artifacts, environment notes, verification runbook и явные команды сборки/тестов.

Источник: [GitTaskBench](https://arxiv.org/abs/2508.18993).

## 5. Подробные плюсы и минусы подходов

### 5.1. Локальный SDD/OpenSpec

Плюсы:

- лучший fit для текущего проекта и существующей folder-based документации;
- нейтрален к AI tool и не требует OpenSpec CLI;
- поддерживает master spec как произвольную папку;
- учитывает stale docs, coverage и navigation;
- имеет branch-diff workflow по локальным git refs;
- хорошо ложится на PR review: ревьюится не только code diff, но и intent/spec delta;
- можно подключать OpenAPI, AsyncAPI, ADR, C4, BDD как содержимое master spec.

Минусы:

- требует собственной дисциплины и поддержки skills;
- меньше внешнего комьюнити и готовых integrations, чем у Spec Kit/OpenSpec/Kiro;
- качество зависит от manifest и правил refresh;
- нужно явно проектировать validators, иначе `coverage.md` и `stale-files.md` станут пассивной документацией;
- без хороших шаблонов возможна неоднородность `change.md` между командами.

Лучше всего подходит:

- brownfield-сервисы;
- проекты с уже существующей документацией;
- команды, где аналитик и разработчик работают через Git branches;
- enterprise-интеграции с API/event/data/security документацией.

### 5.2. Официальный OpenSpec

Плюсы:

- легкий старт;
- понятная структура `openspec/changes/<change>/`;
- proposal/specs/design/tasks/archive lifecycle;
- tool-agnostic slash commands;
- хороший fit для AI coding assistants;
- заметная популярность и активность.

Минусы:

- официальный workflow не полностью решает проблему подключения произвольной enterprise documentation folder;
- self-described comparison с альтернативами стоит проверять на практике;
- CLI/profile/update layer может быть лишним для локального skill-first процесса;
- слабее, чем Spec Kit, по ecosystem extensions/presets.

Лучше всего подходит:

- solo/team AI-assisted development;
- проекты, где нет тяжелого legacy docs layer;
- команды, которым нужен lightweight SDD без IDE lock-in.

### 5.3. GitHub Spec Kit

Плюсы:

- сильный открытый ecosystem;
- 30+ integrations;
- extensions, presets, workflows, project-local overrides;
- явные phases и quality checks;
- хорошая стандартизация для команд;
- сильная позиция по community adoption.

Минусы:

- больше церемоний и артефактов;
- Python/CLI setup;
- может быть избыточен для быстрых изменений;
- при слабой дисциплине превращается в генератор Markdown без реального source-of-truth;
- подключение существующей документационной папки потребует адаптации templates/presets.

Лучше всего подходит:

- организации, которым нужен универсальный SDD toolkit;
- greenfield или semi-greenfield проекты;
- команды, которые готовы инвестировать в presets/extensions и governance.

### 5.4. Kiro

Плюсы:

- удобный IDE-first workflow;
- specs/tasks UI;
- feature и bugfix specs;
- requirements-first, design-first, quick plan;
- steering files и hooks;
- task execution с параллельными независимыми задачами;
- model routing и разные модели в одном продукте.

Минусы:

- выше vendor/product lock-in;
- часть workflow живет в Kiro UX, а не только в репозитории;
- переносимость на Codex/Claude Code/Cursor требует дополнительной дисциплины;
- модельный и региональный availability зависит от Kiro/AWS/provider rules;
- для строгого Git-first process может быть менее прозрачным, чем Markdown-only workflow.

Лучше всего подходит:

- команды, готовые стандартизироваться на Kiro;
- разработчики, которым нужен UX поверх specs/tasks;
- быстрый structured implementation внутри одной IDE.

### 5.5. BMAD Method

Плюсы:

- полный AI-driven agile lifecycle;
- специализированные роли: PM, Analyst, Architect, Developer, UX, Test Architect и другие;
- хорошо подходит для discovery, PRD, architecture, UX, test strategy;
- scale-adaptive planning;
- активное сообщество и высокая популярность.

Минусы:

- тяжелее OpenSpec;
- выше learning curve;
- риск перегрузить проект документами и ролями;
- не является специализированным master-spec delta framework;
- для небольших изменений может быть избыточен.

Лучше всего подходит:

- новые продукты;
- сложные фичи с высокой неопределенностью;
- команды, которым нужен AI-assisted agile operating model, а не только spec governance.

### 5.6. BDD/Cucumber

Плюсы:

- executable specifications;
- хорошо связывает business language и automated tests;
- `.feature` файлы можно хранить в Git;
- полезно для acceptance criteria и регрессионного поведения.

Минусы:

- не описывает архитектуру, данные, интеграции и технический design целиком;
- требует поддержки step definitions;
- может стать громоздким, если пытаться описывать все требования через Gherkin;
- не решает change lifecycle.

Лучше всего подходит:

- критичные пользовательские сценарии;
- acceptance tests;
- регрессионные правила, понятные бизнесу.

### 5.7. OpenAPI/AsyncAPI/Pact

Плюсы:

- машинно-читаемые contracts;
- strong fit для API/event integration;
- можно валидировать в CI;
- можно генерировать документацию, mock/stub, clients/servers;
- Pact проверяет реальные consumer-provider expectations.

Минусы:

- покрывает только интерфейс/сообщения, а не всю бизнес-логику;
- OpenAPI/AsyncAPI могут устаревать без contract tests;
- Pact требует зрелости CI и дисциплины consumer/provider verification;
- не заменяет change/design/tasks.

Лучше всего подходит:

- external/internal API contracts;
- event-driven integrations;
- microservices compatibility;
- приемочные критерии по интеграциям.

### 5.8. ADR/C4/design docs

Плюсы:

- фиксируют архитектурный контекст и причины решений;
- хорошо хранятся в Git;
- C4 помогает быстро понять систему на разных уровнях;
- ADR снижает потерю исторического знания;
- отлично дополняют master specification.

Минусы:

- сами по себе не исполняются;
- легко устаревают;
- не задают end-to-end change workflow;
- требуют культуры обновления при изменениях.

Лучше всего подходит:

- архитектурная память;
- onboarding;
- ревью trade-offs;
- связка `change.md` -> affected ADR/C4 docs.

### 5.9. Plandex и planning agents

Плюсы:

- полезен для больших многофайловых задач;
- strong planning/execution loop;
- review sandbox;
- работает с разными model providers;
- хорош для автономной реализации после согласованного плана.

Минусы:

- не является SDD governance framework;
- plan agent не равен master specification;
- менее удобен для аналитического PR review требований;
- Windows support через WSL, что может быть ограничением для текущего окружения.

Лучше всего подходит:

- реализация крупных изменений после того, как requirements/design уже согласованы;
- альтернативный executor поверх OpenSpec/Spec Kit/BMAD artifacts.

## 6. Рекомендации для текущего проекта

### 6.1. Базовый выбор

Использовать локальный folder-based SDD/OpenSpec workflow как основной стандарт проекта.

Причины:

- он уже соответствует текущей потребности: master specification как папка;
- он лучше работает с существующими документами;
- он Git-first и не требует vendor IDE;
- он допускает branch-diff от ветки аналитика;
- он совместим с OpenAPI, AsyncAPI, ADR, C4, BDD и тестами.

### 6.2. Что взять из OpenSpec official

Взять:

- change folder lifecycle;
- явное согласование intent перед code;
- archive completed changes;
- tool-agnostic commands/skills;
- минимизацию церемоний.

Не копировать слепо:

- зависимость от CLI;
- single expected artifact flow, если у сервиса уже есть своя документационная структура;
- упрощенное сравнение с альтернативами без учета enterprise docs.

### 6.3. Что взять из Spec Kit

Взять:

- quality checklists;
- clarify/analyze phase для underspecified requirements;
- project-local overrides/presets как идею;
- строгую prerequisite validation перед implementation;
- TDD ordering в `tasks.md`, когда применимо.

Не копировать слепо:

- тяжелую фазовую модель для каждого изменения;
- генерацию большого количества Markdown без связи с master spec;
- зависимость от конкретной CLI-структуры `.specify`.

### 6.4. Что взять из Kiro

Взять:

- steering-like files как идею focused context;
- hooks для автоматического refresh/verify;
- параллелизацию независимых задач;
- разделение feature specs и bugfix specs;
- quick plan для малых изменений.

Не копировать слепо:

- IDE-first lock-in;
- скрытие важной логики workflow в UI;
- зависимость от Kiro model availability.

### 6.5. Что взять из BMAD

Взять:

- scale-adaptive planning;
- отдельные роли для анализа, архитектуры, тестирования и implementation;
- story-centric implementation для крупных эпиков;
- test architect perspective для risk-based verification.

Не копировать слепо:

- полный набор ролей для малых изменений;
- PRD/architecture/UX ceremony там, где достаточно change/design/tasks;
- "один большой процесс на все случаи".

### 6.6. Что подключить как обязательные специализированные слои

Для сервисов с API:

- OpenAPI в `openspec/<service>/api/`;
- проверка OpenAPI в CI;
- связь endpoints с `change.md`.

Для event-driven integrations:

- AsyncAPI в `openspec/<service>/integrations/` или `api/events/`;
- явные schemas, channels, operations;
- compatibility checks.

Для межсервисных контрактов:

- Pact или другой contract testing layer;
- consumer/provider verification в CI;
- ссылки из `change.md` на affected contracts.

Для бизнес-сценариев:

- BDD/Gherkin только для реально важных acceptance scenarios;
- не описывать через Gherkin всю документацию.

Для архитектуры:

- C4 context/container минимум;
- ADR для значимых решений;
- links между ADR/C4 и `change.md`.

## 7. Предлагаемая целевая модель

```text
openspec/
  <service-name>/
    _sdd/
      manifest.yaml
      navigation.md
      coverage.md
      stale-files.md
    workflow/
    api/
      openapi.yaml
    integrations/
      asyncapi.yaml
      pact/
    data/
    security/
    architecture/
      c4/
      adr/
    testing/
      bdd/
  changes/
    <change-name>/
      change.md
      design.md
      tasks.md
      .research/
      .spec-diff/
```

Обязательные инварианты:

1. Агент сначала читает `_sdd/navigation.md`, затем `_sdd/manifest.yaml`.
2. Если manifest устарел, агент предупреждает перед созданием `change.md`.
3. `change.md` фиксирует affected docs, affected contracts, risks, acceptance criteria, open questions.
4. `design.md` ссылается на конкретные code modules и master-spec sources.
5. `tasks.md` содержит verification block: tests, contract checks, docs refresh, manual checks.
6. Branch-diff change сохраняет `.spec-diff/refs.txt`, `patch.diff`, `changed-files.yaml`, `context-files.yaml`.
7. Завершенный change архивируется, а master spec проверяется на drift.

## 8. Критерии выбора подхода по типу задачи

| Тип задачи | Рекомендуемый подход |
|---|---|
| Малый bugfix без изменения требований | Direct coding + тесты; OpenSpec не обязателен |
| Bugfix с риском регрессии | Локальный OpenSpec `change.md` + `tasks.md`; возможно Kiro bugfix spec как inspiration |
| API change | Локальный OpenSpec + OpenAPI diff/validation + contract tests |
| Event integration change | Локальный OpenSpec + AsyncAPI + consumer/producer verification |
| Крупная feature в brownfield сервисе | Локальный OpenSpec + structured research + design/tasks |
| Новый продукт/модуль с высокой неопределенностью | BMAD или Spec Kit для discovery/planning, затем локальный OpenSpec для repo governance |
| Быстрый прототип | Kiro quick plan или official OpenSpec lightweight flow |
| Архитектурное решение | ADR + C4 update + OpenSpec change |
| Критичный бизнес-сценарий | OpenSpec acceptance + BDD/Cucumber scenario |
| Большая реализация после согласованного plan | OpenSpec/Spec Kit artifacts + Plandex/Codex/Claude Code executor |

## 9. Риски внедрения локального OpenSpec и меры

| Риск | Почему важно | Мера |
|---|---|---|
| Manifest устаревает | Агент выбирает неправильный контекст | `stale-files.md`, hash, refresh перед propose/design |
| `change.md` становится пересказом diff | Теряется аналитический смысл | Обязательный выбор context docs через manifest |
| Слишком много документов | Слабая модель теряет фокус | read priority, navigation, scoped research |
| Нет executable checks | SDD превращается в документацию ради документации | tests/contracts/checklists в `tasks.md` |
| Аналитик и разработчик расходятся по веткам | `change.md` не соответствует source docs | refs metadata, merge-base, diff mode, verify source |
| Security остается декларацией | AI реализует функционально, но небезопасно | security constraints + acceptance + static checks |
| Артефакты не архивируются | Активные changes становятся мусором | обязательный archive step |
| Избыточная церемония | Команда перестает использовать процесс | quick/medium/full режимы |
| Vendor/tool drift | Workflow работает только в одном агенте | Markdown-first, skills-first, no required CLI |

## 10. Практический рейтинг для проекта

| Место | Подход | Оценка fit | Почему |
|---:|---|---:|---|
| 1 | Локальный folder-based SDD/OpenSpec | 10/10 | Прямо решает задачу master-spec папки и branch-diff workflow |
| 2 | Официальный OpenSpec | 8/10 | Близкая философия, легкий lifecycle, но меньше заточен под существующую enterprise docs folder |
| 3 | GitHub Spec Kit | 8/10 | Сильная экосистема и проверки, но больше церемоний |
| 4 | Kiro | 7/10 | Отличный UX для specs/tasks, но IDE/product lock-in |
| 5 | BMAD Method | 7/10 | Силен для product/agile lifecycle, тяжелее для spec governance |
| 6 | OpenAPI/AsyncAPI/Pact | 7/10 как слой | Обязательны для контрактов, но не заменяют OpenSpec |
| 7 | ADR/C4 | 7/10 как слой | Обязательны для архитектурной памяти, но не workflow |
| 8 | BDD/Cucumber | 6/10 как слой | Полезен для acceptance, опасен при попытке описать все |
| 9 | Plandex/автономные agents | 5/10 как SDD, 8/10 как executor | Хорош для реализации, но не для управления спецификациями |

## 11. Итоговая позиция

Локальный SDD/OpenSpec стоит развивать не как копию официального OpenSpec, Spec Kit, Kiro или BMAD, а как репозиторный governance layer для требований и документации сервиса.

Целевая формула:

```text
Folder-based master spec
+ manifest/navigation/coverage/stale diagnostics
+ change.md как аналитический delta artifact
+ design/tasks как engineering bridge
+ Git branch-diff для ветки аналитика
+ executable verification через tests/contracts
+ ADR/C4/OpenAPI/AsyncAPI/BDD как специализированные вложенные слои
```

Такой вариант лучше всего соответствует проблеме проекта: большие brownfield-сервисы, слабые и сильные модели, уже существующая документация, работа аналитика в отдельной ветке и необходимость получать не механический diff, а продуманный change-request.

## 12. Источники

- [Локальная детализация SDD/OpenSpec framework](../SDD_DETAILS.md)
- [Официальный OpenSpec сайт](https://openspec.pro/)
- [Fission-AI/OpenSpec GitHub](https://github.com/Fission-AI/OpenSpec)
- [GitHub Spec Kit documentation](https://github.github.com/spec-kit/)
- [github/spec-kit GitHub](https://github.com/github/spec-kit)
- [AWS Kiro documentation overview](https://aws.amazon.com/documentation-overview/kiro/)
- [Kiro Specs](https://kiro.dev/docs/specs/)
- [Kiro Steering](https://kiro.dev/docs/steering/)
- [Kiro Models](https://kiro.dev/docs/models/)
- [BMAD Method GitHub](https://github.com/bmad-code-org/BMAD-METHOD)
- [BMAD Method docs](https://docs.bmad-method.org/)
- [Cucumber documentation](https://cucumber.io/docs/)
- [OpenAPI learning documentation](https://learn.openapis.org/specification/)
- [AsyncAPI Specification](https://www.asyncapi.com/docs/reference/specification/latest)
- [Pact documentation](https://docs.pact.io/)
- [How Pact works](https://docs.pact.io/getting_started/how_pact_works)
- [ADR GitHub organization](https://adr.github.io/)
- [C4 model diagrams](https://c4model.com/diagrams)
- [Plandex GitHub](https://github.com/plandex-ai/plandex)
- [Spec Kit Agents: Context-Grounded Agentic Workflows](https://arxiv.org/abs/2604.05278)
- [Spec-Driven Development: From Code to Contract in the Age of AI Coding Assistants](https://arxiv.org/abs/2602.00180)
- [Constitutional Spec-Driven Development](https://arxiv.org/abs/2602.02584)
- [GitTaskBench](https://arxiv.org/abs/2508.18993)
