## 1. Notification Builder Change

- [x] 1.1 Add `.setOnlyAlertOnce(true)` to the `NotificationCompat.Builder` in `WatchNotificationHelper.postNearbyNotification()`

## 2. Verification

- [x] 2.1 Build the app and confirm no compilation errors
- [x] 2.2 On a physical device with a watched blocked device, verify the first appearance triggers a full heads-up alert
- [x] 2.3 Confirm that repeated `onDeviceAppeared` callbacks (device oscillating) while the notification is visible produce no additional sound, vibration, or heads-up
- [x] 2.4 Dismiss the notification and confirm the next `onDeviceAppeared` fires a fresh full alert
