# ⚡ ADHORA (অধরা)

> **High-Performance, Native Rust & Jetpack Compose Android Client for Telegram Media**

ADHORA is an ultra-fast, lightweight Android application engineered from the ground up for butter-smooth media browsing, real-time video streaming, and instant HD photo viewing from Telegram channels and groups.

---

## 🌟 Key Highlights

- **Native Rust Engine (`arm64-v8a`)**:
  - Direct MTProto 2.0 communication with Telegram data centers using Rust (`grammers-client`).
  - Integrated multithreaded HTTP stream server (`127.0.0.1:8080`) supporting HTTP Range requests (`206 Partial Content`).
  - **Zero Full-Download Waiting**: Video streams chunk-by-chunk on the fly directly into Android Media3 ExoPlayer.
- **Ultra-Lightweight & Fast**:
  - Full Release APK size is only **~9.9 MB** (optimized with R8 minification and resource shrinking).
  - Designed for smooth 60 / 120 FPS performance even on low-end hardware.
- **Cyber Aesthetic**:
  - **Pitch Black (`#000000`)** and **Electric Neon Lime (`#A6FF00`)** theme.
- **Unified Media Feed**:
  - Select multiple channels/groups (with dedicated tabs for All vs Admin/Owned channels).
  - Aggregates photos, videos, and media files into one responsive 3-column grid.
  - Infinite scroll pagination (loads in batches of 100+ items).
  - Quick filter chips: **All**, **Videos**, and **Photos**.
- **Immersive Fullscreen Media Viewer**:
  - **Single-Tap UI Toggle**: Single tap anywhere to instantly hide all toolbars, descriptions, and buttons for a 100% border-to-border, clean media experience. Tap again to restore.
  - **Gesture Navigation**: Butter-smooth horizontal swipe to navigate between photos and videos.
  - **Pinch-to-Zoom & Double-Tap**: Smooth zoom from 1x to 2.5x to 5x.
- **Smart Video Player**:
  - **Double-Tap 10s Seek**: Double tap left to rewind 10s (`◀◀ 10s`), double tap right to fast-forward 10s (`10s ▶▶`).
  - **Auto-Hide Controls**: Controls automatically fade out after 3.5 seconds during playback.
  - Custom Lime scrubber bar with live position, duration, and one-tap mute toggle.
- **Gallery Downloader**:
  - One-tap button to download and save original-resolution photos and videos directly to `Pictures/ADHORA/`.

---

## 🛠️ Architecture & Tech Stack

```
ADHORA Architecture
├── UI Layer (Kotlin + Jetpack Compose)
│   ├── Material3, Jetpack Navigation & Animations
│   ├── Coil (Asynchronous Image Loading)
│   ├── AndroidX Media3 / ExoPlayer (Video Playback)
│   └── JNI Bridge (`com.adhora.app.engine.NativeEngine`)
│
├── Native Core (Rust `rust_core/`)
│   ├── `tg.rs`: MTProto 2.0 Client & Session Manager (Grammers)
│   ├── `server.rs`: Concurrent HTTP Range Streaming Server
│   ├── `state.rs`: Global State & Cache Management
│   └── `lib.rs`: JNI Bindings for Android
│
└── Build System
    ├── Gradle 8.14 + Kotlin 2.1
    ├── Android NDK (arm64-v8a)
    └── Cargo NDK
```

---

## 🚀 Building from Source

### Prerequisites
1. **Android Studio** or Android SDK (API 34+, NDK installed)
2. **Rust & Cargo** (`rustup target add aarch64-linux-android`)
3. **Java 17+**

### 1. Build Native Rust Shared Library
```bash
cd rust_core
cargo ndk -t arm64-v8a build --release
# Copy the built library into Android jniLibs
cp target/aarch64-linux-android/release/libadhora_engine.so ../app/src/main/jniLibs/arm64-v8a/
```

### 2. Build Android APK
```bash
# In the root project directory:
./gradlew assembleRelease
```
The optimized release APK will be generated at:
`app/build/outputs/apk/release/app-release.apk` (and `ADHORA.apk`).

---

## 📱 Installation via ADB
Connect your Android device via USB and run:
```bash
adb install -r ADHORA.apk
adb shell am start -n com.adhora.app/.MainActivity
```

---

## 🔒 Security & Privacy
- **Direct Telegram MTProto**: Connects directly to official Telegram servers. No middleman or third-party servers.
- **Cloud 2FA Support**: Fully supports Telegram 2-Step Verification (Cloud Password).
- Sessions are stored securely on the local device.

---

## 📄 License
This project is licensed under the Apache 2.0 License.
