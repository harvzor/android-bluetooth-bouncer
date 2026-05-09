## Why

When CDM fires `onDeviceDisappeared` for a non-temporarily-allowed device, the reactive notification observer immediately removes the device from `nearbyDevices` and cancels the notification — but CDM's presence detection is aggressive and fires well before the user perceives the device as gone. The result: the "Nearby — tap to allow" notification flashes briefly and disappears while the UI still shows "Detected recently", leaving the user with no way to act on it from the shade.

This is a regression from `da7774d` (reactive notification refactor), which re-introduced the same bug that was explicitly reverted in `f1a1f2f`: removing from `nearbyDevices` on disappear for non-temp-allowed devices now causes the notification observer to cancel the notification, just through a different mechanism.

## What Changes

- When `onDeviceDisappeared` fires for a non-temporarily-allowed device, the removal from `nearbyDevices` is **delayed by a grace period** (matching the UI's detection-decay window) instead of happening immediately
- If `onDeviceAppeared` fires for the same device within the grace period, the pending removal is cancelled — the notification stays up without interruption
- The `30_000 ms` detection-decay timeout is extracted into a shared constant (`DETECTION_GRACE_PERIOD_MS` on `BluetoothBouncerApp`) so the notification grace period and the UI decay window are always in sync
- The `30_000L` magic number in `DeviceListViewModel` is replaced with the shared constant

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `reactive-notification-state`: The "device leaves nearby set" trigger is now deferred for non-temporarily-allowed devices — removal from `nearbyDevices` (and thus notification cancellation) is delayed by `DETECTION_GRACE_PERIOD_MS` and can be cancelled if the device reappears within that window
- `device-watching`: The `onDeviceDisappeared` scenario for non-temporarily-allowed devices changes from "no action taken" to "schedule delayed removal from nearby set"

## Impact

- `BluetoothBouncerApp.kt` — add `DETECTION_GRACE_PERIOD_MS` constant to companion object
- `DeviceWatcherService.kt` — add `pendingRemovals: MutableMap<String, Job>`; delay nearbyDevices removal in `onDeviceDisappeared` for non-temp-allowed devices; cancel pending removal in `onDeviceAppeared`
- `DeviceListViewModel.kt` — replace `30_000L` magic number with `BluetoothBouncerApp.DETECTION_GRACE_PERIOD_MS`
- No DB schema changes, no new permissions, no new notification channels
