## Context

The Connect button in `DeviceListScreen` currently has two visible states: shown (idle) and absent (in-flight or temp-allowed-but-disconnected). When the user taps Connect, the Shizuku IPC call completes quickly, but the OS-level Bluetooth connection is asynchronous — detected only when a `BroadcastReceiver` fires and `refreshDeviceList()` re-queries profile proxies. The gap between IPC completion and `isConnected` flipping true leaves the UI with no button and no feedback.

`connectLoadingAddress` is already tracked in `UiState` and propagated to `DeviceRow`, but is currently cleared in the `finally` block immediately after the IPC call returns.

## Goals / Non-Goals

**Goals:**
- Show "Connecting..." text on the device row from the moment Connect is tapped until the device is confirmed connected or the timeout expires
- Automatically revert all side effects (policy, `isTemporarilyAllowed`) on timeout
- Keep the UI recoverable: Connect button always comes back after a failed or timed-out attempt

**Non-Goals:**
- A cancel button or explicit abort mechanism
- Changing the Disconnect flow in any way
- Changing the Shizuku IPC timeout or retry logic
- Any visual change beyond the button label (no spinner, no progress bar)

## Decisions

### 1. Timeout location: ViewModel, not Shizuku layer

The Shizuku layer already has its own timeouts (10s service bind, 8s per-proxy). Those guard IPC reliability. The new 5-second timeout is a **UI concern** — how long the app waits for `isConnected` to reflect the OS connection. Keeping it in `DeviceListViewModel` means the Shizuku layer stays stateless and reusable.

_Alternative considered_: Adding a timeout to `ShizukuHelper.connectDevice()`. Rejected — that layer has no access to device state and would conflate transport reliability with UX responsiveness.

### 2. Observe `_uiState` for connection confirmation

Rather than polling or introducing a separate signalling mechanism, the post-IPC wait uses a `StateFlow` collect on the existing `_uiState`:

```kotlin
_uiState
    .map { s -> s.devices.any { it.address == addr && it.isConnected } }
    .first { it }
```

This is idiomatic, zero-cost when the state is already live, and composes cleanly with `withTimeout`. `refreshDeviceList()` already drives `_uiState` from BroadcastReceiver events, so no new observation path is needed.

_Alternative considered_: A separate `MutableStateFlow<Boolean>` per device. Rejected — adds complexity and duplicates what `_uiState.devices` already tracks.

### 3. Timeout duration: 5 seconds

5 seconds is long enough to cover typical Bluetooth profile negotiation (usually 1–3s) while feeling responsive on failure. It is shorter than the existing Shizuku proxy timeout (8s) so the UX timeout is always the binding constraint from the user's perspective.

### 4. Rollback on timeout: full revert

On timeout, all side effects are reversed:
- Blocked/temp-allowed path: `setConnectionPolicy(FORBIDDEN)` called, `isTemporarilyAllowed` cleared in DB
- `connectLoadingAddress` cleared → Connect button restored

This is the safest outcome. If the device was mid-handshake and connects after the 5-second window, the policy revert kicks it off. The user can tap Connect again. Leaving a dangling `POLICY_ALLOWED` with no UI tracking would be harder to reason about.

### 5. IPC failure bypasses the wait

If the Shizuku IPC itself returns `Result.failure`, the timeout wait is never entered — the error snackbar fires immediately and loading is cleared. The 5-second wait only begins after a successful IPC call, because there is no OS connection to wait for if the IPC failed.

### 6. UI: "Connecting..." as a third `when` branch

The existing `when` block in `DeviceRow` gains a new first branch: if `isConnectLoading` is true (regardless of `isConnected` or `isTemporarilyAllowed`), show a disabled "Connecting..." `TextButton`. This takes priority over all other states — it is impossible for `isConnectLoading` to be true while `isConnected` is also true (the StateFlow collect would have completed and cleared loading), so the ordering is safe.

## Risks / Trade-offs

- **Device connects at 5.001s** → policy is reverted and device is disconnected. User taps Connect again. Acceptable: 5s covers all realistic cases; a device that takes >5s has a deeper issue.
- **`viewModelScope` cancelled mid-wait** (navigation away) → coroutine is cancelled, loading is cleared by scope cancellation. The rollback coroutine does not fire — this is acceptable because the ViewModel is being destroyed and no UI is observing the state.
- **Rollback `setConnectionPolicy` call fails** → logged as a warning. The Connect button is still restored. The device may be in `POLICY_ALLOWED` with `isTemporarilyAllowed = false`; the `DeviceWatcherService` presence-leave path would eventually re-apply `FORBIDDEN` if the device is being watched. Low probability.

## Migration Plan

No data migration required. No new dependencies. The change is entirely within `DeviceListViewModel` and `DeviceListScreen`. It ships as a single release with no feature flag.
