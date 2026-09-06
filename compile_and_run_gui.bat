@echo off
setlocal enabledelayedexpansion

set PROYECTO=Compilador Timotomata — GUI
set JAVAFX_VER=23.0.2
if not defined LOCALAPPDATA set "LOCALAPPDATA=%TEMP%"
set "CACHE_DIR=%LOCALAPPDATA%\Timotomata"
set "JAVAFX_ZIP=%CACHE_DIR%\javafx-sdk-%JAVAFX_VER%.zip"
set "JAVAFX_DIR=%CACHE_DIR%\javafx-sdk-%JAVAFX_VER%"
set "RICH_DIR=%CACHE_DIR%\richtextfx"
set "RICH_CP=%RICH_DIR%\*"
set SRC_DIR=%~dp0src
set "OUT_DIR=%CACHE_DIR%\out"

if not exist "%CACHE_DIR%\" mkdir "%CACHE_DIR%"

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

REM Opcion D: Eclipse Temurin instalado con winget
for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\Eclipse Adoptium\jdk*" 2^>nul') do (
    if exist "C:\Program Files\Eclipse Adoptium\%%i\bin\javac.exe" (
        set JAVAC_CMD="C:\Program Files\Eclipse Adoptium\%%i\bin\javac"
        set JAVA_CMD="C:\Program Files\Eclipse Adoptium\%%i\bin\java"
        goto :jdk_found
    )
)

echo [ERROR] No se encontró JDK 17+ instalado.
echo.
echo Para instalar JDK:
echo   1. Ve a https://adoptium.net/
echo   2. Descarga el instalador para Windows
echo   3. Ejecutalo y marca "Add to PATH"
echo.
echo O usa el modo CLI (no necesita configuracion):
echo   compile_and_run_cli.bat
echo.
pause
exit /b 1

:jdk_found
echo [1/4] JDK detectado: %JAVAC_CMD%
echo       Java y javac disponibles.
echo.

REM ═══════════════════════════════════════════════════════════════
REM  2. VERIFICAR / DESCARGAR JAVAFX
REM ═══════════════════════════════════════════════════════════════

if exist "%JAVAFX_DIR%\lib\javafx.controls.jar" goto javafx_ready

echo [2/4] Descargando JavaFX SDK %JAVAFX_VER%...
echo      (Esto solo ocurre la primera vez)
echo.

set DOWNLOAD_URL=https://download2.gluonhq.com/openjfx/%JAVAFX_VER%/openjfx-%JAVAFX_VER%_windows-x64_bin-sdk.zip

REM Descargar usando variables de entorno para soportar rutas con espacios
set "TIMOTOMATA_FX_URL=!DOWNLOAD_URL!"
set "TIMOTOMATA_FX_ZIP=!JAVAFX_ZIP!"
set "TIMOTOMATA_CACHE=!CACHE_DIR!"
powershell -NoProfile -Command "$ErrorActionPreference = 'Stop'; Invoke-WebRequest -Uri $env:TIMOTOMATA_FX_URL -OutFile $env:TIMOTOMATA_FX_ZIP -UseBasicParsing"

if errorlevel 1 goto javafx_download_error

echo      Extrayendo...
powershell -NoProfile -Command "$ErrorActionPreference = 'Stop'; Expand-Archive -LiteralPath $env:TIMOTOMATA_FX_ZIP -DestinationPath $env:TIMOTOMATA_CACHE -Force"
if not exist "%JAVAFX_DIR%\lib\javafx.controls.jar" goto javafx_extract_error
echo [OK] JavaFX SDK listo.

:javafx_ready
echo [2/4] JavaFX SDK %JAVAFX_VER% encontrado.
echo.
goto compile

:javafx_download_error
echo.
echo [ERROR] No se pudo descargar JavaFX automaticamente.
echo.
echo Descargalo manualmente desde:
echo   https://gluonhq.com/products/javafx/
echo.
echo Guarda el archivo en: %CACHE_DIR%
echo con nombre: %JAVAFX_ZIP%
echo y vuelve a ejecutar este script.
pause
exit /b 1

:javafx_extract_error
echo [ERROR] No se pudo extraer JavaFX.
pause
exit /b 1

:compile

REM ═══════════════════════════════════════════════════════════════
REM  2B. VERIFICAR / DESCARGAR RICHTEXTFX
REM ═══════════════════════════════════════════════════════════════

if exist "%RICH_DIR%\richtextfx.jar" if exist "%RICH_DIR%\reactfx.jar" if exist "%RICH_DIR%\undofx.jar" if exist "%RICH_DIR%\flowless.jar" if exist "%RICH_DIR%\wellbehavedfx.jar" goto richtext_ready

echo [2B/4] Descargando RichTextFX...
if not exist "%RICH_DIR%\" mkdir "%RICH_DIR%"
set "TIMOTOMATA_RICH_DIR=!RICH_DIR!"
set "TIMOTOMATA_RICH_BASE=https://repo.maven.apache.org/maven2"
powershell -NoProfile -Command "$ErrorActionPreference = 'Stop'; $base=$env:TIMOTOMATA_RICH_BASE; $dir=$env:TIMOTOMATA_RICH_DIR; $items=@{'richtextfx'='org/fxmisc/richtext/richtextfx/0.11.2/richtextfx-0.11.2.jar'; 'reactfx'='org/reactfx/reactfx/2.0-M5/reactfx-2.0-M5.jar'; 'undofx'='org/fxmisc/undo/undofx/2.1.1/undofx-2.1.1.jar'; 'flowless'='org/fxmisc/flowless/flowless/0.7.2/flowless-0.7.2.jar'; 'wellbehavedfx'='org/fxmisc/wellbehaved/wellbehavedfx/0.3.3/wellbehavedfx-0.3.3.jar'}; foreach($name in $items.Keys) { Invoke-WebRequest -Uri ($base + '/' + $items[$name]) -OutFile (Join-Path $dir ($name + '.jar')) -UseBasicParsing -ErrorAction Stop }"
if errorlevel 1 goto richtext_error

:richtext_ready
echo [2B/4] RichTextFX disponible.
echo.

REM ═══════════════════════════════════════════════════════════════
REM  3. COMPILAR
REM ═══════════════════════════════════════════════════════════════

echo [3/4] Compilando...

REM Copiar CSS al directorio de salida
if not exist "%OUT_DIR%\timotomata\ui\" mkdir "%OUT_DIR%\timotomata\ui"
copy /Y "%SRC_DIR%\timotomata\ui\estilos.css" "%OUT_DIR%\timotomata\ui\estilos.css" >nul 2>&1

set MODULES=--module-path "%JAVAFX_DIR%/lib" --add-modules javafx.controls,javafx.graphics

REM Construir la lista real de fuentes; javac no expande comodines entre comillas
set "SOURCE_FILES="
for /r "%SRC_DIR%" %%f in (*.java) do set "SOURCE_FILES=!SOURCE_FILES! "%%f""

if not defined SOURCE_FILES (
    echo [ERROR] No se encontraron archivos fuente Java.
    pause
    exit /b 1
)

%JAVAC_CMD% %MODULES% -cp "%RICH_CP%" -d "%OUT_DIR%" !SOURCE_FILES! 2>&1

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
%JAVA_CMD% %MODULES% -cp "%OUT_DIR%;%RICH_CP%" timotomata.ui.MainApp

if errorlevel 1 (
    echo.
    pause
    exit /b 1
)

exit /b 0

:richtext_error
echo [ERROR] No se pudo descargar RichTextFX.
echo Revisa la conexión a internet y vuelve a ejecutar este script.
pause
exit /b 1
