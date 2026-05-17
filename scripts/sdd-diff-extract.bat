@echo off
rem SDD Diff Extractor — извлечение и классификация git diff (Windows cmd)
rem Использование: scripts\sdd-diff-extract.bat ^<branch-or-commit-1^> ^<branch-or-commit-2^> [target-spec-dir]
rem
rem Аргументы:
rem   branch-or-commit-1  — базовая ветка или коммит (старая версия)
rem   branch-or-commit-2  — целевая ветка или коммит (новая версия)
rem   target-spec-dir     — опционально: директория спеки для копирования результатов
rem
rem Выход:
rem   diff-summary.md  — структурированное резюме изменений
rem   impact-map.md    — карта прямого и косвенного влияния

setlocal enabledelayedexpansion

rem ========================================================================
rem Проверка аргументов
rem ========================================================================
if "%~1"=="" goto :usage
if "%~2"=="" goto :usage

set REF1=%~1
set REF2=%~2
set TARGET_DIR=%~3

rem Проверка наличия .git
if not exist ".git" (
    echo [ERROR] Директория .git не найдена. Запустите из корня git-репозитория.
    exit /b 1
)

set TIMESTAMP=%date% %time%
set ADDED_COUNT=0
set MODIFIED_COUNT=0
set DELETED_COUNT=0
set CONTRACT_COUNT=0
set REQUIREMENT_COUNT=0
set CODE_COUNT=0
set CONFIG_COUNT=0
set OTHER_COUNT=0
set TOTAL_LINES_ADD=0
set TOTAL_LINES_DEL=0

echo [INFO] SDD Diff Extractor — запуск
echo [INFO] Сравнение: %REF1%...%REF2%
echo.

rem ========================================================================
rem Проверка существования refs
rem ========================================================================
git rev-parse --verify "%REF1%" >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Ref не найден: %REF1%
    exit /b 1
)

git rev-parse --verify "%REF2%" >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Ref не найден: %REF2%
    exit /b 1
)

rem ========================================================================
rem Сбор diff stat
rem ========================================================================
set DIFF_STAT=
for /f "delims=" %%i in ('git diff --stat "%REF1%...%REF2%" 2^>nul') do set DIFF_STAT=!DIFF_STAT!%%i
if "!DIFF_STAT!"=="" (
    for /f "delims=" %%i in ('git diff --stat "%REF1%" "%REF2%" 2^>nul') do set DIFF_STAT=!DIFF_STAT!%%i
)

if "!DIFF_STAT!"=="" (
    echo [WARN] Различий между %REF1% и %REF2% не найдено ^(empty diff^).
    echo [INFO] Создаю пустые diff-summary.md и impact-map.md

    (
        echo # Diff Summary: %REF1%...%REF2%
        echo.
        echo **Дата:** %TIMESTAMP%
        echo **Сравнение:** `%REF1%`...`%REF2%`
        echo.
        echo ## Статус
        echo.
        echo Различий не обнаружено — пустой diff.
    ) > diff-summary.md

    (
        echo # Impact Map: %REF1%...%REF2%
        echo.
        echo **Дата:** %TIMESTAMP%
        echo.
        echo ## Статус
        echo.
        echo Пустой diff — карта влияния не применима.
    ) > impact-map.md

    echo [OK] Пустые файлы созданы
    goto :copy_to_target
)

rem ========================================================================
rem Сбор списка файлов с типами и numstat
rem ========================================================================
rem Временные файлы для накопления данных
set TMP_DIR=%TEMP%\sdd-diff-%RANDOM%
mkdir "%TMP_DIR%" 2>nul

> "%TMP_DIR%\name_status.txt" git diff --name-status "%REF1%...%REF2%" 2>nul
if %errorlevel% neq 0 (
    > "%TMP_DIR%\name_status.txt" git diff --name-status "%REF1%" "%REF2%" 2>nul
)

