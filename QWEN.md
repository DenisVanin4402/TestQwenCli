# Проект: TestQwenCli

Веб-приложение на Spring Boot, созданное как Maven-проект для тестирования рабочих процессов Qwen CLI и практик Specification-Driven Development (SDD).

## Обязательное требование: язык общения и документации

**Все общение, комментарии, документация и описания в этом проекте ведутся на русском языке.** Это обязательное правило, а не рекомендация.

- Исключение: код, имена классов, методов, переменных, конфигурационные ключи и технические идентификаторы остаются на английском (по стандартам индустрии).
- Комментарии в коде — по возможности на русском, если они содержат бизнес-логику или пояснения.
- Коммит-сообщения — на русском языке.
- Документация (`README.md`, `QWEN.md`, спецификации, ADR, планы) — строго на русском.

## Стек технологий

| Компонент      | Версия / Подробности                    |
|---------------|-----------------------------------------|
| Java          | 21                                      |
| Spring Boot   | 3.4.3 (parent BOM)                      |
| Система сборки| Maven 3.9+                              |
| Тестирование  | JUnit 5, Spring Boot Test, MockMvc      |
| Пакет         | `com.example.testqwencli`               |

## Сборка и запуск

### Требования

- JDK 21, установленный и доступный в `PATH`
- Maven 3.9+ (`mvn`)

### Команды

```bash
# Запустить все тесты
mvn test

# Запустить приложение
mvn spring-boot:run

# Собрать JAR-пакет
mvn package

# Очистить артефакты сборки
mvn clean
```

### IntelliJ IDEA

1. Открыть корневую директорию репозитория.
2. При появлении запроса — импортировать/перезагрузить `pom.xml` как Maven-проект.
3. Запустить `TestQwenCliApplication.main()` или использовать Maven-запуск.

## Структура проекта

```
TestQwenCli/
├── pom.xml                          # Maven-конфигурация, родитель — Spring Boot 3.4.3
├── src/
│   ├── main/
│   │   ├── java/com/example/testqwencli/
│   │   │   ├── TestQwenCliApplication.java   # Точка входа Spring Boot
│   │   │   └── HomeController.java           # REST-контроллер (GET /)
│   │   └── resources/
│   │       └── application.properties        # Конфигурация приложения
│   └── test/java/com/example/testqwencli/
│       └── TestQwenCliApplicationTests.java  # Интеграционные тесты с MockMvc
├── docs/
│   ├── agentic-sdd-research.md      # Исследование SDD для AI coding-агентов
│   └── sdd-plan.md                  # План внедрения SDD-процесса
├── target/                          # Артефакты сборки (игнорируется в git)
└── .idea/                           # Файлы проекта IntelliJ (игнорируется в git)
```

## Обзор приложения

### Текущие функции

- **Корневой эндпоинт** (`GET /`): возвращает `"TestQwenCli is running"`
- **Автоматическая конфигурация Spring Boot**: стандартная настройка веб-приложения
- **Базовое покрытие тестами**: тест загрузки контекста и проверка эндпоинта

### Контроллер: `HomeController`

- Класс с package-private видимостью, аннотация `@RestController`
- Один эндпоинт: `GET /` → `"TestQwenCli is running"`

### Тесты: `TestQwenCliApplicationTests`

- Используется `@SpringBootTest` + `@AutoConfigureMockMvc`
- Проверяет загрузку контекста приложения
- Проверяет, что корневой эндпоинт возвращает корректный статус и тело ответа

## Соглашения по разработке

### Стиль кода

- Стандартные соглашения Java по именованию (PascalCase для классов, camelCase для методов)
- Package-private видимость там, где не требуется `public`
- Явные модификаторы `public` на тестовых классах или контроллерах используются только при необходимости
- Аннотации Spring Boot используются без лишнего boilerplate

### Структура пакетов

- Весь код находится в пакете `com.example.testqwencli`
- Контроллеры, главный класс и тесты расположены в базовом пакете (допустимо для небольших проектов)

### Зависимости

- Использовать Spring Boot starters вместо отдельных библиотек
- Добавлять новые зависимости только при явном обосновании
- Минимизировать production-зависимости (`spring-boot-starter-web`)

### Практики работы с Git

- `target/`, `.idea/`, `*.class`, `*.log` исключены из git (см. `.gitignore`)
- Коммит-сообщения ссылаются на ID требований при реализации spec-driven-фич
- **Все коммит-сообщения — на русском языке**

## Тестирование

### Паттерны тестов

```java
@SpringBootTest
@AutoConfigureMockMvc
class ExampleTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointReturnsExpectedResponse() throws Exception {
        mockMvc.perform(get("/path"))
                .andExpect(status().isOk())
                .andExpect(content().string("expected"));
    }
}
```

### Запуск тестов

```bash
mvn test                          # Все тесты
mvn test -Dtest=ClassName         # Один класс тестов
```

## Контекст агентской разработки

Этот проект служит **полигоном для Specification-Driven Development (SDD)** с использованием AI coding-агентов.

### Исследования и планирование

