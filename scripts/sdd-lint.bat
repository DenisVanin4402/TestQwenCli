@echo off
REM SDD Spec Lint - проверка SDD-artefactov (Windows)
REM Использование: scripts\sdd-lint.bat

setlocal enabledelayedexpansion
set ERRORS=0
set WARNINGS=0

echo [INFO] SDD Spec Lint - zapusk proverok...
echo.

set "ROOT=%~dp0.."

REM =============================================
REM 1. Diredorii
REM =============================================
echo [INFO] --- Proverka direktoriy ---

for %%D in ("docs\sdd" "docs\specs" "docs\adr" "docs\specs\_template" "docs\specs\_template\contracts" ".qwen\commands\sdd" ".qwen\skills" ".qwen\agents") do (
    if exist "%ROOT%\%%~D\" (
        echo [OK] Direktoriya %%~D ok
    ) else (
        echo [ERROR] Direktoriya %%~D otsutstvuet
        set /a ERRORS+=1
    )
)
echo.

REM =============================================
REM 2. Governance docs
REM =============================================
echo [INFO] --- Proverka governance dokumentov ---

for %%F in ("docs\sdd\constitution.md" "docs\sdd\workflow.md" "docs\sdd\gates.md" "docs\sdd\context-management.md") do (
    if exist "%ROOT%\%%~F" (
        echo [OK] %%~nxF ok
    ) else (
        echo [ERROR] %%~F otsutstvuet
        set /a ERRORS+=1
    )
)
echo.

REM =============================================
REM 3. Project rules
REM =============================================
echo [INFO] --- Proverka project rules ---

for %%F in ("AGENTS.md" "CONVENTIONS.md") do (
    if exist "%ROOT%\%%F" (
        echo [OK] %%F ok
    ) else (
        echo [ERROR] %%F otsutstvuet
        set /a ERRORS+=1
    )
)
echo.

REM =============================================
REM 4. Qwen CLI commands
REM =============================================
echo [INFO] --- Proverka Qwen CLI kommand ---

for %%C in ("specify.md" "clarify.md" "plan.md" "tasks.md" "implement.md" "review.md") do (
    if exist "%ROOT%\.qwen\commands\sdd\%%~C" (
        echo [OK] .qwen/commands/sdd/%%~C ok
    ) else (
        echo [ERROR] .qwen/commands/sdd/%%~C otsutstvuet
        set /a ERRORS+=1
    )
)
echo.

REM =============================================
REM 5. Qwen skills
REM =============================================
echo [INFO] --- Proverka Qwen skills ---

for %%S in ("sdd-spec-review" "sdd-plan" "sdd-task-slice" "sdd-test-gap" "sdd-review") do (
    if exist "%ROOT%\.qwen\skills\%%~S\SKILL.md" (
        echo [OK] .qwen/skills/%%~S/SKILL.md ok
    ) else (
        echo [ERROR] .qwen/skills/%%~S/SKILL.md otsutstvuet
        set /a ERRORS+=1
    )
)
echo.

REM =============================================
REM 6. Agent roles
REM =============================================
echo [INFO] --- Proverka roley agentov ---

for %%A in ("planner.md" "implementer.md" "reviewer.md" "security-reviewer.md") do (
    if exist "%ROOT%\.qwen\agents\%%~A" (
        echo [OK] .qwen/agents/%%~A ok
    ) else (
        echo [ERROR] .qwen/agents/%%~A otsutstvuet
        set /a ERRORS+=1
    )
)
echo.

REM =============================================
REM 7. Template artefacts
REM =============================================
echo [INFO] --- Proverka shablonov artefactov ---

for %%T in ("spec.md" "requirements.md" "plan.md" "research.md" "data-model.md" "test-plan.md" "tasks.md" "quickstart.md" "task-state.md" "work-log.md" "handoff.md") do (
    if exist "%ROOT%\docs\specs\_template\%%~T" (
        echo [OK] _template/%%~T ok
    ) else (
        echo [ERROR] _template/%%~T otsutstvuet
        set /a ERRORS+=1
    )
)

if exist "%ROOT%\docs\specs\_template\contracts\README.md" (
    echo [OK] _template/contracts/README.md ok
) else (
    echo [ERROR] _template/contracts/README.md otsutstvuet
    set /a ERRORS+=1
)
echo.

REM =============================================
REM 8. Specs proverka
REM =============================================
echo [INFO] --- Proverka specifikatsiy ---

set SPEC_FOUND=0
for /d %%D in ("%ROOT%\docs\specs\0*") do (
    if exist "%%~D\" (
        set SPEC_FOUND=1
        set "SNAME=%%~nxD"
        echo.
        echo [INFO] Spec: !SNAME!

        if not exist "%%~D\spec.md" (
            echo [ERROR] !SNAME!/spec.md otsutstvuet
            set /a ERRORS+=1
        ) else (
            findstr /i /r "REQ-[0-9]" "%%~D\spec.md" >nul 2>&1
            if !errorlevel! equ 0 (
                echo [OK] !SNAME! REQ-IDs naydeny
            ) else (
                echo [ERROR] !SNAME! REQ-IDs ne naydeny
                set /a ERRORS+=1
            )

            findstr /i /r "AC-[0-9]" "%%~D\spec.md" >nul 2>&1
            if !errorlevel! equ 0 (
                echo [OK] !SNAME! AC-IDs naydeny
            ) else (
                echo [ERROR] !SNAME! AC-IDs ne naydeny
                set /a ERRORS+=1
            )

            findstr /i "NEEDS CLARIFICATION" "%%~D\spec.md" >nul 2>&1
            if !errorlevel! equ 0 (
                echo [WARN] !SNAME! nuzhny otvety na voprosy
                set /a WARNINGS+=1
            )
        )
    )
)

if !SPEC_FOUND! equ 0 (
    echo [WARN] Specifikatsii ne naydeny
    set /a WARNINGS+=1
)
echo.

REM =============================================
REM 9. ADR
REM =============================================
echo [INFO] --- Proverka ADR ---

set ADR_FOUND=0
for %%F in ("%ROOT%\docs\adr\0*.md") do (
    if exist "%%F" set ADR_FOUND=1
)
if !ADR_FOUND! equ 1 (
    echo [OK] ADR naydeny
) else (
    echo [WARN] ADR ne naydeny
    set /a WARNINGS+=1
)
echo.

REM =============================================
REM Rezultat
REM =============================================
echo ======================================
if !ERRORS! equ 0 (
    echo Result: OK, Errors: 0, Warnings: !WARNINGS!
    exit /b 0
) else (
    echo Result: FAIL, Errors: !ERRORS!, Warnings: !WARNINGS!
    exit /b 1
)
