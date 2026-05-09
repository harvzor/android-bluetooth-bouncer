## Why

The codebase has grown to ~2,700 lines across 19 Kotlin files as features were added incrementally. Several patterns are repeated verbatim across files — most notably in the Shizuku layer where three AIDL operations each duplicate ~40 lines of proxy-dispatch boilerplate. Other duplications include a hidden-API reflection hack copied in two places, section-classification logic repeated twice in the same ViewModel, receiver lifecycle boilerplate in all four `BroadcastReceiver` classes, and raw color literals spread across two UI screens. Cleaning this up now, before the codebase grows further, reduces the cost of future changes.

## What Changes

- **Extract `forEachProfile()` helper** in `BluetoothBouncerUserService`: the three AIDL methods (`setConnectionPolicy`, `connectDevice`, `disconnectDevice`) each contain identical proxy-dispatch loops differing only in the per-proxy call. A single `forEachProfile(macAddress, action)` helper eliminates ~90 lines of copy-paste.
- **Extract `callService()` helper** in `ShizukuHelper`: the same three public suspend functions share identical `awaitServiceIfNeeded` + `userService ?: return failure` + results-check scaffolding. A private `callService(operationName, call)` helper collapses them to one-liners.
- **Create `BluetoothAclHelper` utility object**: the hidden `BluetoothDevice.isConnected()` reflection hack appears in both `DeviceListViewModel` and `DeviceWatcherService`. A shared `util/BluetoothAclHelper.kt` centralises the reflection call.
- **Extract `computeSection()` in `DeviceListViewModel`**: the `when` expression that maps device state to `DeviceSection` is copy-pasted in both `refreshDeviceList()` and the decay ticker's remap lambda. A local private function deduplicates it.
- **Create `launchAsync` extension on `BroadcastReceiver`**: all four receivers (`BootReceiver`, `BondStateReceiver`, `TemporaryAllowReceiver`, `DisconnectReceiver`) repeat the same `goAsync()` + `CoroutineScope(Dispatchers.IO).launch` + `try/finally pendingResult.finish()` skeleton. A single extension function in `receivers/ReceiverExtensions.kt` removes the boilerplate.
- **Create `AppColors` semantic constants**: three raw `Color(0x...)` literals with semantic meaning (Shizuku ready green, device connected blue, device detected orange) are repeated across `DeviceListScreen` and `ShizukuSetupScreen`. A new `ui/theme/AppColors.kt` object names them.
- **Extract `NearbyDeviceTracker`**: the `BluetoothBouncerApp` owns `nearbyDevices`, `pendingRemovals`, `scheduleNearbyRemoval()`, and `cancelPendingRemoval()` — state and logic unrelated to the Application lifecycle itself. Extracting to `service/NearbyDeviceTracker.kt` makes the responsibility boundary explicit.

No user-facing behaviour changes. No API changes. No dependency additions.

## Capabilities

### New Capabilities

None — this is a pure internal refactoring. No new user-facing behaviour is introduced.

### Modified Capabilities

None — all existing specs remain valid. No requirement-level behaviour changes.

## Impact

- **Modified files** (7): `BluetoothBouncerUserService.kt`, `ShizukuHelper.kt`, `DeviceListViewModel.kt`, `DeviceWatcherService.kt`, `BluetoothBouncerApp.kt`, `DeviceListScreen.kt`, `ShizukuSetupScreen.kt`, plus all 4 receivers.
- **New files** (4): `ui/theme/AppColors.kt`, `util/BluetoothAclHelper.kt`, `receivers/ReceiverExtensions.kt`, `service/NearbyDeviceTracker.kt`.
- **No new dependencies**.
- **Net line reduction**: estimated ~350 lines removed (from ~2,700 to ~2,350).
