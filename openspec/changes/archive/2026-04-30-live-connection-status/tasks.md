## 1. Fix existing refresh infrastructure

- [x] 1.1 Fix `refreshDevices()` coroutine leak — replace the unbounded `collect` with a one-shot read of `blockedDeviceDao.getAllDevices()` (e.g., add a `suspend fun getAll(): List<BlockedDeviceEntity>` to the DAO, or use `first()` on the existing Flow), then call `refreshDeviceList()`
- [x] 1.2 Verify the `init` block's `combine` collector remains the sole long-lived collector on the Room Flow

## 2. Add refresh event bus

- [x] 2.1 Add a `MutableSharedFlow<Unit>` (replay = 0, extraBufferCapacity = 1, `DROP_OLDEST`) to `BluetoothBouncerApp` as the app-level refresh signal
- [x] 2.2 In `DeviceListViewModel.init`, collect from the shared flow with `debounce(300)` and call `refreshDeviceList()` on each emission (reading current blocked devices via one-shot DAO query)

## 3. Register Bluetooth event receiver in ViewModel

- [x] 3.1 Create a `BroadcastReceiver` inside `DeviceListViewModel` that handles `ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`, and `BluetoothAdapter.ACTION_STATE_CHANGED` by emitting into the app-level refresh `SharedFlow`
- [x] 3.2 Register the receiver in `onBluetoothPermissionResult(granted = true)` with the appropriate `IntentFilter`
- [x] 3.3 Unregister the receiver in `onCleared()`
- [x] 3.4 Guard against double-registration if `onBluetoothPermissionResult` is called more than once

## 4. Wire BondStateReceiver to refresh bus

- [x] 4.1 In `BondStateReceiver.onReceive()`, after re-applying policy (or finding device not in blocked list), emit into `BluetoothBouncerApp`'s refresh `SharedFlow` so the ViewModel picks up the bond change

## 5. Verification

- [x] 5.1 Build the app and verify no compilation errors
- [x] 5.2 On physical device: pair a Bluetooth device, open the app, connect/disconnect the device — verify "Connected" indicator updates within ~1 second without restarting the app
- [x] 5.3 On physical device: toggle Bluetooth off/on while the app is open — verify the device list clears and repopulates
- [x] 5.4 Verify that opening the app, navigating away, and returning does not crash (receiver lifecycle)
