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

# Локальные команды

```bash
# Сборка и тестирование
mvn clean compile                   # Очистить и скомпилировать
mvn test                            # Запустить все тесты
mvn test -Dtest=ClassName           # Запустить один тестовый класс
mvn package                         # Собрать JAR
mvn spring-boot:run                 # Запустить приложение

# SDD-проверки (spec lint)
bash scripts/sdd-lint.sh            # Spec lint (Linux/macOS)
scripts\sdd-lint.bat               # Spec lint (Windows)
```

### IntelliJ IDEA

1. Открыть корневую директорию репозитория.
2. При появлении запроса — импортировать/перезагрузить `pom.xml` как Maven-проект.
3. Запустить `TestQwenCliApplication.main()` или использовать Maven-запуск.

## Структура проекта

```
TestQwenCli/
├── AGENTS.md                         # Инструкции для AI coding-агентов (cross-tool)
├── CONVENTIONS.md                    # Соглашения, локальные команды, структура
├── QWEN.md                           # Данный файл — Qwen CLI project memory
├── pom.xml                           # Maven-конфигурация
├── src/
│   ├── main/java/com/example/testqwencli/
│   │   ├── TestQwenCliApplication.java
│   │   └── HomeController.java
│   ├── main/resources/
│   │   └── application.properties
│   └── test/java/com/example/testqwencli/
│       └── TestQwenCliApplicationTests.java
├── docs/
│   ├── adr/                          # Architectural Decision Records
│   │   ├── 0001-record-architecture-decisions.md
│   │   └── _template.md
│   ├── specs/                        # Feature specifications (SDD artefacts)
│   │   ├── _template/                # Шаблон для новых spec
│   │   │   ├── spec.md
│   │   │   ├── requirements.md
│   │   │   ├── plan.md
│   │   │   ├── research.md
│   │   │   ├── data-model.md
│   │   │   ├── test-plan.md
│   │   │   ├── tasks.md
│   │   │   ├── quickstart.md
│   │   │   ├── task-state.md
│   │   │   ├── work-log.md
│   │   │   ├── handoff.md
│   │   │   └── contracts/README.md
│   │   └── 0001-sdd-bootstrap/      # Spec для SDD bootstrap (dogfooding)
│   │       ├── spec.md
│   │       ├── plan.md
│   │       ├── tasks.md
│   │       ├── test-plan.md
│   │       ├── task-state.md
│   │       ├── work-log.md
│   │       └── handoff.md
│   └── sdd/                          # SDD governance docs
│       ├── constitution.md           # Непереговорные принципы
│       ├── workflow.md               # SDD процесс и фазы
│       ├── gates.md                  # Definition of Ready/Done
│       └── context-management.md     # Политика контекста
├── scripts/
│   ├── sdd-lint.sh                   # Spec lint (Linux/Mac)
│   └── sdd-lint.bat                  # Spec lint (Windows)
├── .qwen/
│   ├── commands/
│   │   └── sdd/                      # SDD команды: specify, clarify, plan, tasks, implement, review
│   ├── skills/                       # SDD навыки: spec-review, plan, task-slice, test-gap, review
│   └── agents/                       # Роли агентов: planner, implementer, reviewer, security-reviewer
├── target/                           # Артефакты сборки (git-ignore)
└── .idea/                            # Файлы IDE (git-ignore)
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

- Внедрить SDD-процесс согласно `docs/sdd-plan.md`
- Добавить Qwen CLI команды и навыки для фаз SDD
- Создать `AGENTS.md` с переносимыми инструкциями для агентов
- Добавить `docs/specs/_template/` с шаблонами артефактов
- Ввести процесс ADR для архитектурных решений
- Расширить покрытие тестами по мере добавления функций
