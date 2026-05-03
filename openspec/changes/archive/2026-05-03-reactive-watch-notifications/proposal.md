## Why

The notification for a blocked device does not update when the user connects via the in-app UI: the "Nearby — tap to allow" notification stays visible even after the device is connected and temporarily allowed. This is because notification updates are applied imperatively at each call site, and the UI-initiated connect path (`DeviceListViewModel.connectDevice`) was never wired up. Rather than adding another imperative call, the notification system should be made reactive so it automatically reflects current device state regardless of how that state was changed.

## What Changes

- Add a `nearbyDevices` state holder (in-memory `MutableStateFlow<Set<String>>` on the Application class) populated by CDM presence callbacks
- Add a `NotificationStateManager` (or equivalent Application-scoped observer) that combines `nearbyDevices` with the Room devices `Flow` and derives the correct notification state for each device
- Remove manual `WatchNotificationHelper` calls from `TemporaryAllowReceiver`, `DisconnectReceiver`, and `DeviceListViewModel.disconnectDevice` — state writes to Room are now sufficient to drive notification updates
- `DeviceWatcherService.onDeviceAppeared` and `onDeviceDisappeared` write to `nearbyDevices` instead of directly posting/cancelling notifications (re-block logic stays in `onDeviceDisappeared`)

## Capabilities

### New Capabilities

- `reactive-notification-state`: An Application-scoped observer that derives and posts the correct per-device notification by combining in-memory "nearby" presence state with Room's `isTemporarilyAllowed` field. Replaces the current imperative, call-site-driven notification updates.

### Modified Capabilities

- `temp-allow-disconnect-notification`: Add requirement covering the UI-initiated connect path — when the user connects a blocked device from the app UI, the notification SHALL transition to "Temporarily allowed" with a "Disconnect" action, matching the behaviour already specified for the notification-action path.

## Impact

- `BluetoothBouncerApp`: new `nearbyDevices: MutableStateFlow<Set<String>>` field; new Application-scoped coroutine collecting combined notification state
- `DeviceWatcherService`: simplified — writes to `nearbyDevices` set instead of calling `WatchNotificationHelper` directly
- `TemporaryAllowReceiver`: remove `postAllowedNotification()` call
- `DisconnectReceiver`: remove `postNearbyNotification()` call
- `DeviceListViewModel.disconnectDevice`: remove `postNearbyNotification()` call
- `WatchNotificationHelper`: no structural changes; still the single place that builds and posts notifications
- No Room schema changes; no new dependencies
