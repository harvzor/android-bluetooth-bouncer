## Context

The app already has a `TemporaryAllowReceiver` that handles the "Allow temporarily" notification action. On success it calls `NotificationManagerCompat.cancel()`, making the notification disappear. A `WatchNotificationHelper` object owns all notification construction. `DeviceWatcherService` (a `CompanionDeviceService`) drives presence detection and already contains an `onDeviceDisappeared` handler that re-blocks temp-allowed devices. `DeviceListViewModel.disconnectDevice()` is the in-app disconnect path. The Shizuku helpers required to disconnect and re-block (`disconnectDevice`, `setConnectionPolicy`) are already implemented and reachable from a `BroadcastReceiver` via `BluetoothBouncerApp`.

## Goals / Non-Goals

**Goals:**
- Replace the disappearing notification with a "temporarily allowed" notification containing a one-tap "Disconnect" action
- Dismiss that notification automatically when the device leaves range or is disconnected from any path (notification action, app UI, or device disappearing)
- Reuse all existing Shizuku and DB infrastructure; introduce no new channels, permissions, or DB changes

**Non-Goals:**
- Timeout-based auto-dismiss (no timer; relying on CDM `onDeviceDisappeared` is sufficient)
- Making the notification non-dismissible (ongoing); user can swipe it away
- Error recovery UI beyond the existing error-notification pattern

## Decisions

### 1. Replace the notification in-place (same notification ID)

`WatchNotificationHelper.notificationId(macAddress)` is `macAddress.hashCode()`. Posting a new notification with the same ID atomically replaces the "nearby" notification with the "allowed" notification — no flicker, no duplicate entry in the shade. This is already the pattern used for silent re-post via `setOnlyAlertOnce(true)`.

**Alternative considered:** Cancel + post with a different ID. Rejected — causes a visual flash and two distinct entries in the notification shade momentarily.

### 2. New `DisconnectReceiver` — mirror of `TemporaryAllowReceiver`

The "Disconnect" action follows the identical `PendingIntent` → `BroadcastReceiver` → `goAsync()` → Shizuku pattern already established by `TemporaryAllowReceiver`. Logic:
1. `shizukuHelper.disconnectDevice(macAddress)`
2. `shizukuHelper.setConnectionPolicy(macAddress, POLICY_FORBIDDEN)`
3. `blockedDeviceDao.updateIsTemporarilyAllowed(macAddress, false)`
4. `NotificationManagerCompat.cancel(notifId)`
5. On failure: `postErrorNotification()`

**Alternative considered:** Reusing `TemporaryAllowReceiver` with a mode flag. Rejected — two responsibilities in one receiver; separate class is clearer and testable independently.

### 3. Auto-dismiss on device disappear — extend existing `onDeviceDisappeared` success path

`DeviceWatcherService.onDeviceDisappeared()` already calls `setConnectionPolicy(FORBIDDEN)` and clears the DB flag on success (lines 97–103). Adding a single `NotificationManagerCompat.cancel(notifId)` immediately after is the least invasive change and runs in the same coroutine block. This covers the "device went out of range" case.

### 4. Cancel notification from `DeviceListViewModel.disconnectDevice()` — Option 1

When the user disconnects via the app UI, the ViewModel already clears `isTemporarilyAllowed` in DB. Adding `NotificationManagerCompat.from(getApplication()).cancel(WatchNotificationHelper.notificationId(device.address))` immediately after the DB clear means no stale "temporarily allowed" notification lingers. The ViewModel has `getApplication()` context, so this is a one-liner.

**Alternative considered:** Let `onDeviceDisappeared` handle it eventually. Rejected — the notification would remain visible until the CDM fires, which could be seconds or more after the user has already disconnected from the app.

### 5. Notification properties for the "allowed" notification

- `setOnlyAlertOnce(true)` — no sound/vibration when replacing the "nearby" notification
- `PRIORITY_DEFAULT` (not HIGH) — no heads-up banner; just silently appears in the shade
- `setAutoCancel(false)` — tapping notification body does nothing (only the Disconnect button acts)
- Not `setOngoing(true)` — user can swipe to dismiss without disconnecting

## Risks / Trade-offs

- **Stale notification if process is killed after temp-allow but before disappear fires** → Notification may persist in the shade after a process death/restart. Mitigation: `onDeviceAppeared` already skips reposting if `isTemporarilyAllowed == true`; the allowed notification's presence is harmless since the Disconnect action will still work if Shizuku is available, and the notification will be cleaned up on the next `onDeviceDisappeared`.

- **Swipe-dismiss leaves device connected with no easy path back** → Acceptable; the app UI is always available. Considered making it ongoing but rejected as too intrusive for a quality-of-life feature.

- **`DisconnectReceiver` Shizuku call may fail** → Same failure mode as `TemporaryAllowReceiver`; mitigated identically with an error notification.

## Migration Plan

No data migration, no schema changes, no new permissions. Purely additive to existing receiver/service/viewmodel pattern. Deploy as a standard app update. No rollback strategy needed — worst case the notification reverts to disappearing on tap (same as current behaviour if the new code is absent).

## Open Questions

None — all decisions are settled.
