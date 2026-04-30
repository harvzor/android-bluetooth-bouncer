## Context

The app shows each paired Bluetooth device's connection status ("Connected" indicator, green badge, icon tint) in the device list. This status is read once when `refreshDeviceList()` runs, using reflection on the hidden `BluetoothDevice.isConnected()` API. There are no listeners for Bluetooth connection events, so the UI freezes on the status it read at launch. Users must restart the app to see updated connection state.

The existing `refreshDeviceList()` is triggered by a `combine` flow on Shizuku state and Room's blocked-device table. Neither of these emits when a Bluetooth device connects or disconnects. Additionally, the public `refreshDevices()` method launches a new unbounded Room collector on every call, leaking coroutines.

## Goals / Non-Goals

**Goals:**
- Connection status updates live while the app is in the foreground, without user action
- Bluetooth adapter on/off state updates live while the app is in the foreground
- Device list refreshes when bond state changes (via existing `BondStateReceiver`)
- Fix the coroutine leak in `refreshDevices()`

**Non-Goals:**
- Background monitoring or notifications when the app is not visible — connection status is only meaningful while the user is looking at the device list
- Replacing the reflection-based `isConnected()` polling with per-profile `BluetoothProfile` proxy callbacks — the current approach works and is simpler; the goal is just to re-poll at the right times
- Adding pull-to-refresh gesture — this is a separate enhancement

## Decisions

### 1. Lifecycle-aware receiver in ViewModel (not manifest-registered)

**Decision:** Register ACL and adapter state receivers dynamically, scoped to the ViewModel's lifecycle, rather than adding manifest-registered receivers.

**Rationale:** These events only matter when the UI is active. Manifest-registered receivers would fire even when the app is not visible, wasting resources. The ViewModel already holds the `Application` context needed for `registerReceiver` / `unregisterReceiver`, and cleanup happens naturally in `onCleared()`.

**Alternative considered:** Registering in `MainActivity` and forwarding events — rejected because it couples the Activity to ViewModel internals and the Activity is intentionally thin (just `setContent`).

### 2. Single receiver for all Bluetooth state events

**Decision:** Use one `BroadcastReceiver` instance with a multi-action `IntentFilter` covering `ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`, and `BluetoothAdapter.ACTION_STATE_CHANGED`.

**Rationale:** All three events have the same effect: trigger `refreshDeviceList()`. Separate receivers would add complexity without benefit.

### 3. Debounce rapid events

**Decision:** Debounce incoming broadcast events to avoid redundant list rebuilds when multiple devices connect/disconnect in quick succession (e.g., Bluetooth toggled off disconnects several devices at once).

**Rationale:** `refreshDeviceList()` iterates all bonded devices with reflection calls — running it 5 times in 200ms is wasteful. A short debounce window (~300ms) coalesces bursts while remaining imperceptible to the user.

**Mechanism:** A `MutableSharedFlow<Unit>` that the receiver emits into, collected with `debounce(300)` in the ViewModel's coroutine scope.

### 4. Fix `refreshDevices()` coroutine leak

**Decision:** Replace the current `refreshDevices()` implementation (which launches a new collector each call) with a one-shot read or a mechanism that cancels the previous collector.

**Rationale:** Each call to `refreshDevices()` currently adds an indefinitely-running `collect` on the Room Flow. Over time this accumulates zombie collectors. The simplest fix is to make it a one-shot: read the current blocked devices, call `refreshDeviceList()`, and return. The continuous collection already happens in the `init` block's `combine` — `refreshDevices()` doesn't need to duplicate it.

### 5. BondStateReceiver notifies via app-level event bus

**Decision:** Have `BondStateReceiver` post a refresh signal to the same `MutableSharedFlow` that the new connection receiver uses, so the ViewModel picks it up through the existing debounced pipeline.

**Rationale:** `BondStateReceiver` is manifest-registered and doesn't hold a reference to the ViewModel. The signal flows through `BluetoothBouncerApp`, which both the receiver and ViewModel can access. If the ViewModel isn't alive (app not open), the signal is simply dropped — which is fine since `init` will refresh on next launch anyway.

## Risks / Trade-offs

**[Risk] Reflection on `isConnected()` may stop working in future Android versions** → This is a pre-existing risk, not introduced by this change. Mitigation: the polling approach is wrapped in a try/catch that degrades gracefully (treats device as disconnected). If this API is removed, a future change can switch to `BluetoothProfile` proxy callbacks.

**[Risk] Broadcast receiver registration requires `BLUETOOTH_CONNECT` permission** → The receiver is only registered after the permission is granted (which is gated by `onBluetoothPermissionResult`). If registration is attempted without the permission, it will throw. Mitigation: guard registration behind the permission-granted state.

**[Trade-off] Polling on event vs. streaming profile callbacks** → Polling all bonded devices on each ACL event is O(n) with reflection. For the typical user (< 20 bonded devices), this is negligible. Profile proxy callbacks would give per-device granularity but require managing proxy lifecycle for multiple profiles — significantly more complex for no practical user benefit at this scale.
