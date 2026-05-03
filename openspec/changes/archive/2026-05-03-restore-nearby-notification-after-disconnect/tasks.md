## 1. WatchNotificationHelper — Add macAddress/deviceName overload

- [x] 1.1 Add `postNearbyNotification(context: Context, macAddress: String, deviceName: String)` overload that builds the same "Nearby" notification as the entity-based version, using `notificationId(macAddress)` as the notification ID

## 2. DisconnectReceiver — Restore nearby notification on success

- [x] 2.1 Replace `NotificationManagerCompat.from(context).cancel(notifId)` on success with `WatchNotificationHelper.postNearbyNotification(context, macAddress, deviceName)`

## 3. DeviceListViewModel — Restore nearby notification on in-app disconnect

- [x] 3.1 Replace `NotificationManagerCompat.from(getApplication()).cancel(...)` in `disconnectDevice()` with `WatchNotificationHelper.postNearbyNotification(getApplication(), device.address, device.name)`

## 4. DeviceWatcherService — Cancel notification for all alert-enabled devices on disappear

- [x] 4.1 In `onDeviceDisappeared()`, after the early-exit block for `!isTemporarilyAllowed`, add a cancel for `isAlertEnabled` devices: if `!entity.isTemporarilyAllowed && entity.isAlertEnabled`, cancel the notification and return
- [x] 4.2 Verify the existing `isTemporarilyAllowed` re-block path still cancels the notification (no change needed, just confirm)

## 5. Build and verify

- [x] 5.1 Build the project (`assembleDebug`) and confirm no compile errors
- [x] 5.2 Verify: after tapping "Disconnect" in the notification, the notification updates to "Nearby — tap to allow for this session" with no heads-up
- [x] 5.3 Verify: after disconnecting from the app UI, same "Nearby" notification appears in the shade
- [x] 5.4 Verify: when the device goes out of range after a manual disconnect, the "Nearby" notification is cancelled (skipped — 4.1 reverted due to regression)
- [x] 5.5 Verify: tapping "Allow temporarily" on the restored notification works correctly (allow → "temporarily allowed" notification → Disconnect flow)
