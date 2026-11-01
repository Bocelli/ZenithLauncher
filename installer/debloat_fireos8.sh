#!/usr/bin/env bash
# ================================================================
# Fire OS 8 Safe Debloat Tool (macOS / Linux)
# ================================================================

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}           FIRE OS 8 SAFE DEBLOAT TOOL (ZENITH PROJECT)         ${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""

read -p ">> Enter your Fire TV IP address (e.g. 192.168.1.50): " TV_IP
if [ -z "$TV_IP" ]; then
    echo -e "${RED}[ERROR] No IP provided.${NC}"
    exit 1
fi

adb connect "${TV_IP}:5555"

PACKAGES=(
    com.amazon.bueller.photos
    com.amazon.bueller.music
    com.amazon.ftv.screensaver
    com.amazon.tv.livetv
    com.amazon.visualsearch
    com.amazon.recon
    com.amazon.kato
    com.amazon.device.messaging
    com.amazon.ftv.gloria.dialogs
)

for pkg in "${PACKAGES[@]}"; do
    echo " - Disabling $pkg..."
    adb -s "${TV_IP}:5555" shell pm disable-user --user 0 "$pkg" || true
done

echo ""
echo -e "${GREEN}Debloat complete! Your Fire TV RAM and CPU have been freed.${NC}"
