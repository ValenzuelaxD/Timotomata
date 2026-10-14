@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM  Timotomata - compilador de linea de comandos
REM
REM  Corre las cuatro fases del front-end (lexico, sintactico, semantico y el
REM  volcado de la tabla de simbolos) sin abrir la interfaz grafica.
REM
REM    compile_and_run_cli.bat                     -> tests\*.txt
REM    compile_and_run_cli.bat mi_programa.txt     -> un archivo
REM    compile_and_run_cli.bat -v *.txt            -> con tabla, flujo y memoria
REM
REM  Devuelve 0 si todo compila y 1 si hay errores, asi que sirve como
REM  comprobacion automatica:  compile_and_run_cli.bat || echo FALLO
REM ============================================================================

set PROYECTO=Timotomata " compilador de linea de comandos

if not defined LOCALAPPDATA set "LOCALAPPDATA=%TEMP%"
set "CACHE_DIR=%LOCALAPPDATA%\Timotomata"
set "OUT_DIR=%CACHE_DIR%\out"
set SRC_DIR=%~dp0src

echo ===== %PROYECTO% =====
echo.

REM --- 1. JDK ----------------------------------------------------------------

set JAVAC_CMD=
set JAVA_CMD=

REM Las variables se guardan CON comillas a proposito: las rutas de instalacion
REM suelen tener espacios ("C:\Program Files\...") y sin ellas la invocacion se
REM parte en dos palabras.

if defined JAVA_HOME (
    if exist "!JAVA_HOME!\bin\javac.exe" (
        set JAVAC_CMD="!JAVA_HOME!\bin\javac.exe"
        set JAVA_CMD="!JAVA_HOME!\bin\java.exe"
        goto :jdk_found
    )
)

for /f "tokens=*" %%i in ('where javac 2^>nul') do (
    for %%f in ("%%i") do set JAVAC_CMD="%%f"
    for %%f in ("%%i") do set JAVA_CMD="%%~dpfjava.exe"
    goto :jdk_found
)

for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\Java\jdk*" 2^>nul') do (
    if exist "C:\Program Files\Java\%%i\bin\javac.exe" (
        set JAVAC_CMD="C:\Program Files\Java\%%i\bin\javac.exe"
        set JAVA_CMD="C:\Program Files\Java\%%i\bin\java.exe"
        goto :jdk_found
    )
)

for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\Eclipse Adoptium\jdk*" 2^>nul') do (
    if exist "C:\Program Files\Eclipse Adoptium\%%i\bin\javac.exe" (
        set JAVAC_CMD="C:\Program Files\Eclipse Adoptium\%%i\bin\javac.exe"
        set JAVA_CMD="C:\Program Files\Eclipse Adoptium\%%i\bin\java.exe"
        goto :jdk_found
    )
)

echo [ERROR] No se encontro un JDK 17 o superior.
echo.
echo Para instalarlo:
echo   1. Ve a https://adoptium.net/
echo   2. Descarga el instalador para Windows
echo   3. Ejecutalo y marca "Add to PATH"
echo.
exit /b 1

:jdk_found
echo [1/3] JDK detectado.
echo.

REM --- 2. COMPILAR -----------------------------------------------------------
REM Solo el front-end. La interfaz grafica necesita JavaFX y no aporta nada
REM para compilar un programa, asi que se excluye del SOURCE_FILES.

if not exist "%OUT_DIR%\" mkdir "%OUT_DIR%"

set "SOURCE_FILES="
for /r "%SRC_DIR%" %%f in (*.java) do (
    set "FP=%%f"
    if "!FP!"=="!FP:\ui\=!" set "SOURCE_FILES=!SOURCE_FILES! ""%%f"""
)

if not defined SOURCE_FILES (
    echo [ERROR] No se encontraron fuentes del front-end.
    exit /b 1
)

echo [2/3] Compilando el front-end...

%JAVAC_CMD% -d "%OUT_DIR%" !SOURCE_FILES!

if errorlevel 1 (
    echo.
    echo [ERROR] La compilacion fallo.
    exit /b 1
)

echo [OK] Compilacion exitosa.
echo.

REM --- 3. EJECUTAR -----------------------------------------------------------

echo [3/3] Analizando...

set "ARGS="
if "%~1"=="" (
    for %%f in ("%~dp0tests\*.txt") do set "ARGS=!ARGS! ""%%f"""
) else (
    set "ARGS=%*"
)

%JAVA_CMD% -cp "%OUT_DIR%" timotomata.tools.CompiladorCLI !ARGS!

exit /b %ERRORLEVEL%