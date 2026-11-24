# Framed — Aesthetic Android Hybrid Edge/Cloud AI Camera & Darkroom

<p align="center">
  <img src="https://raw.githubusercontent.com/Koushikv18/framed/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="100" alt="Framed Logo" />
  <br>
  <em>A native Android camera and aesthetic photo-editing darkroom with 1:1 iOS "Framed 2.0" parity plus live self-upgrading AI.</em>
  <br>
  <strong>Engineered for Android 17 (API 37) & Samsung Galaxy S25 Ultra (Snapdragon 8 Elite, 120Hz AMOLED)</strong>
</p>

<p align="center">
  <a href="https://github.com/Koushikv18/framed/releases/download/latest/Framed.apk"><img src="https://img.shields.io/badge/Download-Latest%20APK-00E599?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" /></a>
  <a href="https://github.com/Koushikv18/framed/releases/tag/latest"><img src="https://img.shields.io/badge/Release-Latest%20Build-18181B?style=for-the-badge&logo=github" alt="GitHub Releases" /></a>
  <img src="https://img.shields.io/badge/Platform-Android%2015%20%7C%2017%20(API%2037)-18181B?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/AI%20Engine-MediaPipe%20%2B%20TFLite%20%2B%20Gemini%20Flash-00E599?style=for-the-badge" alt="AI Engine" />
  <img src="https://img.shields.io/badge/Toolchain-AGP%209.4.1%20%7C%20Gradle%209.6.0-18181B?style=for-the-badge" alt="Toolchain" />
</p>

---

## 📥 Download APK

### [👉 Click Here to Download Latest APK (Framed.apk)](https://github.com/Koushikv18/framed/releases/download/latest/Framed.apk)

