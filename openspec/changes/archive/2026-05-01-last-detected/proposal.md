## Why

Blocked devices often establish a brief ACL link (1–2 seconds) before the connection policy kicks them off. This blip is currently invisible after the fact — `isDetected` flips back to `false` and the UI shows nothing, leaving no trace that the device tried. Users watching the list may miss the event entirely, especially with the 300 ms debounce.

## What Changes

- After a device's ACL link drops, if it was previously detected, its row displays "Detected Ns ago" (e.g. "Detected 3s ago") counting up in real time.
- The label counts up second-by-second for 30 seconds, then disappears.
- While the ACL link is live, the existing "Detected" label is shown unchanged.
- The "Ns ago" label is shown for all devices but is practically only visible for blocked devices (non-blocked devices rarely drop ACL without a profile connection).
- No database changes — last-detected timestamps are held in memory only and reset when the app process is killed.

## Capabilities

### New Capabilities

- `detection-decay`: Tracks the timestamp of the most recent detection event per device and exposes how many seconds ago it occurred, decaying to nothing after 30 seconds.

### Modified Capabilities

- `device-presence`: The "Detected" display state gains an additional sub-state: recently detected (ACL dropped within the last 30 seconds), rendered as "Detected Ns ago".

## Impact

- `DeviceListViewModel.kt`: new in-memory timestamp map, transition tracking, 1 s decay ticker coroutine, `lastDetectedSecondsAgo: Int?` field on `DeviceUiModel`.
- `DeviceListScreen.kt`: new label branch in `DeviceRow` for the "Detected Ns ago" state.
- No Room schema changes. No new files. No new dependencies.
