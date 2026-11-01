#!/usr/bin/env bash
# ================================================================
# Zenith Launcher - 1-Click Installer for Fire OS 8 (macOS & Linux)
# ================================================================

set -e

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}          ZENITH LAUNCHER - FIRE OS 8 AUTO-INSTALLER            ${NC}"
echo -e "${CYAN}      Ultra-clean Apple TV-style Launcher for Fire TV           ${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""

# 1. Check for adb
if ! command -v adb &> /dev/null; then
    echo -e "${RED}[ERROR] ADB not found!${NC}"
    echo -e "${YELLOW}Install Android Platform Tools (e.g. 'brew install android-platform-tools' on macOS or 'sudo apt install adb' on Ubuntu).${NC}"
    exit 1
fi

# 2. Locate APK
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$DIR/../ZenithLauncher.apk"
if [ ! -f "$APK" ]; then
    APK="$DIR/ZenithLauncher.apk"
fi

if [ ! -f "$APK" ]; then
    echo -e "${RED}[ERROR] ZenithLauncher.apk not found!${NC}"
    exit 1
fi

# 3. Prompt for TV IP
read -p ">> Enter your Fire TV IP address (e.g. 192.168.1.50): " TV_IP
if [ -z "$TV_IP" ]; then
    echo -e "${RED}[ERROR] No IP provided.${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}[1/5] Connecting to Fire TV at ${TV_IP}:5555...${NC}"
adb connect "${TV_IP}:5555"
sleep 2

if ! adb devices | grep -q "${TV_IP}"; then
    echo -e "${RED}[ERROR] Could not connect to Fire TV at ${TV_IP}.${NC}"
    echo "Please ensure:"
    echo " 1. Fire TV is powered ON and connected to the same Wi-Fi."
    echo " 2. ADB Debugging is enabled in Settings -> My Fire TV -> Developer Options."
    echo " 3. Accept any 'Allow USB debugging?' dialog on your TV screen."
    exit 1
fi

echo -e "${GREEN}[2/5] Installing Zenith Launcher APK...${NC}"
adb -s "${TV_IP}:5555" install -r -g "$APK"

echo -e "${GREEN}[3/5] Enabling Home Button Interceptor (Accessibility Service)...${NC}"
adb -s "${TV_IP}:5555" shell settings put secure enabled_accessibility_services com.zenith.launcher/com.zenith.launcher.HomeInterceptorService
adb -s "${TV_IP}:5555" shell settings put secure accessibility_enabled 1

echo -e "${GREEN}[4/5] Enabling Ambient 4K Screensaver (DreamService)...${NC}"
adb -s "${TV_IP}:5555" shell settings put secure screensaver_components com.zenith.launcher/com.zenith.launcher.ZenithScreensaverService
adb -s "${TV_IP}:5555" shell settings put secure screensaver_enabled 1

echo -e "${GREEN}[5/5] Launching Zenith Launcher on Fire TV...${NC}"
adb -s "${TV_IP}:5555" shell am start -n com.zenith.launcher/.MainActivity

echo ""
echo -e "${CYAN}================================================================${NC}"
echo -e "${GREEN}                    INSTALLATION COMPLETE!                      ${NC}"
echo -e "  Zenith Launcher is now active on your Fire TV."
echo -e "  Press HOME on your remote to test the instant return."
echo -e "${CYAN}================================================================${NC}"
