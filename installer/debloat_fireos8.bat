@echo off
setlocal enabledelayedexpansion
title Zenith Launcher - Fire OS 8 Safe Debloat Tool

echo ================================================================
echo           FIRE OS 8 SAFE DEBLOAT TOOL (ZENITH PROJECT)
echo    Disables telemetry, background ad scrapers, and bloatware
echo    (Safe: preserves Prime Video, Netflix, HDMI, Remote & WiFi)
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
echo Disabling safe bloatware packages...

set PACKAGES=com.amazon.bueller.photos com.amazon.bueller.music com.amazon.ftv.screensaver com.amazon.tv.livetv com.amazon.visualsearch com.amazon.recon com.amazon.kato com.amazon.device.messaging com.amazon.ftv.gloria.dialogs

for %%p in (%PACKAGES%) do (
    echo  - Disabling %%p...
    adb -s %TV_IP%:5555 shell pm disable-user --user 0 %%p >nul 2>&1
)

echo.
echo ================================================================
echo Debloat complete! Your Fire TV RAM and CPU have been freed.
echo To restore all packages at any time, run restore_fireos8.bat
echo ================================================================
echo.
pause
