# Project Context

## Android Development Environment
- **Android SDK**: `C:\Users\Harve\AppData\Local\Android\Sdk`
- **Android Studio**: `C:\Program Files\Android\Android Studio`
- **Platform installed**: android-36 (Android 16)
- **Build Tools**: 36.1.0
- **Command-line tools**: Installed at `<SDK>/cmdline-tools/latest/`
- **Emulator AVD**: `Pixel_7_API_34` (API 34 / Android 14 / Google APIs / x86_64)
  - Launch: `C:\Users\Harve\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Pixel_7_API_34`
- **ANDROID_HOME** is NOT set as an environment variable — use the path directly
- **Physical test device**: Available (user's phone) — required for Bluetooth/Shizuku testing

## App Details
- **Package**: `net.harveywilliams.bluetoothbouncer`
- **Min SDK**: 31 (Android 12)
- **Target SDK**: 35
- **Spec location**: `openspec/changes/bluetooth-bouncer/`

## Build & Tooling
- **Gradle wrapper**: No system-wide Gradle install. The wrapper (`gradlew.bat` + `gradle-wrapper.jar`) is checked in; `gradle-wrapper.jar` was sourced from `C:\Data\Dev\random\duali-td\android\build\gradle\wrapper\`
- **`ANDROID_HOME` per shell**: Set `$env:ANDROID_HOME = "C:\Users\Harve\AppData\Local\Android\Sdk"` before every `gradlew.bat` invocation
- **AGP / compileSdk warning**: AGP 8.5.2 warns about compileSdk 35 — this is cosmetic; suppress with `android.suppressUnsupportedCompileSdk=35` in `gradle.properties` or upgrade AGP when available
- **Java**: Temurin JDK 17.0.17 is installed and on PATH

## Android API Gotchas
- **`BluetoothProfile.HID_HOST`**: Not in the compile-time SDK — use integer literal `4` directly
- **`setConnectionPolicy` reflection**: Hidden API; called via reflection in the Shizuku UserService — policy parameter must be `Int::class.java` (primitive)

## Key Technical Decisions
- Shizuku is required for `setConnectionPolicy(CONNECTION_POLICY_FORBIDDEN)` — no fallback mode
- `setConnectionPolicy` persists at the OS level, so no foreground service needed
- Emulator is for UI iteration only — no real Bluetooth hardware, Shizuku not installed on it
- No mock/preview data layer — develop against real dependencies
