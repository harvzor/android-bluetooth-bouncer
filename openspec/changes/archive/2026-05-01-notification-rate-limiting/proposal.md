## Why

When a blocked Bluetooth device oscillates at the edge of range, the CompanionDeviceManager fires `onDeviceAppeared` repeatedly every few seconds, causing a new heads-up notification alert (with sound and vibration) on each callback. The user gets spammed with identical alerts for a device they already know about.

## What Changes

- `WatchNotificationHelper.postNearbyNotification()` gains `setOnlyAlertOnce(true)` on the notification builder so that re-posts of an already-visible notification are silent
- The notification is posted with the existing deterministic ID (`macAddress.hashCode()`), ensuring re-posts replace rather than stack

## Capabilities

### New Capabilities

- `notification-rate-limiting`: Ensures a watched-device alert only makes noise once per appearance event; subsequent re-posts while the notification is still visible are silent

### Modified Capabilities

- `device-watching`: The "alert fires when device appears" behaviour is refined — the user is alerted at most once per continuous appearance; oscillation at the edge of range produces one alert, not many

## Impact

- `notification/WatchNotificationHelper.kt`: one-line change to `postNearbyNotification()`
- No database changes, no new dependencies, no API changes
- No changes to `DeviceWatcherService` — the posting logic stays identical; only the notification flags change