- **`docs/agentic-sdd-research.md`**: Комплексное исследование архитектуры SDD, управления контекстом, мультиагентных моделей, навыков/команд,验证-ворон и антипаттернов для AI agent-разработки.
- **`docs/sdd-plan.md`**: План внедрения SDD в проект в 5 этапов:
  - **Фаза 0**: Bootstrap (спека SDD для самого себя, governance-документы)
  - **Фаза 1**: Шаблоны артефактов (`docs/specs/_template/`)
  - **Фаза 2**: Точки входа Qwen CLI (`.qwen/commands/sdd/`, `.qwen/skills/`)
  - **Фаза 3**: Лайтвес верификация (spec lint, проверка трассировки)
  - **Фаза 4**: Пилот на реальной задаче
  - **Фаза 5**: Расширение мультиагентных ролей

### Принципы SDD (ключевые выводы исследования)

- Спецификации — источник истины, а не история чата
- Агенты получают **контекстные пакеты** (а не весь репозиторий)
- Разные уровни: `AGENTS.md`, `QWEN.md`, ADR, спецификации, команды, навыки, work log-и
- Человеческие гейты на спецификациях, архитектурных решениях и приёмке
- Безопасность, нефункциональные требования и критерии приёмки определяются до реализации
- Верификация: матрица трассировки, test-first для критических путей

## Рабочий SDD pipeline

Для существенных задач используйте цепочку:

```text
/sdd:specify -> /sdd:clarify -> /sdd:plan -> /sdd:tasks -> /sdd:implement -> /sdd:review
```

Если входом является MR системного аналитика с изменениями в Markdown-документации, OpenAPI или DBML, используйте diff-driven цепочку:

```text
/sdd:intake-diff -> /sdd:impact-map -> /sdd:synthesize-spec -> /sdd:clarify -> /sdd:plan -> /sdd:tasks -> /sdd:implement -> /sdd:review
```

Файловый источник истины находится в `docs/specs/<id>-<slug>/`. Общие правила и gates находятся в `docs/sdd/`, шаблон новой задачи — в `docs/specs/_template/`.

### Qwen commands

- `.qwen/commands/sdd/specify.md` - создает или обновляет спецификацию.
- `.qwen/commands/sdd/clarify.md` - закрывает критические уточнения.
- `.qwen/commands/sdd/intake-diff.md` - строит intake и diff map по MR аналитика.
- `.qwen/commands/sdd/impact-map.md` - строит impact map и source context.
- `.qwen/commands/sdd/synthesize-spec.md` - синтезирует change-spec из diff и контекста.
- `.qwen/commands/sdd/plan.md` - формирует технический план.
- `.qwen/commands/sdd/tasks.md` - строит task graph с ownership.
- `.qwen/commands/sdd/implement.md` - реализует bounded task.
- `.qwen/commands/sdd/review.md` - выполняет read-only review.

### Qwen skills

- `sdd-spec-review` - проверка запроса или diff против спеки.
- `sdd-plan` - техническое планирование.
- `sdd-task-slice` - нарезка задач.
- `sdd-test-gap` - поиск gaps в traceability и тестах.
- `sdd-review` - независимый review.
- `sdd-diff-intake` - анализ analyst MR diff.
- `sdd-impact-map` - direct/indirect impact analysis.
- `sdd-openapi-diff` - анализ OpenAPI changes.
- `sdd-dbml-diff` - анализ DBML changes.
- `sdd-spec-synthesis` - сборка synthesized change-spec.

### Qwen agents

- `sdd-orchestrator` - держит фазу, gates и context packet.
- `sdd-spec-writer` - пишет требования и acceptance criteria.
- `sdd-planner` - готовит plan, research и ADR-кандидаты.
- `sdd-builder` - реализует bounded task.
- `sdd-test-engineer` - закрывает тестовые gaps.
- `sdd-reviewer` - read-only review.
- `sdd-security-reviewer` - read-only security/privacy review.
- `sdd-doc-diff-analyst` - анализ Markdown diff.
- `sdd-contract-analyst` - анализ OpenAPI diff.
- `sdd-data-model-analyst` - анализ DBML diff.
- `sdd-spec-synthesizer` - синтез итоговой change-spec.

### Context packet

Перед реализацией или review используйте короткий context packet:

```md
# Context Packet

## Goal
<REQ/TASK и цель>

## Source of truth
- Spec: docs/specs/<id>-<slug>/spec.md
- Plan: docs/specs/<id>-<slug>/plan.md
- Tasks: docs/specs/<id>-<slug>/tasks.md

## Acceptance criteria
- AC-...

## Constraints
- <ownership, contracts, security>

## Relevant files
- <path>

## Verification commands
- mvn test
```

## Конфигурация

### Maven (pom.xml)

- Родитель: `org.springframework.boot:spring-boot-starter-parent:3.4.3`
- Зависимости:
  - `spring-boot-starter-web` (production)
  - `spring-boot-starter-test` (test scope)
- Плагин: `spring-boot-maven-plugin` для исполняемого JAR

### Свойства приложения (application.properties)

```properties
spring.application.name=test-qwen-cli
```

## Планы на будущее

- Проверить SDD-процесс на первой реальной задаче через полный Qwen pipeline
- Добавить легкий spec lint для обязательных секций и traceability
- Уточнить Qwen commands и skills по итогам пилота
- Расширить покрытие тестами по мере добавления функций