> "%TMP_DIR%\numstat.txt" git diff --numstat "%REF1%...%REF2%" 2>nul
if %errorlevel% neq 0 (
    > "%TMP_DIR%\numstat.txt" git diff --numstat "%REF1%" "%REF2%" 2>nul
)

rem Считаем файлы
for /f "tokens=1*" %%a in ('find /c /v "" ^< "%TMP_DIR%\name_status.txt"') do set TOTAL_FILES=%%a

rem ========================================================================
rem Классификация
rem ========================================================================
set CONTRACT_LIST=
set REQUIREMENT_LIST=
set CODE_LIST=
set CONFIG_LIST=
set OTHER_LIST=
set DIRECT_IMPACT_LIST=

for /f "tokens=1,2" %%a in ('type "%TMP_DIR%\name_status.txt"') do (
    set STATUS=%%a
    set FILEPATH=%%b

    rem Соответствующая строка numstat
    set FILE_ADDED=0
    set FILE_DELETED=0
    for /f "tokens=1,2,3" %%x in ('findstr /c:"!FILEPATH!" "%TMP_DIR%\numstat.txt" 2^>nul') do (
        set FILE_ADDED=%%x
        set FILE_DELETED=%%y
    )
    if "!FILE_ADDED!"=="-" (
        set FILE_ADDED=0
        set FILE_DELETED=0
    )
    set /a TOTAL_LINES_ADD+=!FILE_ADDED!
    set /a TOTAL_LINES_DEL+=!FILE_DELETED!

    rem Определяем статус-лейбл
    if "!STATUS!"=="A" (
        set STATUS_LABEL=Added
        set /a ADDED_COUNT+=1
    ) else if "!STATUS!"=="M" (
        set STATUS_LABEL=Modified
        set /a MODIFIED_COUNT+=1
    ) else if "!STATUS!"=="D" (
        set STATUS_LABEL=Deleted
        set /a DELETED_COUNT+=1
    ) else (
        set STATUS_LABEL=Unknown
    )

    set FILE_ENTRY=- `!FILEPATH!` [!STATUS_LABEL!] +!FILE_ADDED!/-%FILE_DELETED% строк

    rem Классификация
    echo !FILEPATH! | findstr /i /r "openapi\.ya?ml$ asyncapi\.ya?ml$ \.dbml$" >nul 2>&1
    if !errorlevel! equ 0 (
        set CONTRACT_LIST=!CONTRACT_LIST!
!FILE_ENTRY!
        set /a CONTRACT_COUNT+=1
        goto :classify_done
    )

    echo !FILEPATH! | findstr /r "\.md$" >nul 2>&1
    if !errorlevel! equ 0 (
        set REQUIREMENT_LIST=!REQUIREMENT_LIST!
!FILE_ENTRY!
        set /a REQUIREMENT_COUNT+=1
        goto :classify_done
    )

    echo !FILEPATH! | findstr /r "^src\\" >nul 2>&1
    if !errorlevel! equ 0 (
        set CODE_LIST=!CODE_LIST!
!FILE_ENTRY!
        set /a CODE_COUNT+=1
        goto :classify_done
    )

    echo !FILEPATH! | findstr /r "\.properties$ \.yml$ \.yaml$ pom\.xml" >nul 2>&1
    if !errorlevel! equ 0 (
        set CONFIG_LIST=!CONFIG_LIST!
!FILE_ENTRY!
        set /a CONFIG_COUNT+=1
        goto :classify_done
    )

    set OTHER_LIST=!OTHER_LIST!
!FILE_ENTRY!
    set /a OTHER_COUNT+=1

    :classify_done

    rem Прямое влияние
    if "!STATUS!"=="A" (
        set DIRECT_IMPACT_LIST=!DIRECT_IMPACT_LIST!
- **Added:** `!FILEPATH!` ^(новый файл^)

    ) else if "!STATUS!"=="M" (
        set DIRECT_IMPACT_LIST=!DIRECT_IMPACT_LIST!
- **Modified:** `!FILEPATH!`

    ) else if "!STATUS!"=="D" (
        set DIRECT_IMPACT_LIST=!DIRECT_IMPACT_LIST!
- **Deleted:** `!FILEPATH!`

    )
)

