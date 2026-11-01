<#
.SYNOPSIS
    Zenith Launcher - 1-Click PowerShell Installer for Amazon Fire TV (Fire OS 8)
#>

$ErrorActionPreference = "Stop"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "          ZENITH LAUNCHER - FIRE OS 8 AUTO-INSTALLER           " -ForegroundColor White
Write-Host "      Ultra-clean Apple TV-style Launcher for Fire TV          " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Locate ADB
$adb = $null
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $adb = "adb"
} elseif (Test-Path "C:\Android\platform-tools\adb.exe") {
    $adb = "C:\Android\platform-tools\adb.exe"
} elseif (Test-Path "$PSScriptRoot\..\..\platform-tools\adb.exe") {
    $adb = "$PSScriptRoot\..\..\platform-tools\adb.exe"
} else {
    Write-Host "[ERROR] ADB executable not found!" -ForegroundColor Red
    Write-Host "Please install Android Platform Tools or place adb.exe in your PATH." -ForegroundColor Yellow
    exit 1
}

# 2. Locate APK
$apk = "$PSScriptRoot\..\ZenithLauncher.apk"
if (-not (Test-Path $apk)) {
    $apk = "$PSScriptRoot\ZenithLauncher.apk"
}
if (-not (Test-Path $apk)) {
    Write-Host "[ERROR] ZenithLauncher.apk not found!" -ForegroundColor Red
    exit 1
}

# 3. Prompt for IP
$tvIp = Read-Host ">> Enter your Fire TV IP address (e.g. 192.168.1.50)"
if ([string]::IsNullOrWhiteSpace($tvIp)) {
    Write-Host "[ERROR] TV IP address is required." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[1/5] Connecting to Fire TV at ${tvIp}:5555..." -ForegroundColor Yellow
& $adb connect "${tvIp}:5555" | Out-Null
Start-Sleep -Seconds 2

$devices = & $adb devices
if ($devices -notmatch $tvIp) {
    Write-Host "[ERROR] Could not connect to Fire TV at $tvIp." -ForegroundColor Red
    Write-Host "Verify that:" -ForegroundColor Yellow
    Write-Host " 1. Fire TV is powered ON and on the same Wi-Fi network."
    Write-Host " 2. ADB Debugging is enabled (Settings -> My Fire TV -> Developer Options)."
    Write-Host " 3. Accept any 'Allow USB debugging?' prompt on your TV screen."
    exit 1
}

Write-Host "[2/5] Installing Zenith Launcher APK..." -ForegroundColor Green
& $adb -s "${tvIp}:5555" install -r -g $apk | Out-Null

Write-Host "[3/5] Enabling Home Button Interceptor (Accessibility Service)..." -ForegroundColor Green
& $adb -s "${tvIp}:5555" shell settings put secure enabled_accessibility_services com.zenith.launcher/com.zenith.launcher.HomeInterceptorService | Out-Null
& $adb -s "${tvIp}:5555" shell settings put secure accessibility_enabled 1 | Out-Null

Write-Host "[4/5] Enabling Ambient 4K Screensaver (DreamService)..." -ForegroundColor Green
& $adb -s "${tvIp}:5555" shell settings put secure screensaver_components com.zenith.launcher/com.zenith.launcher.ZenithScreensaverService | Out-Null
& $adb -s "${tvIp}:5555" shell settings put secure screensaver_enabled 1 | Out-Null

Write-Host "[5/5] Launching Zenith Launcher on Fire TV..." -ForegroundColor Green
& $adb -s "${tvIp}:5555" shell am start -n com.zenith.launcher/.MainActivity | Out-Null

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "                    INSTALLATION COMPLETE!                      " -ForegroundColor Green
Write-Host "  Zenith Launcher is now running on your Fire TV.               " -ForegroundColor White
Write-Host "  Press HOME on your remote to test the instant return.         " -ForegroundColor Yellow
Write-Host "================================================================" -ForegroundColor Cyan
