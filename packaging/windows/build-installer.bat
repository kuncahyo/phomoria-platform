```bat
@echo off
setlocal EnableExtensions

cd /d "%~dp0..\..\.."

echo ============================================
echo PHOMORIA 0.3.2 - WINDOWS INSTALLER BUILD
echo ============================================

call "%~dp0build-app-image.bat"
if errorlevel 1 exit /b 1

echo.
echo Creating EXE installer with Inno Setup...

set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
set "ISS_FILE=%CD%\build\phomoria-package\installer\Phomoria-Setup.iss"

if not exist "%ISCC%" (
    echo ERROR: ISCC.exe not found.
    exit /b 1
)

if not exist "%ISS_FILE%" (
    echo ERROR: Phomoria-Setup.iss not found.
    exit /b 1
)

"%ISCC%" "%ISS_FILE%"

if errorlevel 1 (
    echo.
    echo ERROR: Installer creation failed.
    exit /b 1
)

echo.
echo ============================================
echo INSTALLER SUCCESSFUL
echo ============================================
echo Installer:
echo build\phomoria-package\installer\Phomoria-Setup-0.3.2.exe
echo ============================================

exit /b 0
```