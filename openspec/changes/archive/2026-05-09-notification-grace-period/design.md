## Context

The reactive notification observer (`BluetoothBouncerApp.launchNotificationObserver`) cancels a device's notification the instant its MAC is removed from `nearbyDevices`. The removal is triggered by `DeviceWatcherService.onDeviceDisappeared()` for non-temporarily-allowed devices.

CDM's presence detection is aggressive — it can fire `onDeviceDisappeared` within a few seconds of a device going to the edge of range, while the Bluetooth ACL link (and thus the UI's "Detected recently" state) may persist for 30 more seconds. This causes the notification to disappear while the user can still see "Detected recently" in the app. The user has no way to tap "Allow temporarily" from the shade during that window.

The UI already has a 30-second grace period for the "Detected recently" label, driven by `lastDetectedTimes` in `DeviceListViewModel` and the `30_000L` eviction threshold. The notification system has no equivalent — it responds instantly to CDM events.

## Goals / Non-Goals

**Goals:**
- Notification stays visible for at least `DETECTION_GRACE_PERIOD_MS` after CDM fires `onDeviceDisappeared` for non-temp-allowed devices
- If the device reappears within the grace window, the pending notification cancellation is abandoned — no flicker
- The grace period duration is a single shared constant so the notification window and UI decay window stay in sync automatically

**Non-Goals:**
- Changing the temp-allowed disappear path — that path re-blocks via Shizuku and should remain immediate
- Changing notification behaviour for devices that are already connected (ACL-deferred re-block path is unchanged)
- Persisting the grace period across process death — in-memory only, same as `nearbyDevices` itself

## Decisions

### 1. Delay removal from `nearbyDevices` rather than change the notification observer

The notification observer already has correct logic: cancel when MAC is not in `nearbyDevices`. The simplest fix is to make `nearbyDevices` reflect the correct "is the device still relevant" state rather than the raw CDM presence state.

Alternatives considered:
- **Add a separate grace-period set to the observer**: Would require combining three flows (nearby, grace, Room), adding complexity to `BluetoothBouncerApp`. Rejected — the observer is simpler with a single source of truth.
- **Skip the cancel in the observer for a time window**: Would require timestamping removal events in the observer. Rejected — push/pull inversion, harder to reason about.

### 2. Track pending removal jobs per MAC in `DeviceWatcherService`

A `MutableMap<String, Job>` (`pendingRemovals`) in the service holds one delayed coroutine per device that is in the grace period. On `onDeviceAppeared`, the pending job for that MAC (if any) is cancelled before adding to `nearbyDevices`. On `onDeviceDisappeared` for a non-temp-allowed device, a new delayed job is created.

The service's existing `serviceScope` (cancelled in `onDestroy()`) automatically kills all pending removals when the service is destroyed — no additional cleanup needed.

Alternatives considered:
- **Single scheduled check on a timer**: Less precise, would need to iterate all pending devices. Rejected — per-device jobs are simpler and cancel cleanly.
- **Grace period in `BluetoothBouncerApp`**: Would move disappear logic out of the service and into the Application class. Rejected — keeps concerns separated; the service owns CDM event handling.

### 3. Shared constant `DETECTION_GRACE_PERIOD_MS` on `BluetoothBouncerApp.Companion`

Both `DeviceWatcherService` (new grace period) and `DeviceListViewModel` (existing decay ticker) need the same 30-second value. Placing it on `BluetoothBouncerApp.Companion` makes it accessible to both without introducing a new file or a circular dependency.

Alternatives considered:
- **New `AppConstants.kt` file**: More discoverable but adds a file for a single constant. Rejected for now — can be refactored later if more constants accumulate.
- **Duplicate literals**: Rejected — the whole point is to keep them in sync.

## Risks / Trade-offs

- **Notification lingers 30s after device genuinely leaves range** → Acceptable trade-off; the user sees "Detected recently" in the UI for the same duration. Tapping "Allow temporarily" on a stale notification will attempt to allow a device that's out of range, which will either succeed (if the ACL is briefly maintained) or fail gracefully via the existing Shizuku error path.
- **`pendingRemovals` map grows unbounded if `onDeviceAppeared`/`onDeviceDisappeared` oscillate rapidly** → Each new disappear replaces the previous job for the same MAC (the old job is cancelled first), so at most one entry per device exists at any time.
- **Service destroyed before grace period expires** → `serviceScope.cancel()` in `onDestroy()` kills all pending jobs. `nearbyDevices` in `BluetoothBouncerApp` will still contain the MAC until process death resets it to empty — acceptable since the notification observer is also Application-scoped and the process lifetime is the same.

## Migration Plan

No data migration. No schema changes. Purely additive to `DeviceWatcherService`; one constant extraction; one constant reference in `DeviceListViewModel`. Deploy as a standard app update with no rollback concern.
