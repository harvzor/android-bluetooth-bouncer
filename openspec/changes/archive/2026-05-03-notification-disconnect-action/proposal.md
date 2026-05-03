## Why

After tapping "Allow temporarily" on a presence notification, the notification disappears and the device stays connected with no quick way to reverse the decision — the user must open the app, find the device, and disconnect from there. A persistent "Disconnect" action in the notification would allow one-tap reversal from the notification shade, without opening the app.

## What Changes

- When "Allow temporarily" is tapped successfully, the "nearby" notification is **replaced** (same notification ID) with a new "temporarily allowed" notification containing a "Disconnect" action button
- A new `DisconnectReceiver` handles the "Disconnect" action: force-disconnects all profiles, re-applies `POLICY_FORBIDDEN`, and clears the temp-allow flag — all without opening the app
- The "allowed" notification is **automatically dismissed** when the device leaves Bluetooth range (existing `onDeviceDisappeared` re-block path extended)
- The "allowed" notification is also **cancelled when the user disconnects from the app UI** (ViewModel disconnect path extended)
- The notification is swipe-dismissible (not ongoing) — user can remove it without disconnecting if desired

## Capabilities

### New Capabilities

- `temp-allow-disconnect-notification`: The "temporarily allowed" notification shown after tapping "Allow temporarily", its Disconnect action, and its dismissal lifecycle (disconnect action, device disappears, user disconnects from app UI, or swipe-dismiss)

### Modified Capabilities

- `device-watching`: The "Allow temporarily" notification action scenario changes — the notification is no longer dismissed after a successful allow, but replaced with the "temporarily allowed" notification. The auto-reblock-on-disappear scenario gains a notification cancellation step.

## Impact

- `WatchNotificationHelper` — new `postAllowedNotification()` method; new `ACTION_DISCONNECT` constant and extras
- `TemporaryAllowReceiver` — replace `cancel()` call with `postAllowedNotification()` on success
- `DisconnectReceiver` — new `BroadcastReceiver` in `receivers/`; declared in `AndroidManifest.xml`
- `DeviceWatcherService.onDeviceDisappeared()` — cancel "allowed" notification after successful re-block
- `DeviceListViewModel.disconnectDevice()` — cancel "allowed" notification after clearing temp-allow flag
- `AndroidManifest.xml` — new `<receiver>` entry for `DisconnectReceiver`
- No new permissions, no new notification channels, no DB schema changes
