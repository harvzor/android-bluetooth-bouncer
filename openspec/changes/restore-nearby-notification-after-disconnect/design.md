## Context

After a user taps "Disconnect" (notification action or in-app button), the current code cancels the notification and the device is re-blocked. The device is still physically nearby — the CDM will not fire `onDeviceAppeared` again because `onDeviceDisappeared` has not fired. This leaves the user with no notification-based path to re-allow the device; they must open the app.

A secondary problem surfaced during design: if the user disconnects while the device is still in range, `postNearbyNotification` is called to restore the notification. If the device then leaves range shortly after, `onDeviceDisappeared` fires with `isTemporarilyAllowed = false`, which currently takes no action — leaving the "nearby" notification stranded in the shade indefinitely.

Current state:
- `postNearbyNotification(context, entity)` — takes a full `BlockedDeviceEntity`
- `DisconnectReceiver` and `DeviceListViewModel` cancel the notification on success
- `onDeviceDisappeared` only acts when `isTemporarilyAllowed = true`

## Goals / Non-Goals

**Goals:**
- Restore the "nearby" notification after manual disconnect so the user can re-allow from the shade
- Prevent orphaned "nearby" notifications when the device departs after a manual disconnect
- Keep the change surgical — no new channels, no new DB fields, no new permissions

**Non-Goals:**
- Re-alerting (sound/vibration/heads-up) when the nearby notification is restored after disconnect
- Any behaviour change for the automatic re-block path (`onDeviceDisappeared` with `isTemporarilyAllowed = true`) — that path already cancels the notification correctly

## Decisions

### 1. Replace cancel with `postNearbyNotification` on manual disconnect paths

Both `DisconnectReceiver` and `DeviceListViewModel.disconnectDevice()` currently call `NotificationManagerCompat.cancel(notifId)` on success. Replacing this with `postNearbyNotification(context, macAddress, deviceName)` transitions the "temporarily allowed" notification in-place (same notification ID) to the "nearby" state — silently, with no new heads-up, because the notification already exists in the shade and `setOnlyAlertOnce(true)` suppresses re-alerting for updates.

**Alternative considered:** Cancel + wait for `onDeviceAppeared` to re-fire. Rejected — `onDeviceAppeared` only fires on the CDM transition from absent → present; with the device already present, it never re-fires.

### 2. Add `postNearbyNotification(context, macAddress, deviceName)` overload

The existing `postNearbyNotification(context, entity)` requires a `BlockedDeviceEntity`. `DisconnectReceiver` has `macAddress` and `deviceName` as intent extras but fetching the entity from the DB for a notification post is unnecessary overhead. A lightweight overload taking just `(context, macAddress, deviceName)` keeps the call sites simple. The entity-based overload in `DeviceWatcherService.onDeviceAppeared` is unchanged.

**Alternative considered:** Query the entity in the receiver. Rejected — the two fields needed (`macAddress`, `deviceName`) are already available as extras; adding a DB query adds latency and complexity for no gain.

### 3. Cancel notification in `onDeviceDisappeared` for all alert-enabled devices

Currently `onDeviceDisappeared` returns early if `!isTemporarilyAllowed`. This means a "nearby" notification restored after manual disconnect will orphan in the shade when the device subsequently leaves range. The fix: after the early-exit check for `isTemporarilyAllowed` (which handles re-blocking), add an unconditional notification cancel for any `isAlertEnabled` device that disappears. This runs regardless of `isTemporarilyAllowed` state and ensures no notification lingers.

The cancel is a no-op if no notification is posted for that device, so it is safe to call unconditionally for all alert-enabled devices.

**Alternative considered:** Track "is nearby notification currently showing" in DB or SharedPreferences. Rejected — over-engineered; a cancel on a non-existent notification ID is a no-op on Android, so the unconditional cancel is both correct and cheap.

## Risks / Trade-offs

- **Notification flicker on in-place update** → Negligible: same notification ID + `setOnlyAlertOnce(true)` means the OS updates the existing row silently. No observable flash.
- **"Nearby" notification appears while user is in the app** → When the user disconnects from the app UI, a "nearby" notification will appear (or update) in the shade. This is intentional and unobtrusive (no heads-up), but is a visible side-effect of in-app actions. Acceptable given the use case.
- **Race: disconnect completes, device disappears before notification is posted** → Window is milliseconds; the cancel in `onDeviceDisappeared` would fire first, then `postNearbyNotification` would post a "nearby" notification for a device that's gone. Mitigation: this is cosmetic only — the notification will be stale but tappable, and the next `onDeviceAppeared` will correctly re-post or the user can dismiss. Very low probability in practice.

## Migration Plan

No data migration, no schema changes. Purely additive to existing call sites. Deploy as a standard app update.
