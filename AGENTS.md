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

## Key Technical Decisions
- Shizuku is required for `setConnectionPolicy(CONNECTION_POLICY_FORBIDDEN)` — no fallback mode
- `setConnectionPolicy` persists at the OS level, so no foreground service needed
- Emulator is for UI iteration only — no real Bluetooth hardware, Shizuku not installed on it
- No mock/preview data layer — develop against real dependencies
