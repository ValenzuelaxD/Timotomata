@echo off
setlocal enabledelayedexpansion

set PROYECTO=Compilador Timotomata — CLI
set SRC_DIR=%~dp0src
set OUT_DIR=%~dp0out

echo ===== %PROYECTO% =====
echo.

REM ═══════════════════════════════════════════════════════════════
REM  1. DETECTAR JDK
REM ═══════════════════════════════════════════════════════════════

set JAVAC_CMD=
set JAVA_CMD=

REM Opcion A: JAVA_HOME
if defined JAVA_HOME (
    if exist "!JAVA_HOME!\bin\javac.exe" (
        set JAVAC_CMD="!JAVA_HOME!\bin\javac"
        set JAVA_CMD="!JAVA_HOME!\bin\java"
        goto :jdk_found
    )
)

REM Opcion B: Buscar en PATH
for /f "tokens=*" %%i in ('where javac 2^>nul') do (
    set JAVAC_CMD="%%i"
    for %%f in ("%%i") do set JAVA_DIR=%%~dpf
    set JAVA_CMD="!JAVA_DIR!java"
    goto :jdk_found
)

REM Opcion C: Buscar en Program Files
for /d %%i in ("C:\Program Files\Java\jdk*") do (
    if exist "%%i\bin\javac.exe" (
        set JAVAC_CMD="%%i\bin\javac"
        set JAVA_CMD="%%i\bin\java"
        goto :jdk_found
    )
)

echo [ERROR] No se encontró JDK en tu sistema.
echo.
echo Para instalar JDK:
echo   1. Ve a https://adoptium.net/
echo   2. Descarga el instalador para Windows (MSI)
echo   3. Ejecútalo y marca "Add to PATH"
echo.
pause
exit /b 1

:jdk_found
echo [1/3] JDK detectado: %JAVAC_CMD%
"%JAVA_CMD%" -version 2>&1 | findstr "version"
echo.

REM ═══════════════════════════════════════════════════════════════
REM  2. COMPILAR
REM ═══════════════════════════════════════════════════════════════

echo [2/3] Compilando...

%JAVAC_CMD% -d "%OUT_DIR%" ^
    "%SRC_DIR%\timotomata\Main.java" ^
    "%SRC_DIR%\timotomata\lexer\*.java" ^
    "%SRC_DIR%\timotomata\parser\*.java" ^
    "%SRC_DIR%\timotomata\parser\ast\*.java" ^
    2>&1

if errorlevel 1 (
    echo.
    echo [ERROR] Compilacion fallida.
    pause
    exit /b 1
)

echo [OK] Compilacion exitosa.
echo.

REM ═══════════════════════════════════════════════════════════════
REM  3. EJECUTAR
REM ═══════════════════════════════════════════════════════════════

echo [3/3] Ejecutando...
echo.
echo Escribe tu codigo Timotomata y finaliza con FIN.
echo Para salir sin escribir, presiona Ctrl+C.
echo.

%JAVA_CMD% -cp "%OUT_DIR%" timotomata.Main

if errorlevel 1 (
    echo.
    pause
    exit /b 1
)

echo.
pause
