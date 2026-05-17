#!/usr/bin/env bash
#
# SDD Spec Lint — проверка SDD-артефактов
# Использование: bash scripts/sdd-lint.sh
# Работает на Linux/Mac. Для Windows использовать scripts/sdd-lint.bat
#

set -e

ERRORS=0
WARNINGS=0

RED='\033[0;31m'
YELLOW='\033[0;33m'
GREEN='\033[0;32m'
NC='\033[0m'
BOLD='\033[1m'

log_error() { echo -e "${RED}[ERROR]${NC} $1"; ERRORS=$((ERRORS + 1)); }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; WARNINGS=$((WARNINGS + 1)); }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
log_info()  { echo -e "${BOLD}[INFO]${NC}   $1"; }

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

log_info "SDD Spec Lint — запуск проверок..."
echo ""

# ========================================================================
# 1. Проверка директорий
# ========================================================================
log_info "--- Проверка структуры директорий ---"

for dir in "docs/sdd" "docs/specs" "docs/adr" "docs/specs/_template" \
           "docs/specs/_template/contracts" ".qwen/commands/sdd" \
           ".qwen/skills" ".qwen/agents"; do
    if [ -d "$PROJECT_ROOT/$dir" ]; then
        log_ok "Директория $dir существует"
    else
        log_error "Директория $dir отсутствует"
    fi
done
echo ""

# ========================================================================
# 2. Проверка governance-документов
# ========================================================================
log_info "--- Проверка governance-документов ---"

for file in "constitution.md" "workflow.md" "gates.md" "context-management.md"; do
    filepath="$PROJECT_ROOT/docs/sdd/$file"
    if [ -f "$filepath" ]; then
        size=$(wc -c < "$filepath")
        if [ "$size" -gt 100 ]; then
            log_ok "docs/sdd/$file ($size байт)"
        else
            log_warn "docs/sdd/$file подозрительно мал ($size байт)"
        fi
    else
        log_error "docs/sdd/$file отсутствует"
    fi
done
echo ""

# ========================================================================
# 3. Проверка project rules
# ========================================================================
log_info "--- Проверка project rules ---"

for file in "AGENTS.md" "CONVENTIONS.md"; do
    filepath="$PROJECT_ROOT/$file"
    if [ -f "$filepath" ]; then
        size=$(wc -c < "$filepath")
        if [ "$size" -gt 100 ]; then
            log_ok "$file ($size байт)"
        else
            log_warn "$file подозрительно мал ($size байт)"
        fi
    else
        log_error "$file отсутствует"
    fi
done
echo ""

# ========================================================================
# 4. Проверка Qwen CLI команд
# ========================================================================
log_info "--- Проверка Qwen CLI команд SDD ---"

REQUIRED_COMMANDS="specify.md clarify.md plan.md tasks.md implement.md review.md"
for cmd in $REQUIRED_COMMANDS; do
    filepath="$PROJECT_ROOT/.qwen/commands/sdd/$cmd"
    if [ -f "$filepath" ]; then
        log_ok ".qwen/commands/sdd/$cmd существует"
    else
        log_error ".qwen/commands/sdd/$cmd отсутствует"
    fi
done
echo ""

# ========================================================================
# 5. Проверка Qwen skills
# ========================================================================
log_info "--- Проверка Qwen skills SDD ---"

REQUIRED_SKILLS="sdd-spec-review sdd-plan sdd-task-slice sdd-test-gap sdd-review"
for skill in $REQUIRED_SKILLS; do
    filepath="$PROJECT_ROOT/.qwen/skills/$skill/SKILL.md"
    if [ -f "$filepath" ]; then
        log_ok ".qwen/skills/$skill/SKILL.md существует"
    else
        log_error ".qwen/skills/$skill/SKILL.md отсутствует"
    fi
done
echo ""

# ========================================================================
# 6. Проверка ролей агентов
# ========================================================================
log_info "--- Проверка ролей агентов ---"

REQUIRED_AGENTS="planner.md implementer.md reviewer.md security-reviewer.md"
for agent in $REQUIRED_AGENTS; do
    filepath="$PROJECT_ROOT/.qwen/agents/$agent"
    if [ -f "$filepath" ]; then
        log_ok ".qwen/agents/$agent существует"
    else
        log_error ".qwen/agents/$agent отсутствует"
    fi
