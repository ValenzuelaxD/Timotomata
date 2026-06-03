@echo off
setlocal enabledelayedexpansion

set PROYECTO=Compilador Timotomata — GUI
set JAVAFX_VER=23.0.2
set JAVAFX_DIR=%~dp0javafx-sdk-%JAVAFX_VER%
set SRC_DIR=%~dp0src
set OUT_DIR=%~dp0out
set LIB_DIR=%~dp0lib

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
for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\Java\jdk*" 2^>nul') do (
    if exist "C:\Program Files\Java\%%i\bin\javac.exe" (
        set JAVAC_CMD="C:\Program Files\Java\%%i\bin\javac"
        set JAVA_CMD="C:\Program Files\Java\%%i\bin\java"
        goto :jdk_found
    )
)

echo [ERROR] No se encontro JDK 17+ instalado.
echo.
echo Para instalar JDK:
echo   1. Ve a https://adoptium.net/
echo   2. Descarga el instalador para Windows
echo   3. Ejecutalo y marca "Add to PATH"
echo.
pause
exit /b 1

:jdk_found
echo [1/4] JDK detectado: %JAVAC_CMD%
for /f "tokens=1" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr "version"') do echo       %%v
echo.

REM ═══════════════════════════════════════════════════════════════
REM  2. VERIFICAR JAVAFX (sin descarga automatica para evitar bloqueos)
REM ═══════════════════════════════════════════════════════════════

if exist "%JAVAFX_DIR%\lib\javafx.controls.jar" (
    echo [2/4] JavaFX SDK %JAVAFX_VER% encontrado.
) else (
    echo [2/4] JavaFX SDK %JAVAFX_VER% NO encontrado.
    echo.
    echo Descargalo manualmente desde:
    echo   https://gluonhq.com/products/javafx/
    echo.
    echo Guarda el ZIP en: %~dp0
    echo con nombre: javafx-sdk-%JAVAFX_VER%.zip
    echo y extraelo en: %~dp0javafx-sdk-%JAVAFX_VER%\
    echo.
    echo O usa el instalador automatico de Eclipse/IntelliJ.
    pause
    exit /b 1
)
echo.

REM ═══════════════════════════════════════════════════════════════
REM  3. COMPILAR
REM ═══════════════════════════════════════════════════════════════

echo [3/4] Compilando...

if not exist "%OUT_DIR%\timotomata\ui\" mkdir "%OUT_DIR%\timotomata\ui"
copy /Y "%SRC_DIR%\timotomata\ui\estilos.css" "%OUT_DIR%\timotomata\ui\estilos.css" >nul 2>&1

set MODULES=--module-path "%JAVAFX_DIR%/lib" --add-modules javafx.controls,javafx.graphics
set LIBS=%LIB_DIR%\*

%JAVAC_CMD% %MODULES% -cp "%LIBS%" -d "%OUT_DIR%" ^
    "%SRC_DIR%\timotomata\lexer\*.java" ^
    "%SRC_DIR%\timotomata\parser\*.java" ^
    "%SRC_DIR%\timotomata\parser\ast\*.java" ^
    "%SRC_DIR%\timotomata\ui\*.java" 2>&1

if errorlevel 1 (
    echo.
    echo [ERROR] Compilacion fallida.
    pause
    exit /b 1
)

echo [OK] Compilacion exitosa.
echo.

REM ═══════════════════════════════════════════════════════════════
REM  4. EJECUTAR
REM ═══════════════════════════════════════════════════════════════

echo [4/4] Iniciando interfaz grafica...
echo.
%JAVA_CMD% %MODULES% -cp "%OUT_DIR%;%LIBS%" timotomata.ui.MainApp

if errorlevel 1 (
    echo.
    pause
    exit /b 1
)
