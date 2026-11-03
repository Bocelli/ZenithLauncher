# 🌌 Zenith Launcher for Fire OS (TCL & Fire TV)

[![Fire OS 7 / 8 Compatible](https://img.shields.io/badge/Fire%20OS-7%20%2F%208%20Compatible-brightgreen.svg)](#)
[![Display](https://img.shields.io/badge/Resolution-True%204K%20UHD%20(3840x2160)-blue.svg)](#)
[![Zero Bloat](https://img.shields.io/badge/Size-~340%20KB-purple.svg)](#)
[![Native HDMI Switching](https://img.shields.io/badge/HDMI-TvView%20%2F%20Hardware%20TIF-orange.svg)](#)
[![No Root](https://img.shields.io/badge/Root-Not%20Required-success.svg)](#)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

> **Bilingual Documentation:** [English](README.md) • [Italiano](README_IT.md)

**Zenith Launcher** is an ultra-lightweight, ad-free, high-performance launcher engineered specifically to liberate Smart TVs and streaming devices running **Fire OS** (tailor-made and battle-tested for **TCL 75T6C / 75" 4K**, Fire TV Stick 4K / Max, and Omni Series TVs).

While Amazon's stock launcher consumes over **1.2 GB of RAM**, floods your screen with noisy auto-playing video ads, introduces noticeable remote lag, and attempts to lock you out of custom launchers, **Zenith Launcher restores rock-solid 60 FPS fluidity in an APK under ~340 KB, featuring native 4K rendering, instantaneous hardware HDMI switching, and zero telemetry.**

---

## 📸 Real Screenshots (Captured Directly from Fire TV Hardware)

| 🏠 Home Dashboard (4K with HDMI Inputs & Weather) | 📱 Full-Screen App Drawer |
|:---:|:---:|
| ![Zenith Home Live](docs/zenith_home_live.png) | ![Zenith Apps Live](docs/zenith_apps_live.png) |

| ⚙️ Zenith Settings & Screensaver Modal | 🔒 Per-App PIN Lock with Physical Remote Keypad |
|:---:|:---:|
| ![Zenith Settings Live](docs/zenith_settings_live.png) | ![PIN Lock Prompt](docs/pin_lock.png) |

---

## 🔄 Evolution: From Legacy to Zenith v2.0

Zenith Launcher has been completely reimagined compared to previous releases:

| Feature | Legacy Version | New Zenith v2.0 |
| :--- | :--- | :--- |
| **User Interface** | Minimal bottom dock, overlay drawer | Modern full-screen dashboard with animated Segmented Switch tabs (*Home* / *App*) |
| **Top Status Bar** | Static digital clock | Top bar with live clock, quick-access Zenith Settings, and dedicated TV Wi-Fi shortcut |
| **HDMI Inputs** | 4 static placeholder cards (broken on Fire OS) | **3 real hardware inputs wired to the MediaTek TIS chip (`android.media.tv.TvView`)** |
| **Input Switching** | Shell key events silently blocked by Amazon signatures | **Native full-screen HDMI player: ~130ms hardware lock, status HUD, and clean Back navigation** |
| **Input Customization** | Hardcoded titles and subtitles | **Rename device titles, subtitles, choose between 4 themed icons, or delete/hide input cards** |
| **Inputs Visibility** | Always visible on home | **Toggle Ingressi section visibility on/off, or reset TCL 3-HDMI defaults anytime in Settings** |
| **Live Weather** | Static city or automatic IP only | **Dynamic Open-Meteo integration with geocoding search to configure any city manually** |
| **Ambient Screensaver** | Fixed timer without playback awareness | **Configurable timer (1-30m / Off) with playback guard preventing screensaver during video/audio** |
| **APK Footprint** | ~1.3 MB | **~340 KB (even lighter, zero external libraries or AndroidX bloat)** |

---

## 📺 Hardware HDMI Switching Architecture (TCL 75T6C & MediaTek TIS)

On Amazon Fire OS, stock input-switch activities (`PassthroughPlayerActivity`) and key injection commands require proprietary system signatures (`TUNE_INPUT`, `INJECT_EVENTS`), which silently block third-party launchers from changing HDMI sources.

### How Zenith Solves It Natively:
Zenith Launcher features a dedicated, full-screen **[`HdmiActivity`](app/src/main/java/com/zenith/launcher/HdmiActivity.java)** that leverages Android's public **TV Input Framework (TIF)** and `android.media.tv.TvView`:
- Directly binds to the television's hardware TV Input Service (**`com.mediatek.tis/.HdmiInputService`**):
  - **HDMI 1**: `com.mediatek.tis/.HdmiInputService/HW2`
  - **HDMI 2**: `com.mediatek.tis/.HdmiInputService/HW3`
  - **HDMI 3**: `com.mediatek.tis/.HdmiInputService/HW4`
- When you click an HDMI card:
  1. The native full-screen HDMI player opens immediately.
  2. A status HUD banner reports *"Connecting..."* or *"No signal detected."* if your console, PC, or cable box is turned off.
  3. Once video sync is established, video seamlessly displays at full 60 FPS with zero latency or quality loss.
  4. Pressing **BACK** on your TV remote cleanly finishes the player and returns you instantly to the Zenith Home dashboard.

---

## 🛠️ Customizing, Hiding, or Mapping Inputs for Other TVs

### 1. Renaming or Deleting Cards Directly on TV
The 3 default cards are pre-configured out of the box for the **TCL 75T6C** (*Android TV / Switch 2 / Series X*):
* **Rename an input**: Long-press `OK/Enter` on any HDMI card to open the customize modal:
  * Edit the device name (e.g., *PlayStation 5*, *Apple TV*, *PC Gaming*);
  * Edit the subtitle (e.g., *HDMI 2 • Connected*);
  * Choose an icon between **HDMI**, **Gamepad**, **PC**, or **TV**.
* **Delete / Hide an input card**: In the edit modal, click **"🗑️ Remove this Input"**. That card is instantly hidden from your dashboard.
* **Hide the entire Inputs section**: If running Zenith on a Fire TV Stick, Fire TV Cube, or projector without physical inputs, open **Zenith Settings** and click **"📺 Inputs Section: Visible / Hidden"**.
* **Restore Defaults**: Reset back to the 3 default TCL HDMI ports anytime with **"🔄 Reset TCL Inputs (3 HDMI)"** in Settings.

---

### 2. Mapping Inputs for Another TV Model using Antigravity via ADB

If you have a different TV model (such as Sony Bravia, Philips, Xiaomi, Hisense, or Fire TV Omni) and want to map your physical HDMI ports, TV Tuner, or composite AV inputs:

You can have **Antigravity** (your agentic AI coding assistant) automatically inspect and configure your TV ports over an ADB connection:

```bash
# 1. Connect Antigravity to your TV via ADB
adb connect <TV_IP_ADDRESS>:5555

# 2. Inspect the hardware TIF inputs registered on your TV:
adb shell dumpsys tv_input
```

**What Antigravity does automatically:**
1. Parses your TV manufacturer's registered hardware paths (e.g. `HWx` IDs registered under `tv_input`).
2. Updates the input IDs inside [`MainActivity.java`](app/src/main/java/com/zenith/launcher/MainActivity.java) to match your physical ports.
3. Rebuilds `ZenithLauncher.apk` in seconds using `build.ps1` and installs the tailored build directly to your TV!

---

## ✨ Full Feature Overview

- ⚡ **Zero-Bloat Native Architecture (~340 KB)**: Built in pure native Android SDK, compiled with `javac` and dexed with `d8`. No AndroidX, no Jetpack, negligible RAM usage, consistent 60 FPS.
- 📺 **Native 4K UHD Razor-Sharp Rendering**: Full 3840×2160 vector rendering with 32-bit `RGBA_8888` color depth, resolving the "SD blur" seen in old launchers.
- 🕹️ **Hardware HDMI Switching**: Real-time hardware tuning with `android.media.tv.TvView`.
- 📍 **Open-Meteo Live Weather & Geocoding**:
  - Automatic IP-based geolocation.
  - Manual search modal with Open-Meteo Geocoding to select any city or town worldwide.
  - Clean display: icon, city, temperature, and condition (e.g. `☁️ Rome • 14°C Overcast`).
- 🔒 **Master PIN Application Lock**:
  - Protect sensitive apps with a 4-digit security PIN.
  - Full support for physical remote number buttons (0–9) on `ACTION_DOWN` with debouncing.
  - Master PIN management with emergency recovery PIN `0000`.
- 🌙 **Smart Anti-Burn-In Screensaver**:
  - Inactivity timer configurable from 1 to 30 minutes (or Disabled).
  - **Media Playback Guard**: Strictly checks `AudioManager.isMusicActive()` and active playback configurations so the screensaver will **never interrupt** movies, TV shows, or music.
  - Micro-drift animation to safeguard OLED and QLED panels.
- 🏠 **Fire OS Home Button Interceptor**:
  - Dedicated low-latency Accessibility Service (`HomeInterceptorService`) catches remote Home presses before Amazon's ad feed can load.

---

## 🚀 Quick Start & Installation

### Method 1: Automated 1-Click Installer (Recommended)
Connect your computer to the same Wi-Fi network as your TV.

#### On Windows:
1. Run `installer/install_windows.bat` (or right-click `installer/install_windows.ps1` -> *Run with PowerShell*).
2. Enter your TV's local IP address (found under *Settings > My Fire TV > About > Network*).
3. The script will automatically connect ADB, install the APK, and enable necessary accessibility permissions.

#### On macOS & Linux:
```bash
chmod +x installer/install_mac_linux.sh
./installer/install_mac_linux.sh
```

---

### Method 2: Manual ADB Commands

```bash
# 1. Connect to Fire TV
adb connect <FIRE_TV_IP>:5555

# 2. Install Zenith Launcher
adb install -r ZenithLauncher.apk

# 3. Enable Home Button Interceptor (Accessibility Service)
adb shell settings put secure enabled_accessibility_services com.zenith.launcher/com.zenith.launcher.HomeInterceptorService
adb shell settings put secure accessibility_enabled 1

# 4. Set Zenith as the default Screensaver (DreamService)
adb shell settings put secure screensaver_components com.zenith.launcher/com.zenith.launcher.ZenithScreensaverService
adb shell settings put secure screensaver_enabled 1

# 5. Launch Zenith Launcher
adb shell am start -n com.zenith.launcher/.MainActivity
```

---

## 📜 License

Released under the [MIT License](LICENSE). Built for complete freedom, speed, and privacy on your TV.