done
echo ""

# ========================================================================
# 7. Проверка шаблонов артефактов
# ========================================================================
log_info "--- Проверка шаблонов артефактов ---"

REQUIRED_TEMPLATES="spec.md requirements.md plan.md research.md data-model.md \
                    test-plan.md tasks.md quickstart.md task-state.md work-log.md \
                    handoff.md contracts/README.md"
for tmpl in $REQUIRED_TEMPLATES; do
    filepath="$PROJECT_ROOT/docs/specs/_template/$tmpl"
    if [ -f "$filepath" ]; then
        log_ok "docs/specs/_template/$tmpl существует"
    else
        log_error "docs/specs/_template/$tmpl отсутствует"
    fi
done
echo ""

# ========================================================================
# 8. Проверка спецификаций (spec.md)
# ========================================================================
log_info "--- Проверка спецификаций ---"

SPEC_FOUND=0
for spec_dir in "$PROJECT_ROOT"/docs/specs/0*/; do
    if [ -d "$spec_dir" ]; then
        SPEC_FOUND=1
        spec_file="$spec_dir/spec.md"
        spec_name=$(basename "$spec_dir")
        echo ""
        log_info "Spec: $spec_name"

        if [ ! -f "$spec_file" ]; then
            log_error "$spec_name/spec.md отсутствует"
            continue
        fi

        content=$(cat "$spec_file")

        # Требование: наличие секции требований/Requirements
        if echo "$content" | grep -qi "^\s*## требования\|^\s*## requirements"; then
            log_ok "$spec_name: секция требований найдена"
        else
            log_error "$spec_name: секция требований (## Requirements) не найдена"
        fi

        # Требование: наличие REQ-* ID
        if echo "$content" | grep -q "REQ-[0-9]"; then
            log_ok "$spec_name: REQ-* IDs найдены"
        else
            log_error "$spec_name: нет REQ-* IDs"
        fi

        # Требование: наличие AC-* ID
        if echo "$content" | grep -q "AC-[0-9]"; then
            log_ok "$spec_name: AC-* IDs найдены"
        else
            log_error "$spec_name: нет AC-* IDs"
        fi

        # Предупреждение: NEEDS CLARIFICATION
        if echo "$content" | grep -q "NEEDS CLARIFICATION"; then
            log_warn "$spec_name: содержит [NEEDS CLARIFICATION] — требует ответа"
        fi

        # Требование: matrix трассировки
        if echo "$content" | grep -qi "матрица трассировки\|traceability"; then
            log_ok "$spec_name: матрица трассировки найдена"
        else
            log_warn "$spec_name: матрица трассировки не найдена"
        fi

        # Требование: NFR секции
        if echo "$content" | grep -qi "nfr\|безопасност\|производител\|наблюдаем"; then
            log_ok "$spec_name: NFR секция найдена"
        else
            log_warn "$spec_name: NFR секция не найдена"
        fi

        # Проверка наличия сопутствующих файлов
        for f in plan.md tasks.md test-plan.md; do
            if [ -f "$spec_dir/$f" ]; then
                log_ok "$spec_name/$f существует"
            else
                log_warn "$spec_name/$f отсутствует (необязательно на ранней фазе)"
            fi
        done
    fi
done

if [ "$SPEC_FOUND" -eq 0 ]; then
    log_warn "Спецификации (000N-slug/) не найдены"
fi
echo ""

# ========================================================================
# 9. Проверка ADR
# ========================================================================
log_info "--- Проверка ADR ---"

ADR_COUNT=$(find "$PROJECT_ROOT/docs/adr" -name "0*.md" 2>/dev/null | wc -l)
if [ "$ADR_COUNT" -gt 0 ]; then
    log_ok "Найдено ADR файлов: $ADR_COUNT"
else
    log_warn "ADR файлы не найдены"
fi
echo ""

# ========================================================================
# Итог
# ========================================================================
echo "======================================"
if [ "$ERRORS" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    echo -e "${GREEN}Все проверки пройдены!${NC}"
    exit 0
elif [ "$ERRORS" -eq 0 ]; then
    echo -e "${YELLOW}Ошибок: 0, Предупреждений: $WARNINGS${NC}"
    exit 0
else
    echo -e "${RED}Ошибок: $ERRORS, Предупреждений: $WARNINGS${NC}"
    exit 1
fi
