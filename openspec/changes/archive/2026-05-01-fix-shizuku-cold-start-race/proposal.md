## Why

When the app process is dead and a blocked Bluetooth device comes into range, the OS wakes the app via `CompanionDeviceService`. If the user taps "Allow temporarily" on the resulting notification, the tap arrives while the Shizuku UserService is still binding asynchronously — causing an immediate "Could not allow — Shizuku unavailable" error. A second tap succeeds. The root cause is that `setConnectionPolicy` treats a null `userService` as a hard failure without waiting for the in-progress bind to complete.

## What Changes

- `ShizukuHelper.setConnectionPolicy()` will suspend and wait for `State.Ready` (up to 10 seconds) when the UserService is null but Shizuku is installed and running, before returning failure.
- `BluetoothBouncerApp` will eagerly initialize `shizukuHelper` during `onCreate()` so binding starts at process launch rather than on the first caller.
- The error notification text will be updated from "Could not allow — Shizuku is not running" to "Could not allow — Shizuku unavailable" (the old message was misleading since Shizuku is running; the service just hadn't finished binding yet).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `shizuku-bridge`: The requirement for how `setConnectionPolicy` behaves when the UserService is still binding needs to change — callers must no longer receive an immediate failure during the binding window. Currently the spec only covers the already-connected and already-disconnected scenarios; a new scenario for "binding in progress" is required.

## Impact

- `ShizukuHelper.kt`: core logic change in `setConnectionPolicy()`
- `BluetoothBouncerApp.kt`: eager init of `shizukuHelper` in `onCreate()`
- `WatchNotificationHelper.kt`: error notification text string
- All existing callers of `setConnectionPolicy()` benefit automatically with no changes needed