echo [INFO] Классификация: контракты=!CONTRACT_COUNT!, требования=!REQUIREMENT_COUNT!, код=!CODE_COUNT!, конфиги=!CONFIG_COUNT!, прочее=!OTHER_COUNT!

rem ========================================================================
rem Определяем косвенное влияние
rem ========================================================================
set INDIRECT_IMPACT_LIST=

type "%TMP_DIR%\name_status.txt" | findstr /i "openapi asyncapi" >nul 2>&1
if !errorlevel! equ 0 (
    set INDIRECT_IMPACT_LIST=!INDIRECT_IMPACT_LIST!
- Изменены API-контракты → рекомендуется проверить:
  - `AGENTS.md` — инструкции для агентов
  - `docs/specs/` — существующие спеки с API-требованиями
  - `docs/adr/` — ADR с архитектурными решениями об API

)

type "%TMP_DIR%\name_status.txt" | findstr "\.md$" >nul 2>&1
if !errorlevel! equ 0 (
    set INDIRECT_IMPACT_LIST=!INDIRECT_IMPACT_LIST!
- Изменены .md файлы → рекомендуется проверить:
  - `docs/sdd/constitution.md` — принципы SDD
  - Связанные спеки в `docs/specs/`

)

type "%TMP_DIR%\name_status.txt" | findstr "^src\" >nul 2>&1
if !errorlevel! equ 0 (
    set INDIRECT_IMPACT_LIST=!INDIRECT_IMPACT_LIST!
- Изменён код в `src/` → рекомендуется проверить:
  - Тестовые файлы (`src/test/`)
  - Связанные спеки

)

rem ========================================================================
rem Генерация diff-summary.md
rem ========================================================================

(
    echo # Diff Summary: `%REF1%`...`%REF2%`
    echo.
    echo **Дата:** %TIMESTAMP%
    echo **Сравнение:** `%REF1%`...`%REF2%`
    echo **Извлечено:** sdd-diff-extract.bat
    echo.
    echo ## Статистика
    echo.
    echo ^| Метрика ^| Значение ^|
    echo ^|---------^|----------^|
    echo ^| Всего файлов ^| `%TOTAL_FILES%` ^|
    echo ^| Добавлено ^| `%ADDED_COUNT%` ^|
    echo ^| Изменено ^| `%MODIFIED_COUNT%` ^|
    echo ^| Удалено ^| `%DELETED_COUNT%` ^|
    echo ^| Строк добавлено ^| `%TOTAL_LINES_ADD%` ^|
    echo ^| Строк удалено ^| `%TOTAL_LINES_DEL%` ^|
    echo.
    echo ## Классификация изменений
    echo.

    echo ### Контракты (API/DBML^) — !CONTRACT_COUNT! файлов
    echo.
    if "!CONTRACT_LIST!"=="" (
        echo Нет изменений в контрактах.
    ) else (
        echo !CONTRACT_LIST!
    )
    echo.

    echo ### Требования ^(.md файлы^) — !REQUIREMENT_COUNT! файлов
    echo.
    if "!REQUIREMENT_LIST!"=="" (
        echo Нет изменений в файлах требований.
    ) else (
        echo !REQUIREMENT_LIST!
    )
    echo.

    echo ### Код ^(src/^) — !CODE_COUNT! файлов
    echo.
    if "!CODE_LIST!"=="" (
        echo Нет изменений в исходном коде.
    ) else (
        echo !CODE_LIST!
    )
    echo.

    echo ### Конфигурации — !CONFIG_COUNT! файлов
    echo.
    if "!CONFIG_LIST!"=="" (
        echo Нет изменений в конфигурациях.
    ) else (
        echo !CONFIG_LIST!
    )
    echo.

    if !OTHER_COUNT! gtr 0 (
        echo ### Прочие — !OTHER_COUNT! файлов
        echo.
        echo !OTHER_LIST!
        echo.
    )

    echo ## Полный список файлов
    echo.
    echo ^| Тип ^| Файл ^| Добавлено ^| Удалено ^|
    echo ^|-----^|------^|-----------^|---------^|
    for /f "tokens=1,2" %%a in ('type "%TMP_DIR%\name_status.txt"') do (
        set FSTATUS=%%a
        set FPATH=%%b
        set F_ADDED=0
        set F_DELETED=0
        for /f "tokens=1,2,3" %%x in ('findstr /c:"%%b" "%TMP_DIR%\numstat.txt" 2^>nul') do (
            set F_ADDED=%%x
            set F_DELETED=%%y
        )
        echo ^| !FSTATUS! ^| `!FPATH!` ^| !F_ADDED! ^| !F_DELETED! ^|
    )
) > diff-summary.md

