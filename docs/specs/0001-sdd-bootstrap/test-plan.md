# План тестирования: SDD Bootstrap

## Стратегия

Тестирование bootstrap-фазы включает три уровня:

1. **Структурная проверка** — все директории и файлы существуют
2. **Содержательная проверка** — обязательные секции заполнены
3. **Регрессионная проверка** — существующие тесты приложения не сломаны

## Структурные проверки

| Требование | Проверка | Ожидаемый результат |
|------------|----------|---------------------|
| REQ-1 | Директории существуют | Все 9 директорий созданы |
| REQ-2 | Governance файлы | 4 файла в `docs/sdd/` |
| REQ-3 | Project rules | AGENTS.md, CONVENTIONS.md, QWEN.md |
| REQ-4 | Commands | 6 файлов в `.qwen/commands/sdd/` |
| REQ-5 | Skills | 5 SKILL.md в `.qwen/skills/` |
| REQ-6 | Agents | 4 файла в `.qwen/agents/` |
| REQ-7 | Templates | 12 файлов в `docs/specs/_template/` |
| REQ-8 | Spec lint | `scripts/sdd-lint.sh` существует и executable |

## Проверка содержимого (Spec Lint)

Каждый spec проверяется на:

- [ ] Секция "Требования" или "Requirements" присутствует
- [ ] Есть как минимум один REQ-* ID
- [ ] Есть как минимум один AC-* ID
- [ ] Нет `[NEEDS CLARIFICATION]` в critical path
- [ ] Заголовок spec.md не пустой

Каждый governance doc проверяется на:

- [ ] Не пустой (> 100 символов)
- [ ] Содержимое конкретное (не только placeholder-ы)

## Регрессионные тесты

| Команда | Ожидание |
|---------|----------|
| `mvn compile` | passed |
| `mvn test` | passed, без регрессий |

## Команды проверки

```bash
# Структурная проверка
ls docs/sdd/
ls docs/specs/_template/
ls .qwen/commands/sdd/
ls .qwen/skills/
ls .qwen/agents/

# Spec lint
bash scripts/sdd-lint.sh

# Регрессия
mvn clean test
```
