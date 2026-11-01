@echo off
setlocal enabledelayedexpansion
title Zenith Launcher - 1-Click Installer for Fire OS 8

echo ================================================================
echo           ZENITH LAUNCHER - FIRE OS 8 AUTO-INSTALLER
echo       Ultra-clean Apple TV-style Launcher for Amazon Fire TV
echo ================================================================
echo.

:: 1. Check for adb
where adb >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    set ADB=adb
) else (
    if exist "%~dp0..\..\platform-tools\adb.exe" (
        set ADB="%~dp0..\..\platform-tools\adb.exe"
    ) else if exist "C:\Android\platform-tools\adb.exe" (
        set ADB="C:\Android\platform-tools\adb.exe"
    ) else (
        echo [ERROR] ADB not found! Please install Android Platform Tools
        echo or put adb.exe in the same folder as this script.
        pause
        exit /b 1
    )
)

:: 2. Find APK
set APK=%~dp0..\ZenithLauncher.apk
if not exist "%APK%" (
    set APK=%~dp0ZenithLauncher.apk
)
if not exist "%APK%" (
    echo [ERROR] ZenithLauncher.apk not found!
    echo Ensure ZenithLauncher.apk is in the project folder.
    pause
    exit /b 1
)

:: 3. Prompt for TV IP
set /p TV_IP=">> Enter your Fire TV IP address (e.g. 192.168.1.50): "
if "%TV_IP%"=="" (
    echo [ERROR] No IP provided.
    pause
    exit /b 1
)

echo.
echo [1/5] Connecting to Fire TV at %TV_IP%:5555...
%ADB% connect %TV_IP%:5555
timeout /t 2 >nul

:: Verify connection
%ADB% devices | findstr /i "%TV_IP%" >nul
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Could not connect to Fire TV.
    echo Please make sure:
    echo  1. Fire TV is powered ON and on the same Wi-Fi network.
    echo  2. ADB Debugging is enabled (Settings -^> My Fire TV -^> Developer Options).
    echo  3. Accept any "Allow USB debugging?" prompt on your TV screen.
    pause
    exit /b 1
)

echo.
echo [2/5] Installing Zenith Launcher APK...
%ADB% -s %TV_IP%:5555 install -r "%APK%"
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Install retry with grant permissions...
    %ADB% -s %TV_IP%:5555 install -r -g "%APK%"
)

echo.
echo [3/5] Enabling Home Button Interceptor (Accessibility Service)...
%ADB% -s %TV_IP%:5555 shell settings put secure enabled_accessibility_services com.zenith.launcher/com.zenith.launcher.HomeInterceptorService
%ADB% -s %TV_IP%:5555 shell settings put secure accessibility_enabled 1

echo.
echo [4/5] Enabling Ambient 4K Screensaver (DreamService)...
%ADB% -s %TV_IP%:5555 shell settings put secure screensaver_components com.zenith.launcher/com.zenith.launcher.ZenithScreensaverService
%ADB% -s %TV_IP%:5555 shell settings put secure screensaver_enabled 1

echo.
echo [5/5] Launching Zenith Launcher on Fire TV...
%ADB% -s %TV_IP%:5555 shell am start -n com.zenith.launcher/.MainActivity

echo.
echo ================================================================
echo                     INSTALLATION COMPLETE!
echo  Zenith Launcher is now active on your Fire TV.
echo  Press HOME on your remote to test the instant return to Zenith.
echo ================================================================
echo.
pause
