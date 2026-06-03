@echo off
setlocal enabledelayedexpansion

set PROYECTO=Timotomata — GUI
set JAVAFX_DIR=%~dp0javafx-sdk-23.0.2
set SRC_DIR=%~dp0src
set OUT_DIR=%~dp0out
set LIB_DIR=%~dp0lib

echo ===== %PROYECTO% =====
echo.

REM ─── 1. Verificar JavaFX ───
if not exist "%JAVAFX_DIR%\lib\javafx.controls.jar" (
    echo [ERROR] JavaFX SDK no encontrado.
    echo Ejecuta primero compile_and_run_gui.bat para descargarlo.
    pause
    exit /b 1
)

REM ─── 2. Compilar si es necesario ───
echo [1/2] Compilando...

REM Copiar CSS
if not exist "%OUT_DIR%\timotomata\ui\" mkdir "%OUT_DIR%\timotomata\ui"
copy /Y "%SRC_DIR%\timotomata\ui\estilos.css" "%OUT_DIR%\timotomata\ui\estilos.css" >nul 2>&1

set MODULES=--module-path "%JAVAFX_DIR%/lib" --add-modules javafx.controls,javafx.graphics
set LIBS=%LIB_DIR%\*

REM Buscar javac
set JAVAC_CMD=
for /f "tokens=*" %%i in ('where javac 2^>nul') do (
    set JAVAC_CMD="%%i"
    goto :javac_found
)
if defined JAVA_HOME (
    if exist "!JAVA_HOME!\bin\javac.exe" (
        set JAVAC_CMD="!JAVA_HOME!\bin\javac"
        goto :javac_found
    )
)
echo [ERROR] No se encontro javac. Instala JDK 17+.
pause
exit /b 1

:javac_found
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

REM ─── 3. Ejecutar ───
echo [2/2] Iniciando interfaz grafica...
echo.
java %MODULES% -cp "%OUT_DIR%;%LIBS%" timotomata.ui.MainApp

if errorlevel 1 (
    echo.
    pause
    exit /b 1
)
