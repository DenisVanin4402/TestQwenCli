# Соглашения проекта

Документ описывает стиль, архитектурные соглашения, структуру и локальные команды проекта.

## Стек

| Компонент | Версия |
|-----------|--------|
| Java | 21 |
| Spring Boot | 3.4.3 |
| Сборка | Maven 3.9+ |
| Тестирование | JUnit 5, MockMvc |
| Пакет | `com.example.testqwencli` |

## Локальные команды

```bash
# Сборка и тестирование
mvn clean compile                    # Очистить и скомпилировать
mvn test                             # Запустить все тесты
mvn test -Dtest=ClassName            # Запустить один тестовый класс
mvn package                          # Собрать JAR
mvn spring-boot:run                  # Запустить приложение

# SDD-проверки
bash scripts/sdd-lint.sh             # Проверить SDD-артефакты

# Git
git log -n 5                         # Последние 5 коммитов
git diff HEAD                        # Все изменения в рабочей области
git diff --staged                    # Только staged изменения
```

## Соглашения по коду

### Именование
- Классы: PascalCase
- Методы/переменные: camelCase
- Константы: UPPER_SNAKE_CASE
- Пакеты: lowercase, без подчёркиваний

### Структура
- Весь код в базовом пакете `com.example.testqwencli`
- Package-private видимость по умолчанию
- Явный `public` только на точках расширения (контроллеры, точки входа)

### Зависимости
- Spring Boot starters вместо отдельных библиотек
- Новые зависимости требуют обоснование и ADR
- Минимизировать production-зависимости

## Структура репозитория

```
TestQwenCli/
├── AGENTS.md                         # Инструкции для AI-агентов
├── CONVENTIONS.md                    # Данный файл
├── QWEN.md                           # Qwen CLI project memory
├── pom.xml
├── src/
│   ├── main/java/com/example/testqwencli/
│   └── test/java/com/example/testqwencli/
├── docs/
│   ├── adr/                          # Architectural Decision Records
│   ├── specs/                        # Feature specifications
│   │   ├── _template/                # Шаблон для новых spec
│   │   └── 0001-sdd-bootstrap/      # Текущая spec
│   └── sdd/                          # SDD governance
│       ├── constitution.md
│       ├── context-management.md
│       ├── gates.md
│       └── workflow.md
├── scripts/
│   └── sdd-lint.sh                   # Spec lint проверка
├── .qwen/
│   ├── commands/
│   │   └── sdd/                      # SDD команды Qwen CLI
│   └── skills/                       # SDD навыки Qwen CLI
└── target/                           # Артефакты сборки (git-ignore)
```

## SDD-соглашения

- Спецификации — источник истины
- Каждая задача ссылается на REQ-ID в spec
- Агенты получают context packet, а не весь репозиторий
- NFR включаются в спецификацию до реализации
- Human gate на спецификациях, ADR и приёмке

## Коммит-сообщения

- На русском языке
- Ссылка на ID требования при реализации SDD-фич
- Пример: `feat: реализовать REQ-1 — эндпоинт управления задачами`

## Игнорируемые файлы

- `target/`, `.idea/`, `*.class`, `*.log`
- Временные файлы IDE
