## 1. Database Migration

- [x] 1.1 Add `cdmAssociationId: Int?` (nullable, default null) and `isTemporarilyAllowed: Boolean` (default false) fields to `BlockedDeviceEntity`
- [x] 1.2 Write Room migration from version 1 to version 2 adding both columns with appropriate defaults
- [x] 1.3 Update `AppDatabase` version to 2 and register the migration
- [x] 1.4 Update `BlockedDeviceDao` with queries for the new fields: update `cdmAssociationId`, update `isTemporarilyAllowed`, query all watched devices (non-null `cdmAssociationId`)

## 2. CompanionDeviceService

- [x] 2.1 Create `DeviceWatcherService` extending `CompanionDeviceService` (API 31+)
- [x] 2.2 Implement `onDeviceAppeared(associationInfo: AssociationInfo)`: query Room for the device by `cdmAssociationId`; if blocked and not temporarily allowed, post the "nearby" notification
- [x] 2.3 Implement `onDeviceDisappeared(associationInfo: AssociationInfo)`: query Room for the device; if `isTemporarilyAllowed` is true and device is not profile-connected, call `setConnectionPolicy(FORBIDDEN)` via Shizuku and clear `isTemporarilyAllowed`
- [x] 2.4 Add `<service>` declaration for `DeviceWatcherService` in `AndroidManifest.xml` with `android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE"`

## 3. Notification Infrastructure

- [x] 3.1 Create notification channel `"device_watch"` (importance HIGH) in `BluetoothBouncerApp.onCreate()`
- [x] 3.2 Build the "nearby" notification helper: shows device name, body "Tap to allow for this session", with an "Allow temporarily" action `PendingIntent`
- [x] 3.3 Create `TemporaryAllowReceiver` (`BroadcastReceiver`) to handle the "Allow temporarily" action: call `setConnectionPolicy(ALLOWED)` via Shizuku, set `isTemporarilyAllowed = true` in Room, dismiss the notification; on Shizuku failure post an error notification
- [x] 3.4 Register `TemporaryAllowReceiver` in `AndroidManifest.xml`

## 4. Watch Lifecycle — Enabling

- [x] 4.1 Create `DeviceWatchManager` (or extend `ShizukuHelper`/ViewModel) responsible for `associate()`, `startObservingDevicePresence()`, `stopObservingDevicePresence()`, and `disassociate()` calls
- [x] 4.2 Implement `associate()` flow using `ActivityResultLauncher` for `CompanionDeviceManager.associate()` with a `BluetoothDeviceFilter` matched to the device MAC address
- [x] 4.3 On successful association, call `startObservingDevicePresence(associationId)` and persist `cdmAssociationId` to Room
- [x] 4.4 Handle association failure / cancellation: show Snackbar "Device not found nearby. Try again when it's in Bluetooth range." and leave Watch toggle off

## 5. Watch Lifecycle — Disabling & Cleanup

- [x] 5.1 Implement disable-watch flow: call `stopObservingDevicePresence(associationId)`, call `disassociate(associationId)`, set `cdmAssociationId = null` in Room
- [x] 5.2 Update the unblock flow in `DeviceListViewModel.toggleBlock()`: if `cdmAssociationId` is non-null, run the disable-watch flow before deleting the `BlockedDeviceEntity`

## 6. UI — Watch Toggle

- [x] 6.1 Add `isWatched: Boolean` field to `DeviceUiModel` (derived from `cdmAssociationId != null`)
- [x] 6.2 Add Watch toggle to the device list row in `DeviceListScreen`: visible only for blocked devices on API 33+, wired to a new `onToggleWatch(device)` callback
- [x] 6.3 Expose `toggleWatch(device: DeviceUiModel)` in `DeviceListViewModel`, calling `DeviceWatchManager` enable/disable as appropriate
- [x] 6.4 Ensure Watch toggle is disabled (greyed out) while an association request is in progress (loading state)

## 7. Follow-up Spec: Shizuku-Not-Running Error Handling
- [ ] 7.1 Create an OpenSpec proposal for handling Shizuku unavailability when the "Allow temporarily" notification action fires (currently surfaces a basic error notification only)

## 8. Follow-up Spec: Notification Rate-Limiting
- [ ] 8.1 Create an OpenSpec proposal for rate-limiting or suppressing repeat notifications when a watched device repeatedly enters and leaves range
