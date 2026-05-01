## 1. Eager Shizuku Initialization

- [x] 1.1 In `BluetoothBouncerApp.onCreate()`, access `shizukuHelper` to trigger its `by lazy` initialization, starting the Shizuku bind at app startup

## 2. Await-Binding Logic in ShizukuHelper

- [x] 2.1 Add a `BIND_TIMEOUT_MS` constant (10 000 ms) to `ShizukuHelper.Companion`
- [x] 2.2 At the top of `setConnectionPolicy()`, if `userService` is null and `isShizukuRunning()` returns true and `hasPermission()` returns true, use `withTimeout(BIND_TIMEOUT_MS)` + `_state.first { it is State.Ready }` to await binding completion
- [x] 2.3 After the await, re-read `userService` and proceed with the existing call; if it is still null (shouldn't happen) fall through to the existing null-check failure

## 3. Error Notification Text

- [x] 3.1 In `WatchNotificationHelper.postErrorNotification()`, update the notification body text from `"Could not allow — Shizuku is not running"` to `"Could not allow — Shizuku unavailable"`

## 4. Verification

- [x] 4.1 Build the project (`gradlew assembleDebug`) and confirm no compilation errors
- [x] 4.2 On a physical device with Shizuku running: force-stop the app, bring a blocked device into range, tap "Allow temporarily" on the first notification — confirm it succeeds without an error notification
- [x] 4.3 On a physical device with Shizuku stopped: repeat the flow — confirm the error notification appears with the updated text "Could not allow — Shizuku unavailable"
