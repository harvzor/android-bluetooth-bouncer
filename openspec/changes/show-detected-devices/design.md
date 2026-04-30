## Context

The device list screen shows each paired Bluetooth device with a "Connected" indicator when the device has an active profile-level (A2DP or Headset) connection, or an ACL-level connection for non-profile devices (HID etc). Currently, when a device is blocked via `CONNECTION_POLICY_FORBIDDEN`, the OS prevents profile connections but may maintain an ACL link. The app already detects this ACL signal in `getAclConnectedAddresses()` — it explicitly subtracts blocked addresses from the connected set to avoid showing "Connected" for a blocked device. That signal is then discarded. This change preserves it as a separate `isDetected` state.

## Goals / Non-Goals

**Goals:**
- Surface ACL presence as a "Detected" label for any paired device that has an ACL link but no profile connection
- Covers both the primary case (blocked device nearby) and the general case (paired device present but not profile-connected)
- Sort detected devices between connected and offline in the list

**Non-Goals:**
- Active scanning for non-paired/non-bonded devices
- RSSI or proximity estimation
- Any changes to how blocking works
- Any changes to BroadcastReceivers, Room DB, Shizuku, or the boot/bond receivers

## Decisions

### Reuse `getAclConnectedAddresses()` without modification
The existing reflection-based method already returns all bonded devices with an active ACL link. No new detection mechanism is needed. `detectedAddresses` is simply derived as `aclConnected - connectedAddresses` — devices the stack knows about but that aren't profile-connected.

**Alternative considered:** Add a separate scan or RSSI-based presence check. Rejected — overkill, requires more permissions, and the ACL signal already captures exactly what we want.

### `isDetected` is a separate field, not a replacement for `isConnected`
`DeviceUiModel` gets an `isDetected: Boolean` field alongside the existing `isConnected`. The two are mutually exclusive by construction (`detectedAddresses = aclConnected - connectedAddresses`), but keeping them separate keeps the UI logic explicit and the model honest.

**Alternative considered:** A tri-state enum (`OFFLINE / DETECTED / CONNECTED`). Reasonable, but adds more churn across the UI layer for a small gain. The two booleans are easier to reason about in `if/else if` chains and the sort comparator.

### "Detected" label uses `onSurfaceVariant` color (same as MAC address)
"Detected" is passive information — it doesn't require action. Using the same muted gray as the MAC address keeps the visual hierarchy clean: green "Connected" stands out; gray "Detected" is present but undemanding.

### Sort order: Connected → Detected → Offline
Extends the existing `compareByDescending { it.isConnected }.thenBy { it.name }` comparator to `compareByDescending { it.isConnected }.thenByDescending { it.isDetected }.thenBy { it.name }`. Detected devices float above silent ones.

### No badge dot for detected devices
The green badge dot on the Bluetooth icon is exclusive to connected devices. Detected devices show only the text label — no icon change, no dot. Keeps the icon state unambiguous.

## Risks / Trade-offs

**ACL disconnect broadcast reliability** → The "Detected" label depends on `ACTION_ACL_DISCONNECTED` firing promptly when a device goes out of range. If the OS is slow or misses the broadcast (e.g., abrupt power-off), a stale "Detected" label could persist. Mitigation: the existing 300ms-debounced refresh fires on every ACL event, and the detection is re-evaluated from scratch each time. No persistent state is introduced. Worst case is a stale label until the next natural refresh.

**ACL link persistence for blocked devices** → The Bluetooth stack sometimes maintains an ACL link even after all profiles are disconnected under FORBIDDEN policy, for policy enforcement bookkeeping. This is actually the desired behaviour here — it's precisely what makes "Detected" work for blocked devices. No mitigation needed; this is load-bearing.

## Open Questions

None. Reliability of ACL disconnect broadcasts is an empirical question to confirm via manual testing on a physical device after implementation.
