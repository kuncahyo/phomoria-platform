@echo off
setlocal EnableExtensions

cd /d "%~dp0..\..\.."

echo ============================================
echo PHOMORIA v20 - WINDOWS INSTALLER BUILD
echo ============================================

call "%~dp0build-app-image.bat"
if errorlevel 1 exit /b 1

echo.
echo Creating EXE installer...

if exist target\installer rmdir /s /q target\installer
mkdir target\installer

jpackage ^
    --type exe ^
    --name Phomoria ^
    --app-version 0.1.0 ^
    --vendor "Phomoria" ^
    --description "Phomoria Photobooth Platform" ^
    --input target\app-input ^
    --main-jar phomoria-platform-0.1.0-SNAPSHOT.jar ^
    --main-class com.phomoria.app.Main ^
    --dest target\installer ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    --win-menu-group "Phomoria" ^
    --java-options "-Dfile.encoding=UTF-8"

if errorlevel 1 (
    echo.
    echo ERROR: installer creation failed.
    exit /b 1
)

echo.
echo ============================================
echo INSTALLER SUCCESSFUL
echo ============================================
echo Installer:
echo target\installer\Phomoria-0.1.0.exe
echo ============================================

exit /b 0
