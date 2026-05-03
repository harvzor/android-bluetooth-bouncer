## 1. Add Nearby Presence State to Application

- [x] 1.1 Add `nearbyDevices: MutableStateFlow<Set<String>>` property to `BluetoothBouncerApp`
- [x] 1.2 Add `applicationScope: CoroutineScope` (SupervisorJob + Dispatchers.Default) to `BluetoothBouncerApp` for the observer lifetime

## 2. Implement Reactive Notification Observer

- [x] 2.1 In `BluetoothBouncerApp.onCreate`, launch a coroutine that collects `combine(nearbyDevices, database.blockedDeviceDao().getAllDevices())`
- [x] 2.2 For each device in the combined emission: post "Allowed" notification if MAC is nearby and `isTemporarilyAllowed`, post "Nearby" notification if MAC is nearby and `isAlertEnabled` and not `isTemporarilyAllowed`, or cancel the notification if MAC is not nearby or neither condition applies

## 3. Update DeviceWatcherService

- [x] 3.1 In `onDeviceAppeared`: replace `WatchNotificationHelper.postNearbyNotification()` call with `app.nearbyDevices.update { it + mac }`
- [x] 3.2 In `onDeviceDisappeared`: replace `NotificationManagerCompat.cancel()` call with `app.nearbyDevices.update { it - mac }` (keep re-block logic and Room write in place)

## 4. Remove Manual Notification Calls from Call Sites

- [x] 4.1 `TemporaryAllowReceiver`: remove `WatchNotificationHelper.postAllowedNotification()` call (Room write already triggers observer)
- [x] 4.2 `TemporaryAllowReceiver`: remove `WatchNotificationHelper.postErrorNotification()` call on policy failure — keep it: error notification is not reactively managed (see design)
- [x] 4.3 `DisconnectReceiver`: remove `WatchNotificationHelper.postNearbyNotification()` call on success (Room write triggers observer)
- [x] 4.4 `DeviceListViewModel.disconnectDevice`: remove `WatchNotificationHelper.postNearbyNotification()` call (lines 490–494)

## 5. Verify and Test on Device

- [x] 5.1 Build debug APK and install on device
- [x] 5.2 Verify: connect a blocked device from the app UI → notification transitions from "Nearby" to "Temporarily connected"
- [x] 5.3 Verify: disconnect a temporarily allowed device from the app UI → notification transitions from "Temporarily connected" to "Nearby"
- [x] 5.4 Verify: tap "Allow temporarily" in notification → notification transitions to "Temporarily connected" (regression)
- [x] 5.5 Verify: tap "Disconnect" in notification → notification transitions back to "Nearby" (regression)
- [x] 5.6 Verify: device leaves range while temporarily allowed → notification is cancelled (regression)
