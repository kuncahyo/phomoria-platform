@echo off
setlocal EnableExtensions

cd /d "%~dp0..\.."

set "IMAGE=target\Phomoria"
set "STAGE=target\phomoria-update-package"
set "OUTPUT=target\Phomoria-update.zip"

echo ============================================
echo PHOMORIA UPDATE PACKAGE
echo ============================================

if not exist "%IMAGE%\app\Phomoria.cfg" (
    echo ERROR: Packaged app image not found.
    echo Run build-app-image.bat first.
    exit /b 1
)

if not exist "%IMAGE%\native\ffmpeg\bin\ffmpeg.exe" (
    echo ERROR: FFmpeg runtime is missing from the app image.
    exit /b 1
)

if exist "%STAGE%" rmdir /s /q "%STAGE%"
if exist "%OUTPUT%" del /q "%OUTPUT%"

mkdir "%STAGE%\app"
mkdir "%STAGE%\native\ffmpeg"

echo Copying application files...
xcopy /E /I /Y "%IMAGE%\app\*" "%STAGE%\app\"
if errorlevel 1 exit /b 1

echo Copying FFmpeg runtime...
xcopy /E /I /Y "%IMAGE%\native\ffmpeg\*" "%STAGE%\native\ffmpeg\"
if errorlevel 1 exit /b 1

echo Creating update ZIP...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
    "Compress-Archive -Path '%STAGE%\app','%STAGE%\native' -DestinationPath '%OUTPUT%' -CompressionLevel Optimal -Force"

if errorlevel 1 (
    echo ERROR: Failed to create update ZIP.
    exit /b 1
)

echo.
echo UPDATE PACKAGE CREATED:
echo %OUTPUT%
echo.
echo ZIP structure:
echo app\...
echo native\ffmpeg\bin\ffmpeg.exe
echo native\ffmpeg\LICENSE
echo native\ffmpeg\README.txt
echo.
echo Upload this ZIP and publish its SHA-256 in update.json.
echo ============================================

exit /b 0
