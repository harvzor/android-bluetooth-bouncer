## Why

Blocked devices are permanently forbidden from connecting, but there is no way to grant a temporary exception without fully unblocking the device in the app UI. Users need a frictionless way to allow a specific blocked device to connect for a single session — without opening the app, and without permanently changing its blocked status.

## What Changes

- Add a per-device "Watch" toggle that appears on blocked devices (API 33+ only)
- Integrate CompanionDeviceManager to observe Bluetooth presence for watched devices in the background, even when the app is not running
- Post a notification when a watched blocked device comes into range, with an "Allow temporarily" action
- On temporary allow: call `setConnectionPolicy(ALLOWED)` via Shizuku and mark the device as temporarily allowed in the database
- On device departure (`onDeviceDisappeared`): automatically re-block via `setConnectionPolicy(FORBIDDEN)` and clear the temporary-allow flag
- Auto-disassociate from CompanionDeviceManager when a device is fully unblocked

## Capabilities

### New Capabilities

- `device-watching`: Per-device opt-in to background presence monitoring via CompanionDeviceManager; fires a notification with a temporary-allow action when a watched blocked device appears nearby

### Modified Capabilities

- `device-blocking`: The blocked device entity gains two new fields (`cdmAssociationId`, `isTemporarilyAllowed`) and a new transient state (temporarily allowed); unblocking a device must now also clean up any active CDM association

## Impact

- **Room database**: Schema migration v1 → v2 adding `cdmAssociationId: Int?` and `isTemporarilyAllowed: Boolean` to `blocked_devices`
- **New component**: `CompanionDeviceService` subclass to receive `onDeviceAppeared` / `onDeviceDisappeared` callbacks
- **New component**: `BroadcastReceiver` (or `Service`) to handle the "Allow temporarily" notification action
- **Manifest**: `<service>` declaration for `CompanionDeviceService`; `BLUETOOTH_ADVERTISE` permission not required but `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` may be needed
- **UI**: Watch toggle added to device list rows (blocked devices only, hidden on API < 33)
- **Shizuku**: No new Shizuku capabilities needed; existing `setConnectionPolicy` called from new entry points
- **Dependencies**: No new third-party dependencies; `CompanionDeviceManager` is part of the Android framework
- **Follow-up specs required**: Shizuku-not-running error handling for notification actions; notification rate-limiting for repeat device appearances
