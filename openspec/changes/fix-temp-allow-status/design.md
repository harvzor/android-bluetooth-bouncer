## Context

After a user taps "Allow temporarily" on the Bluetooth Bouncer notification, `TemporaryAllowReceiver` calls Shizuku to set `POLICY_ALLOWED` and writes `isTemporarilyAllowed = true` to Room. The device then connects its A2DP and/or HFP profiles — but the UI stays stuck on "Detected" until the app is restarted.

Two independent problems cause this:

1. The `BroadcastReceiver` registered in `DeviceListViewModel` only listens for ACL-level events (`ACTION_ACL_CONNECTED`) and adapter state changes. For a headset that was already ACL-linked (the OS keeps the ACL up while enforcing `POLICY_FORBIDDEN`), unblocking via Shizuku causes profile connections without firing a new ACL event. No refresh is triggered.

2. Even if the UI did refresh, `DeviceUiModel` has no `isTemporarilyAllowed` field, so the `DeviceRow` composable cannot distinguish "temporarily connected" from "connected" and has no way to show a distinct label.

## Goals / Non-Goals

**Goals:**
- UI refreshes within ~1 second when a temporarily-allowed device connects its profiles.
- When a temporarily-allowed device is connected, its status label reads "Temporarily connected" in a visually distinct (amber) colour.
- The "Connected" label is unchanged for permanently-allowed devices.

**Non-Goals:**
- Changing any persistence, blocking, or Shizuku logic — this is purely a UI/ViewModel concern.
- Adding a "Temporarily allowed but not yet connected" label — as discussed, this transient window is too brief to be meaningful for audio devices.
- Changes to the `onDeviceDisappeared` re-blocking logic.

## Decisions

### Add profile-level broadcast actions to the IntentFilter

`registerBluetoothEventReceiver()` currently adds only `ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`, and `ACTION_STATE_CHANGED`. The fix adds `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` and `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED`.

**Alternative considered:** Use a dedicated `Flow` collector on the Room table change (already exists) and rely on the fact that `TemporaryAllowReceiver` writes to Room. This would propagate the `isTemporarilyAllowed` change immediately, but `refreshDeviceList()` at that moment would still find the device un-connected because the profile hasn't established yet. The profile broadcast is still required to capture the connection event.

**Rationale:** Adding the two broadcast actions is the minimal, targeted fix. The existing `refreshSignal` debounce mechanism already handles burst-coalescing, so no additional infrastructure is needed.

### Add `isTemporarilyAllowed` to `DeviceUiModel` and populate from Room

In `refreshDeviceList()`, build a `temporarilyAllowedAddresses: Set<String>` from `blockedDevices.filter { it.isTemporarilyAllowed }`. Pass it into each `DeviceUiModel` constructor call.

**Rationale:** `blockedDevices: List<BlockedDeviceEntity>` is already available in `refreshDeviceList()`. The pattern of building a set and doing membership tests is already used for `blockedAddresses` and `watchedAddresses` — this follows the same convention with zero new I/O.

### Status label cascade order

```
isConnected && isTemporarilyAllowed  →  "Temporarily connected"  (amber)
isConnected                          →  "Connected"              (green)
isDetected                           →  "Detected"               (muted)
```

**Alternative considered:** Show "Temporarily connected" in green with a clock icon. Rejected — colour-only differentiation is sufficient at this scale, and amber aligns with the "caution/transient" semantic without requiring drawable assets.

**Rationale:** The temporarily-allowed state is intentionally transient and auto-reverts when the device leaves range. Amber communicates "permitted but temporary" clearly. The existing green `0xFF4CAF50` is reserved for stable, permanent connection states.

## Risks / Trade-offs

- **Profile broadcast volume**: `ACTION_CONNECTION_STATE_CHANGED` fires for every profile state transition (connecting → connected → disconnecting → disconnected). Combined with the existing 300 ms debounce on `refreshSignal`, rapid transitions coalesce safely. Risk is low.
- **Proxy availability race**: `a2dpProxy` and `headsetProxy` are populated asynchronously via `getProfileProxy()`. If a profile broadcast fires before the proxy is bound, `connectedDevices` returns an empty list. The subsequent profile-connected callback (which triggers another `refreshSignal`) will correct the state. This is a pre-existing race, not introduced by this change.
- **`isTemporarilyAllowed` stale in UI if Room update races the profile broadcast**: If the Room flow emits (from `TemporaryAllowReceiver`) before the profile connects, the UI shows "Detected" briefly with `isTemporarilyAllowed = true` — but the label cascade only shows "Temporarily connected" when `isConnected` is also true, so the label stays "Detected" during this gap. This is acceptable: the label corrects itself as soon as the profile broadcast fires.
