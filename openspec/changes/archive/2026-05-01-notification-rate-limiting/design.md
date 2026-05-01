## Context

`DeviceWatcherService.onDeviceAppeared` is called by the OS every time the CompanionDeviceManager detects a watched device's Bluetooth advertisements. For devices at the edge of range, this can fire repeatedly every few seconds. Each call unconditionally posts a notification via `WatchNotificationHelper.postNearbyNotification()`.

The notification is built with a deterministic ID (`macAddress.hashCode()`), so re-posts for the same device replace the existing notification rather than stacking. However, because `setOnlyAlertOnce(false)` is the default, each replacement triggers a full heads-up with sound and vibration — indistinguishable to the user from a brand new notification.

The fix is entirely contained within `WatchNotificationHelper.postNearbyNotification()`.

## Goals / Non-Goals

**Goals:**
- Eliminate repeated alert noise (sound, vibration, heads-up) when a device oscillates at range boundary
- Ensure the first appearance still produces a full alert
- Ensure a fresh alert fires if the user dismisses the notification (device still nearby, user swiped it away and comes back into range later)

**Non-Goals:**
- Time-based cooldowns or rate limiting
- Tracking device departure/return cycles
- Any changes to `DeviceWatcherService` or the posting logic

## Decisions

### Use `setOnlyAlertOnce(true)` on the notification builder

Android's `setOnlyAlertOnce(true)` flag means: if a notification with this ID already exists in the drawer, re-posting it will silently update the content without re-triggering sound, vibration, or heads-up. If the notification does not exist (first post, or user dismissed it), the full alert fires normally.

This is exactly the desired behaviour:
- Device oscillates → first `onDeviceAppeared` posts with full alert; all subsequent re-posts while notification is visible are silent
- User dismisses notification → next `onDeviceAppeared` is a fresh post (notification doesn't exist), so full alert fires again

**Alternatives considered:**

- **Timestamp-based cooldown in Room**: Store `lastNotifiedAt` per device, suppress posts within a window (e.g. 15 minutes). Rejected — a fixed cooldown is "slower spam." A friend's device nearby for 3 hours would still generate multiple alerts over time.
- **Disappearance tracking**: Record `lastDisappearedAt`, only re-notify after a "meaningful" absence threshold. More correct but considerably more complex for a problem that `setOnlyAlertOnce` already solves at the OS layer with no persistence, no timestamps, and no thresholds to tune.

## Risks / Trade-offs

- **User dismisses notification intentionally while device remains nearby**: The next `onDeviceAppeared` (potentially seconds later) will fire a fresh full alert. This is acceptable — the user dismissed it, so a re-alert is the right behaviour. In practice the oscillation cadence means this could happen quickly, but it's a rare edge case (why dismiss a notification while the device is actively buzzing?) and no worse than the current behaviour.
- **No state to roll back**: This is a single flag on a notification builder. There is no migration, no schema change, and no rollback concern.
