## Why

After tapping "Disconnect" (either from the notification or the app UI), the notification disappears entirely — but the device is still physically nearby. The CDM won't fire `onDeviceAppeared` again because the device never left range, so the "Nearby" notification never returns unless the device physically goes away and comes back. The user is left with no quick way to re-allow the device from the notification shade.

## What Changes

- When the user disconnects a temporarily allowed device (via the notification "Disconnect" action or the in-app Disconnect button), the "temporarily allowed" notification is **replaced in-place** with the "Nearby — tap to allow for this session" notification instead of being cancelled
- When a watched blocked device leaves Bluetooth range, `onDeviceDisappeared` now **cancels any notification** for that device (nearby or otherwise) even when `isTemporarilyAllowed` is false — this prevents the "nearby" notification from orphaning in the shade if the device departs shortly after a manual disconnect

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `temp-allow-disconnect-notification`: The "Disconnect" action and in-app disconnect now restore the "nearby" notification rather than dismissing the notification entirely
- `device-watching`: The `onDeviceDisappeared` handler now cancels any active notification for alert-enabled devices regardless of `isTemporarilyAllowed` state, preventing orphaned notifications

## Impact

- `WatchNotificationHelper` — add `postNearbyNotification(context, macAddress, deviceName)` overload (macAddress + deviceName only, no entity required) so callers without a `BlockedDeviceEntity` can restore the nearby notification
- `DisconnectReceiver` — replace `NotificationManagerCompat.cancel(notifId)` on success with `WatchNotificationHelper.postNearbyNotification(...)`
- `DeviceListViewModel.disconnectDevice()` — replace notification cancel with `WatchNotificationHelper.postNearbyNotification(...)`
- `DeviceWatcherService.onDeviceDisappeared()` — cancel the notification for all alert-enabled devices on disappear, not just temp-allowed ones
- No DB schema changes, no new permissions, no new notification channels
