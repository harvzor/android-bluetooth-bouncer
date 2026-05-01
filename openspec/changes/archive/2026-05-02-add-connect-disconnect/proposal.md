## Why

The app currently requires multiple steps to temporarily connect a blocked device (enable Alert, wait for a notification, tap "Allow temporarily") and provides no way to disconnect a temporarily-connected device without going into Android Settings. Adding Connect and Disconnect buttons gives users a direct, one-tap path for both actions.

## What Changes

- A contextual **Connect** or **Disconnect** text button appears on each device row, to the right of the existing toggle switches (API 33+ only)
- **Connect** on a blocked + disconnected device: sets `CONNECTION_POLICY_ALLOWED`, marks as temporarily allowed, and registers a CDM association (with system confirmation dialog on first use) so the revert-to-blocked behaviour survives force-close
- **Connect** on an allowed + disconnected device: actively initiates a connection without changing policy
- **Disconnect** on a connected device: actively disconnects the device; for blocked/temp-allowed devices also re-applies `CONNECTION_POLICY_FORBIDDEN`; for allowed devices, policy is unchanged (Android may auto-reconnect — accepted behaviour)
- `BlockedDeviceEntity` gains a new `isAlertEnabled: Boolean` field, decoupling Alert notification preference from CDM association existence
- CDM associations are now created on **first Connect** (as well as on Alert enable) and persist until the device is unblocked — Alert toggle OFF stops notifications but no longer destroys the CDM association
- `DeviceWatcherService.onDeviceAppeared` gates notifications on `isAlertEnabled`, not on `cdmAssociationId != null`
- The Shizuku UserService gains two new AIDL methods: `connectDevice` and `disconnectDevice`

## Capabilities

### New Capabilities

- `device-connect-disconnect`: Direct connect/disconnect actions for paired Bluetooth devices, including temporary-allow semantics for blocked devices and active profile connect/disconnect via Shizuku

### Modified Capabilities

- `device-watching`: Alert toggle OFF no longer destroys the CDM association; `onDeviceAppeared` guards on `isAlertEnabled` rather than `cdmAssociationId` presence; CDM association lifecycle extended to cover Connect button use
- `device-blocking`: `BlockedDeviceEntity` gains `isAlertEnabled: Boolean` (Room migration 2→3); unblock cleanup path unchanged in behaviour but now clears `isAlertEnabled` alongside CDM disassociation
- `shizuku-bridge`: UserService AIDL interface gains `connectDevice(macAddress: String): IntArray` and `disconnectDevice(macAddress: String): IntArray` methods, callable from the app process

## Impact

- **`BlockedDeviceEntity`** — new column `isAlertEnabled INTEGER NOT NULL DEFAULT 0`; Room migration 2→3 required; backfill: set `isAlertEnabled = 1` for all rows where `cdmAssociationId IS NOT NULL`
- **`IBluetoothBouncerUserService.aidl`** — two new method declarations
- **`BluetoothBouncerUserService`** — implementations of `connectDevice` / `disconnectDevice` via reflection on hidden profile APIs (same pattern as `setConnectionPolicy`)
- **`DeviceWatchManager`** — `disableWatch` no longer calls `disassociate`; new `disableWatchAndDisassociate` used only by the unblock path
- **`DeviceWatcherService`** — `onDeviceAppeared` condition changes from `!isTemporarilyAllowed` to `!isTemporarilyAllowed && isAlertEnabled`
- **`DeviceListViewModel`** — new `connect()` and `disconnect()` functions; `toggleWatch` OFF path updated
- **`DeviceListScreen`** — `DeviceRow` gains a conditional Connect/Disconnect `TextButton`
- **`BlockedDeviceDao`** — new `updateIsAlertEnabled` query
- **`DeviceUiModel`** — no new fields needed; `isWatched` derived from `isAlertEnabled` (not `cdmAssociationId`)
- **Android API level** — Connect/Disconnect buttons and CDM-backed temp-allow are API 33+ only
