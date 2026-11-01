@echo off
setlocal enabledelayedexpansion
title Zenith Launcher - Fire OS 8 Package Restore

echo ================================================================
echo           FIRE OS 8 PACKAGE RESTORE TOOL
echo ================================================================
echo.

set /p TV_IP=">> Enter your Fire TV IP address (e.g. 192.168.1.50): "
if "%TV_IP%"=="" (
    echo [ERROR] No IP provided.
    pause
    exit /b 1
)

echo.
echo Connecting to %TV_IP%:5555...
adb connect %TV_IP%:5555 >nul 2>&1

echo.
echo Re-enabling packages...

set PACKAGES=com.amazon.bueller.photos com.amazon.bueller.music com.amazon.ftv.screensaver com.amazon.tv.livetv com.amazon.visualsearch com.amazon.recon com.amazon.kato com.amazon.device.messaging com.amazon.ftv.gloria.dialogs

for %%p in (%PACKAGES%) do (
    echo  - Enabling %%p...
    adb -s %TV_IP%:5555 shell pm enable %%p >nul 2>&1
)

echo.
echo All packages restored to default state!
pause
