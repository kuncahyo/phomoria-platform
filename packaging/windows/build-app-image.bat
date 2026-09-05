@echo off
setlocal EnableExtensions

cd /d "%~dp0..\..\"

echo ============================================
echo PHOMORIA - WINDOWS APP IMAGE BUILD
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
echo [1/6] Cleaning Maven build...
call mvn clean
if errorlevel 1 exit /b 1

echo.
echo [2/6] Building Phomoria...
call mvn package -DskipTests
if errorlevel 1 exit /b 1

echo.
echo [3/6] Collecting runtime dependencies...
if exist target\app-input rmdir /s /q target\app-input
mkdir target\app-input
mkdir target\app-input\lib

copy /y target\phomoria-platform-0.1.0-SNAPSHOT.jar target\app-input\ >nul
if errorlevel 1 exit /b 1

call mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\app-input\lib -DexcludeTransitive=false
if errorlevel 1 exit /b 1

echo.
echo [4/6] Bundling native gPhoto2 helper...
if not exist tools\gphoto2\phomoria-gphoto2-camera.exe (
    echo ERROR: Native gPhoto2 helper not found:
    echo tools\gphoto2\phomoria-gphoto2-camera.exe
    echo Build it with MSYS2/UCRT64 before packaging.
    exit /b 1
)

mkdir target\app-input\native\gphoto2
copy /y tools\gphoto2\phomoria-gphoto2-camera.exe target\app-input\native\gphoto2\ >nul
if errorlevel 1 exit /b 1

rem Optional native runtime directory. If populated, copy all DLLs beside the helper.
if exist tools\gphoto2\runtime (
    xcopy /e /i /y tools\gphoto2\runtime target\app-input\native\gphoto2\runtime >nul
)

echo.
echo [5/6] Creating Windows application image...
if exist target\Phomoria rmdir /s /q target\Phomoria

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
echo [6/6] Building PhomoriaUpdater...
dotnet publish updater\PhomoriaUpdater\PhomoriaUpdater.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:PublishTrimmed=false -o target\Phomoria\updater-publish
if errorlevel 1 (
    echo.
    echo ERROR: PhomoriaUpdater publish failed.
    exit /b 1
)

copy /y target\Phomoria\updater-publish\PhomoriaUpdater.exe target\Phomoria\PhomoriaUpdater.exe >nul
if errorlevel 1 exit /b 1
rmdir /s /q target\Phomoria\updater-publish

echo ============================================
echo BUILD SUCCESSFUL
echo ============================================
echo Application: target\Phomoria\Phomoria.exe
echo Updater:     target\Phomoria\PhomoriaUpdater.exe
echo ============================================
exit /b 0
