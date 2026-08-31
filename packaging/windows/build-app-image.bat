@echo off
setlocal EnableExtensions

cd /d "%~dp0..\..\"

echo ============================================
echo PHOMORIA v20 - WINDOWS APP IMAGE BUILD
echo ============================================

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java was not found in PATH.
    echo Please run this script from a JDK 21 environment.
    exit /b 1
)

where jpackage >nul 2>nul
if errorlevel 1 (
    echo ERROR: jpackage was not found.
    echo A JDK 21 installation is required.
    exit /b 1
)

echo.
echo [1/4] Cleaning Maven build...
call mvn clean
if errorlevel 1 exit /b 1

echo.
echo [2/4] Building Phomoria...
call mvn package -DskipTests
if errorlevel 1 exit /b 1

echo.
echo [3/4] Collecting runtime dependencies...
if exist target\app-input rmdir /s /q target\app-input
mkdir target\app-input

copy /y target\phomoria-platform-0.1.0-SNAPSHOT.jar target\app-input\ >nul
if errorlevel 1 exit /b 1

call mvn dependency:copy-dependencies ^
    -DincludeScope=runtime ^
    -DoutputDirectory=target\app-input\lib ^
    -DexcludeTransitive=false
if errorlevel 1 exit /b 1

echo.
echo [4/4] Creating Windows application image...

if exist target\phomoria-app rmdir /s /q target\phomoria-app

jpackage ^
    --type app-image ^
    --name Phomoria ^
    --app-version 0.1.0 ^
    --vendor "Phomoria" ^
    --description "Phomoria Photobooth Platform" ^
    --input target\app-input ^
    --main-jar phomoria-platform-0.1.0-SNAPSHOT.jar ^
    --main-class com.phomoria.app.Main ^
    --dest target ^
    --java-options "-Dfile.encoding=UTF-8"

if errorlevel 1 (
    echo.
    echo ERROR: jpackage failed.
    exit /b 1
)

echo.
echo ============================================
echo BUILD SUCCESSFUL
echo ============================================
echo Application:
echo target\Phomoria\Phomoria.exe
echo.
echo Run:
echo target\Phomoria\Phomoria.exe
echo ============================================

exit /b 0