echo [OK] Создан diff-summary.md

rem ========================================================================
rem Генерация impact-map.md
rem ========================================================================

(
    echo # Impact Map: `%REF1%`...`%REF2%`
    echo.
    echo **Дата:** %TIMESTAMP%
    echo **Сравнение:** `%REF1%`...`%REF2%`
    echo **Анализирует:** sdd-diff-extract.bat
    echo.
    echo ## Прямое влияние ^(файлы из diff^)
    echo.
    echo Файлы, непосредственно затронутые в %REF1%..%REF2%:
    echo.
    echo !DIRECT_IMPACT_LIST!
    echo.
    echo ## Косвенное влияние ^(рекомендуемые проверки^)
    echo.
    if "!INDIRECT_IMPACT_LIST!"=="" (
        echo Косвенное влияние не определено.
    ) else (
        echo !INDIRECT_IMPACT_LIST!
    )
    echo.
    echo ## Затронутые существующие спеки
    echo.
    echo Проверяется автоматически по совпадению имён...
    echo ^(Требуется ручная проверка docs/specs/^)
    echo.
    echo ## Затронутые существующие ADR
    echo.
    echo Проверяется автоматически по упоминаниям...
    echo ^(Требуется ручная проверка docs/adr/^)
    echo.
    echo ^> Данный анализ является автоматическим. Требуется ручная проверка и дополнение.
) > impact-map.md

echo [OK] Создан impact-map.md

rem Очистка временных файлов
rmdir /s /q "%TMP_DIR%" 2>nul

:copy_to_target
rem ========================================================================
rem Копирование в target spec directory
rem ========================================================================
if not "%TARGET_DIR%"=="" (
    if not exist "%TARGET_DIR%" (
        echo [INFO] Создаю директорию спеки: %TARGET_DIR%
        mkdir "%TARGET_DIR%" 2>nul
    )
    copy /Y diff-summary.md "%TARGET_DIR%\diff-summary.md" >nul
    copy /Y impact-map.md "%TARGET_DIR%\impact-map.md" >nul
    echo [OK] Файлы скопированы в %TARGET_DIR%
)

echo.
echo [INFO] SDD Diff Extractor — завершение
echo.
echo Созданные файлы:
echo   - diff-summary.md  ^(в %CD%^)
echo   - impact-map.md    ^(в %CD%^)
if not "%TARGET_DIR%"=="" (
    echo   - Также скопировано в %TARGET_DIR%
)
echo.
echo Следующий шаг: сгенерировать delta-spec.md из diff-summary.md и impact-map.md
echo   Команда: /sdd:from-diff %REF1% %REF2%

goto :eof

:usage
echo SDD Diff Extractor — извлечение diff между двумя версиями
echo.
echo Использование:
echo   scripts\sdd-diff-extract.bat ^<ref1^> ^<ref2^> [target-spec-dir]
echo.
echo Примеры:
echo   scripts\sdd-diff-extract.bat main feature-branch
echo   scripts\sdd-diff-extract.bat abc123def feature-xyz
echo   scripts\sdd-diff-extract.bat main feature-branch docs\specs\0002-my-spec\
echo.
exit /b 0
