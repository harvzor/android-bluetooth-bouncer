# Project Context

## Android Development Environment
- **Android SDK**: Install via Android Studio; locate your SDK path in *SDK Manager → Android SDK Location*
- **Command-line tools**: Installed at `<SDK>/cmdline-tools/latest/`
- **Emulator AVD**: An x86_64 AVD (API 34, Google APIs) is used for UI iteration — create one via AVD Manager if needed
- **Physical test device**: Required for Bluetooth/Shizuku testing — the emulator has no real Bluetooth hardware

## App Details
- **Package**: `net.harveywilliams.bluetoothbouncer`
- **Min SDK**: 31 (Android 12)
- **Target SDK**: 36
- **Spec location**: `openspec/changes/bluetooth-bouncer/`

## Documentation
- **README.md** must be kept in sync with the app's features. When adding, removing, or changing user-facing functionality, update `README.md` to reflect the current state of the app.

## Build & Tooling
- **Gradle wrapper**: No system-wide Gradle install. The wrapper (`gradlew.bat` + `gradle-wrapper.jar`) is checked in; regenerate with `gradlew.bat wrapper --gradle-version <version>` if needed
- **`ANDROID_HOME` per shell**: Must be set before every `gradlew.bat` invocation. Locate your SDK path in Android Studio → *SDK Manager → Android SDK Location*. Example: `$env:ANDROID_HOME = "C:\Users\<you>\AppData\Local\Android\Sdk"`
- **Java**: JDK 25 (Temurin) is required. Version is pinned in `.java-version` at the repo root.
- **Build command**: `$env:ANDROID_HOME = "<sdk-path>"; .\gradlew.bat assembleDebug` — use a 60000 ms tool timeout; when the output contains `BUILD SUCCESSFUL`, the build is done — proceed immediately without waiting for anything further
- **APK output**: `app\build\outputs\apk\debug\app-debug.apk`
- **Install to device**: `& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk`

## Android API Gotchas
- **`BluetoothProfile.HID_HOST`**: Not in the compile-time SDK — use integer literal `4` directly
- **`setConnectionPolicy` reflection**: Hidden API; called via reflection in the Shizuku UserService — policy parameter must be `Int::class.java` (primitive)
- **Shizuku UserService has no working Bluetooth system service**: `context.getSystemService(BLUETOOTH_SERVICE)` and `BluetoothAdapter.getDefaultAdapter()` both return `null` in Shizuku's `app_process` environment. The adapter must be obtained via `ServiceManager.getService("bluetooth_manager")` reflection, then constructing `BluetoothAdapter` via its hidden `(IBluetoothManager)` constructor. Additionally, `BluetoothAdapter.sAdapter` must be set to the constructed adapter immediately after — `BluetoothProfileConnector` initialises its `mBluetoothAdapter` field via `getDefaultAdapter()` at field-declaration time (before its constructor body runs), so without `sAdapter` populated, `getProfileProxy()` will NPE.
- **`ActivityThread.currentApplication()` returns a Context in Shizuku but it lacks system services**: The Context object is available but its service registry is not wired up. Do not rely on it for `getSystemService()` calls — use `ServiceManager` directly to obtain system binders.

## Key Technical Decisions
- Shizuku is required for `setConnectionPolicy(CONNECTION_POLICY_FORBIDDEN)` — no fallback mode
- `setConnectionPolicy` persists at the OS level, so no foreground service needed
- Emulator is for UI iteration only — no real Bluetooth hardware, Shizuku not installed on it
- No mock/preview data layer — develop against real dependencies
- `ShizukuHelper.setConnectionPolicy()` must validate the returned `IntArray` — if no profile entry equals `1` (success), treat the call as `Result.failure` so the UI surfaces the error rather than silently lying to the user
