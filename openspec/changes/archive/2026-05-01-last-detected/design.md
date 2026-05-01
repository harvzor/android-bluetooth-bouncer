## Context

Detection state in the app is a live boolean (`isDetected`) computed on every refresh from whether a device has an active ACL link but no profile connection. When the ACL drops, `isDetected` immediately flips to `false` and the UI goes silent — no history of the event is retained.

Blocked devices typically establish a 1–2 second ACL blip before `CONNECTION_POLICY_FORBIDDEN` causes a disconnection. With the 300 ms debounce on the refresh pipeline, the live "Detected" label can appear briefly, but users frequently miss it. After the ACL drops, the list row shows nothing, which hides meaningful activity.

The relevant code is concentrated in two files: `DeviceListViewModel.kt` (state computation and event handling) and `DeviceListScreen.kt` (UI rendering).

## Goals / Non-Goals

**Goals:**
- After an ACL link drops on a previously detected device, show "Detected Ns ago" counting up second-by-second for 30 seconds.
- While an ACL link is live, keep showing "Detected" unchanged.
- Keep the ticker lightweight — no Bluetooth API calls, no Room queries, no reflection during tick.
- Hold timestamps in memory only — no persistence, no migration.

**Non-Goals:**
- Persisting detection history across app restarts.
- Notifications or any out-of-app signal for the decay state.
- Changing the sort order for recently-detected devices (they stay in the "offline" tier).
- Showing the decay label for devices that are temporarily allowed or connected.

## Decisions

### 1. In-memory map for timestamps, not a new model layer

**Decision**: Store `lastDetectedTimes: MutableMap<String, Long>` (address → `SystemClock.elapsedRealtime()`) as private ViewModel state.

**Alternatives considered**:
- Room column — adds schema migration, survives restarts (unnecessary), and couples ephemeral UI state to the persistence layer.
- Separate `StateFlow` per device — over-engineered for a simple countdown.

**Rationale**: The feature is UI-only and intentionally ephemeral. In-memory state in the ViewModel is the right home for this. `elapsedRealtime()` is used instead of wall clock because it doesn't jump on timezone/NTP changes.

### 2. Transition detection by diffing address sets

**Decision**: `refreshDeviceList()` compares the newly computed `detectedAddresses` with a `previousDetectedAddresses: Set<String>` field to find which addresses just left the detected set.

```
transitions = previousDetectedAddresses - detectedAddresses
```

For each address in `transitions`: stamp `lastDetectedTimes[address] = SystemClock.elapsedRealtime()`.  
For each address newly in `detectedAddresses` that has an existing `lastDetectedTimes` entry: clear it (device is live again, no "ago" label needed).  
Then update `previousDetectedAddresses = detectedAddresses`.

**Rationale**: Clean, O(n), no hidden state. Fires only on actual transitions, not on every refresh.

### 3. Decay ticker remaps existing device list — no Bluetooth calls

**Decision**: A `decayTickerJob` coroutine in the ViewModel runs a 1-second loop that:
1. Evicts `lastDetectedTimes` entries older than 30 seconds.
2. Remaps `_uiState.value.devices` in place, recomputing `lastDetectedSecondsAgo` for each device from the map.
3. Calls `_uiState.update { it.copy(devices = updatedDevices) }`.
4. Stops the loop when `lastDetectedTimes` is empty.

The ticker is started (or restarted) from `refreshDeviceList()` whenever at least one timestamp exists. If it's already running, `refreshDeviceList()` does not start a second one — it checks `decayTickerJob?.isActive`.

**Alternatives considered**:
- Ticking inside the Composable via `LaunchedEffect` — pushes business logic into the UI layer.
- Calling `refreshDeviceList()` from the ticker — triggers reflection and Room queries on every tick, which is wasteful.

**Rationale**: The ticker only needs to age numbers. It already has everything it needs in `_uiState.value.devices` and `lastDetectedTimes`. Keeping Bluetooth queries out of the tick path is important for performance and correctness (querying ACL state every second is unnecessary noise).

### 4. `lastDetectedSecondsAgo: Int?` on `DeviceUiModel`, not a separate UI state

**Decision**: Add `lastDetectedSecondsAgo: Int?` to the existing `DeviceUiModel`. `null` means nothing to show; 0–29 means show the label.

**Rationale**: `DeviceUiModel` already travels as a list through `UiState`. Adding a field keeps the data co-located with the device it describes and avoids a parallel address-keyed map in the UI layer.

### 5. UI filtering at the render site

**Decision**: The "Detected Ns ago" label in `DeviceRow` is conditioned on `device.lastDetectedSecondsAgo != null` only (no `isBlocked` check). The ViewModel populates `lastDetectedSecondsAgo` for all devices whose ACL drops.

**Rationale**: Non-blocked devices whose ACL drops while not profile-connected are rare in practice (the detection tier only applies to devices with ACL but no profile connection, and for non-blocked devices that normally means a brief profile-setup window). Keeping the logic unconditional in the ViewModel means the UI is authoritative about what to show, and it's trivially easy to restrict or widen visibility in the future with a one-line UI change.

## Risks / Trade-offs

- **Sub-300ms blips are invisible** → The 300ms debounce on `refreshSignal` means an ACL link that comes and goes in under 300ms will be coalesced into a single refresh where the device is already gone. The timestamp will never be stamped. Acceptable — real Bluetooth ACL establishment takes at minimum ~50–100ms, and the specific blip pattern observed is ~1–2 seconds.

- **ViewModel killed, timestamps lost** → Intentional. The feature is scoped to in-session awareness. If the user navigates away and back, the history resets. This is the correct behaviour for ephemeral state.

- **Ticker runs while app is backgrounded** → `viewModelScope` is tied to the ViewModel's lifecycle, not the screen's. If the user backgrounds the app while a timer is active, the coroutine continues until the ViewModel is cleared. Impact is negligible (one `delay(1000)` loop with a map lookup and a list remap). The loop self-terminates when the map empties.

- **Recomposition every second** → `_uiState.update` with a new devices list triggers Compose recomposition for the entire device list. Each device row will re-render. For typical use (< 20 bonded devices) this is imperceptible. If it becomes a concern, `DeviceUiModel` can be annotated with `@Stable` and the list wrapped in a stable holder to minimise recomposition scope.

## Open Questions

None. All decisions resolved during exploration.