| Asset | Target OS | Build Type | Download Link |
| :--- | :--- | :--- | :--- |
| **`Framed.apk`** | Android 15 – 17 (`minSdk 35`, `compileSdk 37`, `targetSdk 37`) | Automated CI/CD (`main`) | [**Download Framed.apk**](https://github.com/Koushikv18/framed/releases/download/latest/Framed.apk) |
| **Release Details** | GitHub Automated Release | Tag `latest` | [**View Release**](https://github.com/Koushikv18/framed/releases/tag/latest) |

> **Local Machine File**: The compiled APK is ready right inside this project at:
> - **`Framed.apk`** / **`framed-app-debug.apk`** (Workspace root)
> - **`app/build/outputs/apk/debug/app-debug.apk`** (Build outputs)

---

## ⚡ Quick Install Guide

### Option 1: Direct Install on Android Phone
1. Open this page on your Android device and tap **[Download Latest APK](https://github.com/Koushikv18/framed/releases/download/latest/Framed.apk)**.
2. Tap the downloaded file and select **Install** (allow *Install unknown apps* if prompted by your browser or file manager).
3. Launch **Framed** and grant Camera permissions.

### Option 2: Install via ADB (USB / Wi-Fi Debugging)
Connect your device with USB Debugging enabled, then run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or launch directly after install:
```bash
adb shell am start -n com.aesthetic.framed/.MainActivity
```

---

## ✨ Key Features

### 📸 S25 Ultra Quad-Lens Hardware Camera
- **Seamless Lens Switching**: Tactile pills with haptic feedback (`HapticFeedbackType.TextHandleMove`):
  - `.6` — `13mm f/1.9` (Ultra-wide)
  - `1x` — `23mm f/1.7` (Wide standard)
  - `3x` — `69mm f/2.4` (Telephoto)
  - `5x` — `115mm f/3.4` (Periscope telephoto)
- **Samsung Telephoto RAW Guardrail**:
  - Uncompressed Adobe DNG RAW capture supported on `0.6x` and `1.0x` wide lenses.
  - Telephoto `3x` and `5x` lenses automatically switch to 98% quality JPEG to eliminate Samsung's third-party telephoto RAW sensor corruption bug.
- **Manual Controls Drawer**:
  - Live ISO (50 – 6400) & Shutter Speed (1/2000s – 1s) with `CONTROL_AE_MODE_OFF`.
  - Continuous Exposure Value (EV) compensation slider (-3.0 EV to +3.0 EV) with `CONTROL_AE_MODE_ON`.
  - Flash modes (`OFF`, `ON`, `AUTO`, `TORCH`).
- **Hardware Level**: Real-time roll/pitch horizon leveling using `SensorManager.TYPE_ROTATION_VECTOR` with visual angle badge and emerald level indicator.

### 🎯 On-Device Composition Coach
- **100% Offline AI Model**: Powered by Google MediaPipe Tasks Vision (`efficientdet_lite2.tflite`) bundled on-device model (zero network queries).
- **Auto Frame Mode**: Real-time analysis of subject framing, rule of thirds, horizon tilt, and scene luminance. Live smart hints (*"Center on thirds"*, *"Zoom in to 3x"*, *"Step back to 0.6x"*, *"Hold steady"*).
- **Draw-to-Track Mode**: Interactive viewfinder bounding box with 4-corner drag handles (`RESIZE_TL`, `RESIZE_TR`, `RESIZE_BL`, `RESIZE_BR`) and center repositioning. Shutter highlights emerald green when alignment meets the IoU $\ge 0.55$ threshold.

### 🎨 Unified OpenGL ES 3.0 Darkroom
- **Zero Coordinate Mismatch**: Pixel-perfect parity between interactive 120fps preview and full-resolution headless EGL FBO export.
- **1080p Normalized Grain**: Film grain UV scale is normalized so live preview and 50MP export grain appearance match identically.
- **Full Creative Suite**:
  - **Light**: Exposure, Contrast, Highlights, Shadows, Whites, Blacks, Fade, Temp, Tint.
  - **Color**: 6-channel HSL mixer (Hue, Saturation, Luminance) & 3-way Split-Tone Color Grading (Shadows, Midtones, Highlights).
  - **Tone Curves**: Master RGB curve, individual R/G/B channels, and primary calibration.
  - **Effects**: Halation highlight bloom, Pro-Mist glow, Light Leaks (Corner Warm, Top Streak, Rainbow Prism), Scratches & Dust, Vignette, and Film Grain.
  - **Film Presets**: `N1600` (high-contrast moody color), `P400` (warm pastel portra), `RELIC` (vintage faded amber/green), and `MONO` (contrast B&W).

### 🖼️ Aesthetic Frames & Non-Destructive Library
- **Frame Presets**: `NONE`, `WHITE`, `PAPER` (textured matte), `BLACK`, `NEGATIVE` (sprocket border), and `SPECS` (camera model, 35mm-equivalent focal length, aperture, shutter speed, and ISO).
- **Retro Amber LCD Date Stamp**: Authentic retro camera date stamp rendered with segment typography.
- **Non-Destructive Recipes**: Photos and edit recipe JSONs are saved in private app storage. Re-edit any photo or import from your system gallery at any time.

---

## 🔒 100% Offline Privacy Guarantee

- `android.permission.INTERNET` is **completely omitted** from `AndroidManifest.xml`.
- Zero third-party analytics, telemetry, or network SDKs.
- Zero subscriptions or paid APIs.
- Media saved locally to `DCIM/Framed` via scoped MediaStore APIs.

---

## 🛠️ Building From Source

### Prerequisites
- JDK 17+ (e.g. Android Studio bundled JBR)
- Android SDK Platform 35 (`compileSdk = 35`, `minSdk = 34`)
- Gradle 8.9 (via included wrapper)

### Build Command
```bash
./gradlew assembleDebug
```
The output APK will be placed at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Open source under the [Apache License 2.0](LICENSE).
Inspired by the iOS app *Framed - Take Aesthetic Photos*.
