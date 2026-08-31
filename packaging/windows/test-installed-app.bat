@echo off
setlocal EnableExtensions

cd /d "%~dp0..\..\.."

if not exist target\Phomoria\Phomoria.exe (
    echo Application image not found.
    echo Run build-app-image.bat first.
    exit /b 1
)

echo Starting Phomoria application image...
start "" "%cd%\target\Phomoria\Phomoria.exe"

exit /b 0
