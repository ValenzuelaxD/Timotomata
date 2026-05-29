@echo off
setlocal enabledelayedexpansion

set PROYECTO=Compilador Timotomata — GUI
set JAVAFX_VER=23.0.2
set JAVAFX_ZIP=javafx-sdk-%JAVAFX_VER%.zip
set JAVAFX_DIR=%~dp0javafx-sdk-%JAVAFX_VER%
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
for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\Java\jdk*" 2^>nul') do (
    if exist "C:\Program Files\Java\%%i\bin\javac.exe" (
        set JAVAC_CMD="C:\Program Files\Java\%%i\bin\javac"
        set JAVA_CMD="C:\Program Files\Java\%%i\bin\java"
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
set JAVA_VER=
for /f "tokens=3" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /r "[0-9]\.[0-9]"') do set JAVA_VER=%%v
echo [1/4] JDK detectado: %JAVAC_CMD%
for /f "tokens=1" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr "version"') do echo       %%v
if not defined JAVA_VER echo       (version detectada)
echo.

REM ═══════════════════════════════════════════════════════════════
REM  2. VERIFICAR / DESCARGAR JAVAFX
REM ═══════════════════════════════════════════════════════════════

if exist "%JAVAFX_DIR%\lib\javafx.controls.jar" (
    echo [2/4] JavaFX SDK %JAVAFX_VER% encontrado.
) else (
    echo [2/4] Descargando JavaFX SDK %JAVAFX_VER%...
    echo      (Esto solo ocurre la primera vez)
    echo.

    set DOWNLOAD_URL=https://download2.gluonhq.com/openjfx/%JAVAFX_VER%/openjfx-%JAVAFX_VER%_windows-x64_bin-sdk.zip

    REM Intentar con PowerShell
    powershell -Command "& { param($u,$z) Write-Host 'Descargando...'; try { Invoke-WebRequest -Uri $u -OutFile $z -UseBasicParsing -ErrorAction Stop; Write-Host 'OK' } catch { Write-Host 'ERROR:' $_.Exception.Message; exit 1 } }" -u "!DOWNLOAD_URL!" -z "%~dp0%JAVAFX_ZIP%"

    if errorlevel 1 (
        echo.
        echo [ERROR] No se pudo descargar JavaFX automaticamente.
        echo.
        echo Descargalo manualmente desde:
        echo   https://gluonhq.com/products/javafx/
        echo.
        echo Guarda el archivo en: %~dp0
        echo con nombre: %JAVAFX_ZIP%
        echo y vuelve a ejecutar este script.
        pause
        exit /b 1
    )

    echo      Extrayendo...
    powershell -Command "Expand-Archive -Path '%~dp0%JAVAFX_ZIP%' -DestinationPath '%~dp0' -Force 2>&1 | Out-Null"
    if exist "%~dp0javafx-sdk-%JAVAFX_VER%" (
        echo [OK] JavaFX SDK listo.
    ) else (
        echo [ERROR] No se pudo extraer JavaFX.
        pause
        exit /b 1
    )
)
echo.

REM ═══════════════════════════════════════════════════════════════
REM  3. COMPILAR
REM ═══════════════════════════════════════════════════════════════

echo [3/4] Compilando...

REM Copiar CSS al directorio de salida
if not exist "%OUT_DIR%\timotomata\ui\" mkdir "%OUT_DIR%\timotomata\ui"
copy /Y "%SRC_DIR%\timotomata\ui\estilos.css" "%OUT_DIR%\timotomata\ui\estilos.css" >nul 2>&1

set MODULES=--module-path "%JAVAFX_DIR%/lib" --add-modules javafx.controls,javafx.graphics

%JAVAC_CMD% %MODULES% -d "%OUT_DIR%" ^
    "%SRC_DIR%\timotomata\Main.java" ^
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
%JAVA_CMD% %MODULES% -cp "%OUT_DIR%" timotomata.ui.MainApp

if errorlevel 1 (
    echo.
    pause
    exit /b 1
)
