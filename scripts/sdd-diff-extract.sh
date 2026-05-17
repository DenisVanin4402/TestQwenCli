#!/usr/bin/env bash
#
# SDD Diff Extractor — извлечение и классификация git diff
# Использование: bash scripts/sdd-diff-extract.sh <branch-or-commit-1> <branch-or-commit-2> [target-spec-dir]
#
# Аргументы:
#   branch-or-commit-1  — базовая ветка или коммит (старая версия)
#   branch-or-commit-2  — целевая ветка или коммит (новая версия)
#   target-spec-dir     — опционально: директория спеки для копирования результатов
#
# Выход:
#   diff-summary.md  — структурированное резюме изменений
#   impact-map.md    — карта прямого и косвенного влияния
#

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BOLD='\033[1m'
NC='\033[0m'

log_info()  { echo -e "${BOLD}[INFO]${NC}   $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ========================================================================
# Проверка аргументов
# ========================================================================
if [ -z "$1" ] || [ -z "$2" ]; then
    echo "SDD Diff Extractor — извлечение diff между двумя версиями"
    echo ""
    echo "Использование:"
    echo "  bash scripts/sdd-diff-extract.sh <ref1> <ref2> [target-spec-dir]"
    echo ""
    echo "Примеры:"
    echo "  bash scripts/sdd-diff-extract.sh main feature-branch"
    echo "  bash scripts/sdd-diff-extract.sh abc123def feature-xyz"
    echo "  bash scripts/sdd-diff-extract.sh main feature-branch docs/specs/0002-my-spec/"
    echo ""
    exit 1
fi

REF1="$1"
REF2="$2"
TARGET_DIR="${3:-}"

# Проверка наличия .git
if [ ! -d ".git" ]; then
    log_error "Директория .git не найдена. Скрипт должен выполняться в корневой директории git-репозитория."
    exit 1
fi

PROJECT_ROOT="$(pwd)"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

log_info "SDD Diff Extractor — запуск"
log_info "Сравнение: $REF1...$REF2"
echo ""

# ========================================================================
# Проверка существования refs
# ========================================================================
if ! git rev-parse --verify "$REF1" >/dev/null 2>&1; then
    log_error "Ref не найден: $REF1"
    exit 1
fi

if ! git rev-parse --verify "$REF2" >/dev/null 2>&1; then
    log_error "Ref не найден: $REF2"
    exit 1
fi

# ========================================================================
# Сбор diff stat (общая статистика)
# ========================================================================
DIFF_STAT=$(git diff --stat "$REF1"..."$REF2" 2>/dev/null || git diff --stat "$REF1" "$REF2" 2>/dev/null || true)

if [ -z "$DIFF_STAT" ]; then
    log_warn "Различий между $REF1 и $REF2 не найдено (empty diff)."
    log_info "Создаю пустые diff-summary.md и impact-map.md"

    cat > diff-summary.md << EMPTYEOF
# Diff Summary: $REF1...$REF2

**Дата:** $TIMESTAMP
**Сравнение:** \`$REF1\`...\`$REF2\`

## Статус
Различий не обнаружено — пустой diff.

## Файлы
Нет изменённых файлов.

## Классификация
Нет данных для классификации.
EMPTYEOF

    cat > impact-map.md << IMPACTEMPTY
# Impact Map: $REF1...$REF2

**Дата:** $TIMESTAMP

## Статус
Пустой diff — карта влияния не применима.
IMPACTEMPTY
    log_ok "Пустые файлы созданы"
    exit 0
fi

# ========================================================================
# Сбор списка файлов с типами изменений (A/M/D)
# ========================================================================
# name-status даёт: A (added), M (modified), D (deleted)
NAME_STATUS_OUTPUT=$(git diff --name-status "$REF1"..."$REF2" 2>/dev/null || git diff --name-status "$REF1" "$REF2" 2>/dev/null)

# Подсчёт файлов
TOTAL_FILES=$(echo "$NAME_STATUS_OUTPUT" | grep -c '[ADM]' || echo "0")
TOTAL_ADDED=$(echo "$NAME_STATUS_OUTPUT" | grep -c '^A' || echo "0")
TOTAL_MODIFIED=$(echo "$NAME_STATUS_OUTPUT" | grep -c '^M' || echo "0")
TOTAL_DELETED=$(echo "$NAME_STATUS_OUTPUT" | grep -c '^D' || echo "0")

log_info "Найдено изменённых файлов: $TOTAL_FILES (добавлено: $TOTAL_ADDED, изменено: $TOTAL_MODIFIED, удалено: $TOTAL_DELETED)"

# ========================================================================
# Классификация файлов по типу
# ========================================================================
# Контракты (API/DBML)
CONTRACT_FILES=""
CONTRACT_COUNT=0

# Требования (.md файлы)
REQUIREMENT_FILES=""
REQUIREMENT_COUNT=0

# Код (src/)
CODE_FILES=""
CODE_COUNT=0

# Конфигурации
CONFIG_FILES=""
CONFIG_COUNT=0

# Прочие
OTHER_FILES=""
OTHER_COUNT=0

# Подсчёт строк добавлено/удалено по каждому файлу
ADDED_LINES=0
DELETED_LINES=0

while IFS=$'\t' read -r status filepath; do
    # Получаем количество строк добавлено/удалено
    FILE_DIFF=$(git diff --numstat "$REF1"..."$REF2" -- "$filepath" 2>/dev/null || git diff --numstat "$REF1" "$REF2" -- "$filepath" 2>/dev/null || echo "0 0")
    FILE_ADDED=$(echo "$FILE_DIFF" | awk '{print $1}')
    FILE_DELETED=$(echo "$FILE_DIFF" | awk '{print $2}')

    # Обработка бинарных файлов (вывод: - - filename)
    if [ "$FILE_ADDED" = "-" ]; then
        FILE_ADDED=0
        FILE_DELETED=0
    fi

    ADDED_LINES=$((ADDED_LINES + FILE_ADDED))
    DELETED_LINES=$((DELETED_LINES + FILE_DELETED))

    # Определяем тип файла
    STATUS_LABEL=""
    case "$status" in
        A) STATUS_LABEL="Added" ;;
        M) STATUS_LABEL="Modified" ;;
        D) STATUS_LABEL="Deleted" ;;
        *) STATUS_LABEL="Unknown" ;;
    esac

    FILE_ENTRY="- \`$filepath\` [$STATUS_LABEL] +${FILE_ADDED}/-${FILE_DELETED} строк"

    # Классифицируем по паттерну
    case "$filepath" in
        *openapi.yaml|*openapi.yml|*asyncapi.yaml|*asyncapi.yml|*.dbml)
            CONTRACT_FILES="${CONTRACT_FILES}${FILE_ENTRY}\n"
            CONTRACT_COUNT=$((CONTRACT_COUNT + 1))
            ;;
        docs/*.md|*.md|*.MD)
            REQUIREMENT_FILES="${REQUIREMENT_FILES}${FILE_ENTRY}\n"
            REQUIREMENT_COUNT=$((REQUIREMENT_COUNT + 1))
            ;;
        src/*)
            CODE_FILES="${CODE_FILES}${FILE_ENTRY}\n"
            CODE_COUNT=$((CODE_COUNT + 1))
            ;;
        *.properties|*.yml|*.yaml|pom.xml|*.xml|*.json|*.toml)
            # Исключаем уже обработанные конфигурационные YAML
            if [[ "$filepath" == *"openapi"* ]] || [[ "$filepath" == *"asyncapi"* ]]; then
                CONTRACT_FILES="${CONTRACT_FILES}${FILE_ENTRY}\n"
                CONTRACT_COUNT=$((CONTRACT_COUNT + 1))
            else
                CONFIG_FILES="${CONFIG_FILES}${FILE_ENTRY}\n"
                CONFIG_COUNT=$((CONFIG_COUNT + 1))
            fi
            ;;
        *)
            OTHER_FILES="${OTHER_FILES}${FILE_ENTRY}\n"
            OTHER_COUNT=$((OTHER_COUNT + 1))
            ;;
    esac
done <<< "$NAME_STATUS_OUTPUT"

log_info "Классификация: контракты=$CONTRACT_COUNT, требования=$REQUIREMENT_COUNT, код=$CODE_COUNT, конфиги=$CONFIG_COUNT, прочее=$OTHER_COUNT"

# ========================================================================
# Генерация diff-summary.md
# ========================================================================
{
    echo "# Diff Summary: \`$REF1\`...\`$REF2\`"
    echo ""
    echo "**Дата:** $TIMESTAMP"
    echo "**Сравнение:** \`$REF1\`...\`$REF2\`"
    echo "**Извлечено:** sdd-diff-extract.sh"
    echo ""
    echo "## Статистика"
    echo ""
    echo "| Метрика | Значение |"
    echo "|---------|----------|"
    echo "| Всего файлов | \`$TOTAL_FILES\` |"
    echo "| Добавлено | \`$TOTAL_ADDED\` |"
    echo "| Изменено | \`$TOTAL_MODIFIED\` |"
    echo "| Удалено | \`$TOTAL_DELETED\` |"
    echo "| Строк добавлено | \`$ADDED_LINES\` |"
    echo "| Строк удалено | \`$DELETED_LINES\` |"
    echo ""
    echo "## Классификация изменений"
    echo ""
    echo "### Контракты (API/DBML) — $CONTRACT_COUNT файлов"
    echo ""
    if [ -n "$CONTRACT_FILES" ]; then
        echo -e "$CONTRACT_FILES"
    else
        echo "Нет изменений в контрактах."
        echo ""
    fi

    echo "### Требования (.md файлы) — $REQUIREMENT_COUNT файлов"
    echo ""
    if [ -n "$REQUIREMENT_FILES" ]; then
        echo -e "$REQUIREMENT_FILES"
    else
        echo "Нет изменений в файлах требований."
        echo ""
    fi

    echo "### Код (src/) — $CODE_COUNT файлов"
    echo ""
    if [ -n "$CODE_FILES" ]; then
        echo -e "$CODE_FILES"
    else
        echo "Нет изменений в исходном коде."
        echo ""
    fi

    echo "### Конфигурации — $CONFIG_COUNT файлов"
    echo ""
    if [ -n "$CONFIG_FILES" ]; then
        echo -e "$CONFIG_FILES"
    else
        echo "Нет изменений в конфигурациях."
        echo ""
    fi

    if [ "$OTHER_COUNT" -gt 0 ]; then
        echo "### Прочие — $OTHER_COUNT файлов"
        echo ""
        echo -e "$OTHER_FILES"
    fi

    echo "## Полный список файлов"
    echo ""
    echo "| Тип | Файл | Добавлено | Удалено |"
    echo "|-----|------|-----------|---------|"
    while IFS=$'\t' read -r status filepath; do
        FILE_DIFF=$(git diff --numstat "$REF1"..."$REF2" -- "$filepath" 2>/dev/null || git diff --numstat "$REF1" "$REF2" -- "$filepath" 2>/dev/null || echo "0 0")
        FILE_ADDED=$(echo "$FILE_DIFF" | awk '{print $1}')
        FILE_DELETED=$(echo "$FILE_DIFF" | awk '{print $2}')
        if [ "$FILE_ADDED" = "-" ]; then FILE_ADDED="binary"; FILE_DELETED="binary"; fi
        echo "| ${status} | \`$filepath\` | $FILE_ADDED | $FILE_DELETED |"
    done <<< "$NAME_STATUS_OUTPUT"

} > "$PROJECT_ROOT/diff-summary.md"

log_ok "Создан diff-summary.md"

# ========================================================================
# Генерация impact-map.md
# ========================================================================

# Прямое влияние — файлы напрямую из diff
DIRECT_IMPACT=""
while IFS=$'\t' read -r status filepath; do
    DIRNAME=$(dirname "$filepath")
    BASENAME=$(basename "$filepath")
    case "$status" in
        A) DIRECT_IMPACT="${DIRECT_IMPACT}- **Added:** \`$filepath\` (новый файл в директории \`$DIRNAME\`)\n" ;;
        M) DIRECT_IMPACT="${DIRECT_IMPACT}- **Modified:** \`$filepath\` (затронута директория \`$DIRNAME\`)\n" ;;
        D) DIRECT_IMPACT="${DIRECT_IMPACT}- **Deleted:** \`$filepath\` (удалён из \`$DIRNAME\`)\n" ;;
    esac
done <<< "$NAME_STATUS_OUTPUT"

# Косвенное влияние — предполагаем связанные файлы
INDIRECT_IMPACT=""

# Если изменены openapi/asyncapi — проверяем AGENTS.md, existing specs
if echo "$NAME_STATUS_OUTPUT" | grep -qi 'openapi\|asyncapi'; then
    INDIRECT_IMPACT="${INDIRECT_IMPACT}- Изменены API-контракты → рекомендуется проверить:\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - \`AGENTS.md\` — инструкции для агентов могут требовать обновления\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - \`docs/specs/\` — существующие спеки с API-требованиями\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - \`docs/adr/\` — ADR с архитектурными решениями об API\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - Тесты API-эндпоинтов в \`src/test/\`\n"
fi

# Если изменены .md файлы требований
if echo "$NAME_STATUS_OUTPUT" | grep -qE '\.md$'; then
    INDIRECT_IMPACT="${INDIRECT_IMPACT}- Изменены .md файлы (требования/документация) → рекомендуется проверить:\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - \`docs/sdd/constitution.md\` — не нарушены ли принципы\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - Связанные спеки в \`docs/specs/\`\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - \`AGENTS.md\` / \`CONVENTIONS.md\` — не противоречат ли новым требованиям\n"
fi

# Если изменён код src/
if echo "$NAME_STATUS_OUTPUT" | grep -q 'src/'; then
    INDIRECT_IMPACT="${INDIRECT_IMPACT}- Изменён код в \`src/\` → рекомендуется проверить:\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - Тестовые файлы (\`src/test/\`) — соответствие новым тестам\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - \`contracts/\` — contract tests\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - Связанные спеки — соответствие реализованного поведения spec\n"
fi

# Если изменён pom.xml или конфигурации
if echo "$NAME_STATUS_OUTPUT" | grep -qE 'pom\.xml|\.properties$|\.yml$|\.yaml$'; then
    INDIRECT_IMPACT="${INDIRECT_IMPACT}- Изменены конфигурации → рекомендуется проверить:\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - \`application.properties\` — совместимость настроек\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - CI/CD pipeline файлы — не требуют ли обновления\n"
    INDIRECT_IMPACT="${INDIRECT_IMPACT}  - Зависимости (\`pom.xml\`) — совместимость версий\n"
fi

# Затронутые существующие spec (по совпадению имён файлов)
AFFECTED_SPECS=""
for spec_dir in docs/specs/0*/; do
    if [ -d "$spec_dir" ]; then
        spec_name=$(basename "$spec_dir")
        SLUG=$(echo "$spec_name" | sed 's/^[0-9]*-//')
        MATCHING=$(echo "$NAME_STATUS_OUTPUT" | awk '{print $2}' | grep -i "$SLUG" | head -5)
        if [ -n "$MATCHING" ]; then
            AFFECTED_SPECS="${AFFECTED_SPECS}- \`$spec_dir\` — затронуто изменениями: $(echo "$MATCHING" | tr '\n' ', ')\n"
        fi
    fi
done

# Затронутые существующие ADR (по совпадению имён/упоминаний)
AFFECTED_ADR=""
for adr_file in docs/adr/0*.md; do
    if [ -f "$adr_file" ]; then
        adr_base=$(basename "$adr_file" .md)
        # Проверяем, упоминается ли ADR в изменённых файлах
        while IFS=$'\t' read -r status filepath; do
            if [ "$status" != "D" ] && [ -f "$filepath" ]; then
                if grep -qi "$adr_base" "$filepath" 2>/dev/null; then
                    AFFECTED_ADR="${AFFECTED_ADR}- \`$adr_file\` — упоминается в \`$filepath\`\n"
                    break
                fi
            fi
        done <<< "$NAME_STATUS_OUTPUT"
    fi
done

{
    echo "# Impact Map: \`$REF1\`...\`$REF2\`"
    echo ""
    echo "**Дата:** $TIMESTAMP"
    echo "**Сравнение:** \`$REF1\`...\`$REF2\`"
    echo "**Анализирует:** sdd-diff-extract.sh"
    echo ""
    echo "## Прямое влияние (файлы из diff)"
    echo ""
    echo "Файлы, непосредственно затронутые в $REF1..$REF2:"
    echo ""
    echo -e "$DIRECT_IMPACT"
    echo ""
    echo "## Косвенное влияние (рекомендуемые проверки)"
    echo ""
    if [ -n "$INDIRECT_IMPACT" ]; then
        echo -e "$INDIRECT_IMPACT"
    else
        echo "Косвенное влияние не определено — нет паттернов для автоматического вывода."
        echo ""
    fi

    echo "## Затронутые существующие спеки"
    echo ""
    if [ -n "$AFFECTED_SPECS" ]; then
        echo -e "$AFFECTED_SPECS"
    else
        echo "Нет очевидных совпадений по именам с существующими спеками."
        echo ""
    fi

    echo "## Затронутые существующие ADR"
    echo ""
    if [ -n "$AFFECTED_ADR" ]; then
        echo -e "$AFFECTED_ADR"
    else
        echo "Нет ADR, упоминающихся в изменённых файлах."
        echo ""
    fi

    echo "## Рекомендации по контекстному пакету"
    echo ""
    echo "На основе анализа diff, рекомендуемый context packet для реализации:"
    echo ""
    if [ -n "$CONTRACT_FILES" ]; then
        echo "1. Контракты: проверить обновлённые openapi/asyncapi/dbml файлы"
    fi
    if [ -n "$REQUIREMENT_FILES" ]; then
        echo "2. Требования: прочитать изменённые .md файлы для понимания бизнес-логики"
    fi
    if [ -n "$CODE_FILES" ]; then
        echo "3. Код: просмотреть изменённые исходные файлы src/"
    fi
    if [ -n "$CONFIG_FILES" ]; then
        echo "4. Конфиги: проверить изменённые .properties/.yml файлы"
    fi
    echo ""
    echo "> Данный анализ является автоматическим. Требуется ручная проверка и дополнение."

} > "$PROJECT_ROOT/impact-map.md"

log_ok "Создан impact-map.md"

# ========================================================================
# Копирование в target spec directory (если указана)
# ========================================================================
if [ -n "$TARGET_DIR" ]; then
    if [ ! -d "$TARGET_DIR" ]; then
        log_info "Создаю директорию спеки: $TARGET_DIR"
        mkdir -p "$TARGET_DIR"
    fi
    cp "$PROJECT_ROOT/diff-summary.md" "$TARGET_DIR/diff-summary.md"
    cp "$PROJECT_ROOT/impact-map.md" "$TARGET_DIR/impact-map.md"
    log_ok "Файлы скопированы в $TARGET_DIR"
fi

echo ""
log_info "SDD Diff Extractor — завершение"
echo ""
echo "Созданные файлы:"
echo "  - diff-summary.md  (в $PROJECT_ROOT)"
echo "  - impact-map.md    (в $PROJECT_ROOT)"
if [ -n "$TARGET_DIR" ]; then
    echo "  - Также скопировано в $TARGET_DIR"
fi
echo ""
echo "Следующий шаг: сгенерировать delta-spec.md из diff-summary.md и impact-map.md"
echo "  Команда: /sdd:from-diff $REF1 $REF2"
