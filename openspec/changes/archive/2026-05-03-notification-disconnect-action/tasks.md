## 1. WatchNotificationHelper — Add "allowed" notification and Disconnect wiring

- [x] 1.1 Add `ACTION_DISCONNECT` constant to `WatchNotificationHelper`
- [x] 1.2 Add `postAllowedNotification(context, macAddress, deviceName)` method: same `notifId` as nearby notification, title = device name, text = "Temporarily allowed", one "Disconnect" action button firing `DisconnectReceiver`, `setOnlyAlertOnce(true)`, `PRIORITY_DEFAULT`, `setAutoCancel(false)`

## 2. DisconnectReceiver — New broadcast receiver

- [x] 2.1 Create `app/src/main/java/net/harveywilliams/bluetoothbouncer/receivers/DisconnectReceiver.kt` handling `ACTION_DISCONNECT` with `goAsync()`
- [x] 2.2 Implement disconnect logic: `shizukuHelper.disconnectDevice(mac)`, then `setConnectionPolicy(mac, POLICY_FORBIDDEN)`, then `blockedDeviceDao.updateIsTemporarilyAllowed(mac, false)`, then `NotificationManagerCompat.cancel(notifId)`
- [x] 2.3 On failure: call `WatchNotificationHelper.postErrorNotification()` with appropriate message
- [x] 2.4 Declare `DisconnectReceiver` in `AndroidManifest.xml` with `android:exported="false"`

## 3. TemporaryAllowReceiver — Replace cancel with allowed notification

- [x] 3.1 Replace `NotificationManagerCompat.from(context).cancel(notifId)` on success with `WatchNotificationHelper.postAllowedNotification(context, macAddress, deviceName)`

## 4. DeviceWatcherService — Cancel notification on auto-reblock

- [x] 4.1 After the successful `setConnectionPolicy(POLICY_FORBIDDEN)` + `updateIsTemporarilyAllowed(false)` block in `onDeviceDisappeared`, add `NotificationManagerCompat.from(this).cancel(WatchNotificationHelper.notificationId(entity.macAddress))`

## 5. DeviceListViewModel — Cancel notification on in-app disconnect

- [x] 5.1 After `blockedDeviceDao.updateIsTemporarilyAllowed(device.address, false)` in `disconnectDevice()`, add `NotificationManagerCompat.from(getApplication()).cancel(WatchNotificationHelper.notificationId(device.address))`

## 6. Build and verify

- [x] 6.1 Build the project (`assembleDebug`) and confirm no compile errors
- [x] 6.2 Install on physical device and verify: "Allow temporarily" replaces the notification with a "Disconnect" button
- [x] 6.3 Verify tapping "Disconnect" force-disconnects the device, re-blocks it, and clears the notification
- [x] 6.4 Verify the notification auto-dismisses when the device goes out of range
- [x] 6.5 Verify the notification is cancelled when disconnecting from the app UI
