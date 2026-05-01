## 1. Database & Data Model

- [x] 1.1 Add `isAlertEnabled: Boolean` field to `BlockedDeviceEntity` (default false)
- [x] 1.2 Write Room migration 2→3: add `isAlertEnabled INTEGER NOT NULL DEFAULT 0` column
- [x] 1.3 Backfill migration: `UPDATE blocked_devices SET isAlertEnabled = 1 WHERE cdmAssociationId IS NOT NULL`
- [x] 1.4 Register migration 2→3 in the Room database builder
- [x] 1.5 Add `updateIsAlertEnabled(mac: String, enabled: Boolean)` query to `BlockedDeviceDao`
- [x] 1.6 Update `DeviceUiModel.isWatched` derivation to use `isAlertEnabled` instead of `cdmAssociationId != null`

## 2. Shizuku AIDL & UserService

- [x] 2.1 Add `connectDevice(macAddress: String): IntArray` to `IBluetoothBouncerUserService.aidl`
- [x] 2.2 Add `disconnectDevice(macAddress: String): IntArray` to `IBluetoothBouncerUserService.aidl`
- [x] 2.3 Implement `connectDevice` in `BluetoothBouncerUserService` via reflection on `BluetoothA2dp.connect`, `BluetoothHeadset.connect`, and HID Host `connect` (profile constant `4`)
- [x] 2.4 Implement `disconnectDevice` in `BluetoothBouncerUserService` via reflection on `BluetoothA2dp.disconnect`, `BluetoothHeadset.disconnect`, and HID Host `disconnect`
- [x] 2.5 Add `connectDevice` and `disconnectDevice` wrapper methods to `ShizukuHelper` following the existing `setConnectionPolicy` pattern (validate IntArray result, return `Result`)

## 3. CDM / Watch Manager

- [x] 3.1 Split `DeviceWatchManager.disableWatch` into two methods:
  - `disableWatch(mac, assocId)` — stops observation + sets `isAlertEnabled = false` only (Alert toggle OFF path)
  - `disableWatchAndDisassociate(mac, assocId)` — full cleanup: stops observation, calls `disassociate()`, clears `cdmAssociationId` (unblock path)
- [x] 3.2 Update `DeviceWatchManager.enableWatch` to also set `isAlertEnabled = true` via `updateIsAlertEnabled`
- [x] 3.3 Add a `ensureAssociated(mac, onPendingIntent, onSuccess, onFailure)` helper in `DeviceWatchManager` that skips `associate()` and calls `onSuccess` immediately if `cdmAssociationId` is already non-null

## 4. DeviceWatcherService

- [x] 4.1 Update `onDeviceAppeared` to gate notification posting on `isAlertEnabled == true` (in addition to `!isTemporarilyAllowed`)
- [x] 4.2 Verify `onDeviceDisappeared` revert logic is unchanged (it already keys on `isTemporarilyAllowed`, not `isAlertEnabled`)

## 5. ViewModel — Alert Toggle

- [x] 5.1 Update `toggleWatch` OFF path in `DeviceListViewModel` to call `disableWatch` (not `disableWatchAndDisassociate`) — stops observation, sets `isAlertEnabled = false`, retains CDM association
- [x] 5.2 Update `toggleWatch` ON path to use `ensureAssociated` — skip dialog if CDM association already exists

## 6. ViewModel — Connect & Disconnect

- [x] 6.1 Implement `connectDevice(device: DeviceUiModel)` in `DeviceListViewModel`:
  - If blocked: call `ensureAssociated` → on success, call `setConnectionPolicy(ALLOWED)` + `updateIsTemporarilyAllowed(true)` + `startObservingDevicePresence`
  - If allowed: call `ShizukuHelper.connectDevice(mac)` directly (no policy change)
- [x] 6.2 Implement `disconnectDevice(device: DeviceUiModel)` in `DeviceListViewModel`:
  - Always call `ShizukuHelper.disconnectDevice(mac)`
  - If blocked or temp-allowed: also call `setConnectionPolicy(FORBIDDEN)` + `updateIsTemporarilyAllowed(false)`
- [x] 6.3 Add `connectLoadingAddress: String?` and `disconnectLoadingAddress: String?` to UI state for in-flight button disabling
- [x] 6.4 Propagate snackbar errors from connect/disconnect failures

## 7. UI — Device Row

- [x] 7.1 Add `onConnect: () -> Unit` and `onDisconnect: () -> Unit` callback parameters to `DeviceRow`
- [x] 7.2 Implement Connect/Disconnect button visibility logic per spec:
  - Blocked + disconnected → Connect
  - Allowed + disconnected → Connect
  - Any + connected → Disconnect
  - Temp-allowed + disconnected → neither
- [x] 7.3 Render Connect/Disconnect as a `TextButton` with label "Connect" or "Disconnect", disabled when Shizuku not ready or loading
- [x] 7.4 Gate Connect/Disconnect buttons on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (API 33+)
- [x] 7.5 Wire `onConnect` and `onDisconnect` callbacks in `DeviceListScreen` to ViewModel

## 8. Unblock Cleanup

- [x] 8.1 Update `toggleBlock` OFF path in `DeviceListViewModel` to call `disableWatchAndDisassociate` (instead of the old `disableWatch`) when `cdmAssociationId` is non-null

## 9. Documentation

- [x] 9.1 Update README.md: document Connect/Disconnect button behaviour including the accepted auto-reconnect limitation for allowed devices
