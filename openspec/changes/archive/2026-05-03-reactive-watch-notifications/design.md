## Context

Device-watch notifications are currently updated by imperative calls to `WatchNotificationHelper` scattered across four call sites: `TemporaryAllowReceiver`, `DisconnectReceiver`, `DeviceListViewModel.disconnectDevice`, and `DeviceWatcherService`. Each call site that changes device state is individually responsible for deciding which notification to post. The UI-initiated connect path (`DeviceListViewModel.connectDevice`) was never wired, leaving a bug where the "Nearby" notification stays visible after the user connects from the app UI.

The fix is to make notification state a derived product of existing Room state and in-memory CDM presence state, observed in a single place, rather than something any call site has to remember to update.

## Goals / Non-Goals

**Goals:**
- Notification content always reflects current device state regardless of which code path caused the change
- Single place in the codebase responsible for posting/cancelling device-watch notifications
- No new Room migrations or external dependencies

**Non-Goals:**
- Changing what the notifications look like or what actions they offer
- Handling the error notification (`postErrorNotification`) reactively — it is an exceptional, transient signal, not derived from persistent state
- Supporting devices that are not in the blocked devices Room table

## Decisions

### Decision 1: In-memory set for "nearby" state, not a Room column

**Choice:** `nearbyDevices: MutableStateFlow<Set<String>>` held on `BluetoothBouncerApp`, populated by CDM presence callbacks.

**Rationale:** CDM delivers presence *transitions*, not current state. If we persisted `isNearby` to Room, a crash between `onDeviceDisappeared` firing and the Room write completing would leave the device permanently marked as nearby — resulting in a phantom notification that has no self-healing path. With an in-memory set the default on process restart is empty (no phantom). The practical gap (process death while device is nearby, with no subsequent CDM re-delivery) is extremely narrow: the user connecting from the UI requires the app to be in the foreground, which means the process started after the last CDM transition and will receive another `onDeviceAppeared` when the service re-binds.

**Alternative considered:** Room `isNearby` column. Rejected because stale-true is a worse failure mode than a brief window of stale-false.

### Decision 2: Observer lives in BluetoothBouncerApp, not DeviceWatcherService

**Choice:** Application-scoped `CoroutineScope` in `BluetoothBouncerApp.onCreate` collects the combined `nearbyDevices + dao.getAllDevices()` flow and drives notifications.

**Rationale:** `CompanionDeviceService` has an unusual lifecycle — the OS can bind/unbind it independently. Holding observer state inside `DeviceWatcherService` risks losing it between binds. The Application object lives for the entire process lifetime and already holds the database and `ShizukuHelper`. It is the natural owner of process-wide reactive state.

**Alternative considered:** A singleton `NotificationStateManager` object initialised from `onCreate`. Acceptable, but adds a class with no meaningful isolation benefit given the app's size. Inline in `BluetoothBouncerApp` is simpler.

### Decision 3: Derive notification state with a `combine` of two flows

**Choice:**
```
combine(nearbyDevices, dao.getAllDevices()) { nearby, devices ->
    for each device in devices:
        val notifId = notificationId(device.macAddress)
        when {
            device.macAddress !in nearby        -> cancel(notifId)
            device.isTemporarilyAllowed         -> postAllowedNotification(...)
            device.isAlertEnabled               -> postNearbyNotification(...)
            else                                -> cancel(notifId)  // CDM-only, no alert
        }
}
```

**Rationale:** Room's `getAllDevices()` is already a `Flow` that emits on every write. `nearbyDevices` is a `StateFlow`. Combining them means any write to Room (e.g., `updateIsTemporarilyAllowed`) automatically triggers re-evaluation and the correct notification is posted without any call site needing to know about it. The `setOnlyAlertOnce(true)` flag on notifications ensures re-posting an identical notification does not re-alert the user.

### Decision 4: Remove manual WatchNotificationHelper calls from call sites

After the observer is in place, the following calls become redundant and are removed:
- `TemporaryAllowReceiver`: `postAllowedNotification()` — Room write triggers observer
- `DisconnectReceiver`: `postNearbyNotification()` — Room write triggers observer
- `DeviceListViewModel.disconnectDevice`: `postNearbyNotification()` — Room write triggers observer

`DeviceWatcherService.onDeviceAppeared` no longer calls `postNearbyNotification()` — it writes to `nearbyDevices` instead, which triggers the observer.
`DeviceWatcherService.onDeviceDisappeared` removes from `nearbyDevices` instead of calling `cancel()` directly. Re-block logic (Shizuku call + Room write) stays in place.

## Risks / Trade-offs

**[Risk] Brief notification flicker on rapid state changes** → The observer emits on every Room write. If `disconnectDevice` writes `isTemporarilyAllowed = false` and then Room's `getAllDevices()` emits before `nearbyDevices` is updated (or vice versa), there could be a transient intermediate state. Mitigation: debounce the observer with a short delay (e.g., 100ms) or use `conflate()` to drop intermediate emissions. For this app's usage patterns, the risk is low enough that no debounce is needed initially.

**[Risk] CDM re-delivery not guaranteed on process restart** → If the process restarts while a device is nearby and CDM does not re-deliver `onDeviceAppeared`, `nearbyDevices` starts empty and the notification is not re-posted. Mitigation: acceptable — the notification may briefly disappear and then reappear when the user interacts, or on next CDM transition. The alternative (Room persistence) has a worse failure mode.

**[Risk] Observer posts notifications before the notification channel is created** → `createNotificationChannel` is called in `BluetoothBouncerApp.onCreate` before the observer is launched. No risk.

## Migration Plan

No database migration required. No external dependency changes. The refactor is entirely within existing classes:

1. Add `nearbyDevices` + observer to `BluetoothBouncerApp`
2. Update `DeviceWatcherService` to write to `nearbyDevices` instead of calling `WatchNotificationHelper`
3. Remove `WatchNotificationHelper` calls from `TemporaryAllowReceiver`, `DisconnectReceiver`, and `DeviceListViewModel.disconnectDevice`
4. Build, install, and verify on device

Rollback: revert commits. No persistent state was changed.

## Open Questions

None — design is fully resolved based on exploration session.
