## Context

Bluetooth Bouncer persists device blocks via `setConnectionPolicy(CONNECTION_POLICY_FORBIDDEN)` at the OS level. Blocked devices are tracked in a Room database (`blocked_devices` table). When a blocked device is physically nearby, the Bluetooth stack still establishes an ACL link for policy enforcement — this is already detected by the app via the hidden `BluetoothDevice.isConnected()` reflection call and shown as "Detected" in the UI.

The existing dynamic `BroadcastReceiver` (registered in `DeviceListViewModel`) catches `ACTION_ACL_CONNECTED` for UI refresh, but this receiver only works while the app is foregrounded. There is no mechanism to detect device presence or fire notifications when the app is not running.

The `CompanionDeviceManager` (CDM) API provides an OS-driven background presence detection system for associated Bluetooth devices. A `CompanionDeviceService` receives `onDeviceAppeared` / `onDeviceDisappeared` callbacks even when the app process is dead. This is the correct framework mechanism for this use case.

## Goals / Non-Goals

**Goals:**
- Allow users to opt specific blocked devices into background presence monitoring
- Notify the user (via system notification) when a watched blocked device comes into range
- Allow a single-tap "Allow temporarily" action from the notification, using existing Shizuku infrastructure
- Automatically re-block the device when it leaves range
- Clean up CDM associations automatically when a device is unblocked

**Non-Goals:**
- Support on API < 33 (presence observation requires API 33+; feature is hidden below this)
- Handling Shizuku not being available when the notification action fires (follow-up spec)
- Rate-limiting notifications for repeat device appearances (follow-up spec)
- Watching devices that are not blocked

## Decisions

### 1. CompanionDeviceManager over a foreground service

**Decision:** Use CDM `startObservingDevicePresence` + `CompanionDeviceService` for background detection.

**Rationale:** A foreground service would require a persistent notification and constant battery usage. CDM lets the OS handle presence detection efficiently and wakes the app only when the device appears. This is the framework-blessed solution for "wake me when this BT device is nearby."

**Alternative considered:** A foreground service with a dynamic `BroadcastReceiver`. Rejected — persistent notification is poor UX, and AGENTS.md explicitly notes "no foreground service needed."

---

### 2. Store CDM association state in `BlockedDeviceEntity`

**Decision:** Add `cdmAssociationId: Int?` and `isTemporarilyAllowed: Boolean` to the existing `blocked_devices` Room entity (schema migration v1 → v2).

**Rationale:** Watch state and temporary-allow state only exist while a device is blocked. Storing them on the same entity means they are automatically cleaned up when the device is unblocked (row deletion). A separate table would introduce join complexity with no benefit.

**Alternative considered:** A separate `WatchedDeviceEntity` table. Rejected — unnecessary complexity for two fields whose lifecycle is identical to `BlockedDeviceEntity`.

---

### 3. "Allow temporarily" action handled by a BroadcastReceiver

**Decision:** The notification "Allow temporarily" action uses a `PendingIntent` targeting a dedicated `BroadcastReceiver` (`TemporaryAllowReceiver`). This receiver calls Shizuku and updates Room.

**Rationale:** A `BroadcastReceiver` is lighter than a `Service` for a single fire-and-forget action. It can start a coroutine scope (`goAsync()`) long enough to call Shizuku (which has an 8s profile proxy wait) and update Room.

**Alternative considered:** Launching the app via `PendingIntent` to `MainActivity`. Rejected — requires the user to see and interact with the app, defeating the one-tap goal.

---

### 4. Re-block on `onDeviceDisappeared`, no grace period

**Decision:** Re-apply `CONNECTION_POLICY_FORBIDDEN` immediately in `onDeviceDisappeared` when `isTemporarilyAllowed == true`.

**Rationale:** The user explicitly blocked this device. A grace period would leave a window where the device could reconnect uninvited. The user can re-tap "Allow temporarily" if they need to reconnect immediately.

**Alternative considered:** A configurable grace period or a "keep allowed until I re-block" option. Deferred — the simpler behaviour ships first and is safer by default.

---

### 5. Watch toggle shown on all blocked devices, graceful failure if not nearby

**Decision:** The Watch toggle is always visible on blocked devices (API 33+). If CDM `associate()` fails or times out (device not nearby), show a Snackbar: "Device not found nearby. Try again when it's in Bluetooth range."

**Rationale:** Blocking and watching are independent user decisions. The user might block a device while it's present and want to enable watching later when it's gone. Hiding the toggle when the device is absent would be confusing and unpredictable.

**Alternative considered:** Only show Watch toggle when device has "Detected" status. Rejected — makes the feature harder to discover and doesn't handle the "set it up in advance" use case.

## Risks / Trade-offs

- **CDM association dialog UX** → The system `associate()` dialog searches for nearby BLE/BT devices. If the target device is not in range, the dialog may time out or show an empty picker. Mitigation: catch `ActivityResultCallback` failure/cancellation and show the Snackbar described in Decision 5.

- **`onDeviceDisappeared` latency** → CDM presence detection has platform-controlled latency (typically seconds to tens of seconds). Re-blocking may not be instantaneous after the device physically leaves range. Mitigation: acceptable — the device needs to be nearby and actively trying to connect for FORBIDDEN to matter.

- **Shizuku not running when notification fires** → If the user's phone reboots between blocking a device and the notification action, Shizuku may not be running. `TemporaryAllowReceiver` will fail to call `setConnectionPolicy`. Mitigation: deferred to follow-up spec; for now, surface an error notification: "Could not allow — Shizuku is not running."

- **Multiple profile ACL links** → `onDeviceDisappeared` may fire while the device is still connected at the profile level (e.g., HFP drops but A2DP stays). Re-blocking at this point would disrupt the active session. Mitigation: only re-block when `isTemporarilyAllowed == true` AND the device is not currently showing as profile-connected (check `BluetoothA2dp`/`BluetoothHeadset` state before calling FORBIDDEN).

- **API 33 minimum for presence observation** → Users on Android 12 (API 31-32, roughly 20-25% of active devices as of 2025) will not see the Watch toggle. Mitigation: feature is silently unavailable; no degraded experience, just no Watch option.

## Migration Plan

1. Room schema migration v1 → v2: add nullable `cdmAssociationId INTEGER` (default null) and `isTemporarilyAllowed INTEGER NOT NULL DEFAULT 0` to `blocked_devices`
2. Existing blocked device rows carry forward unchanged (both new columns use defaults)
3. No CDM registrations exist before this change — no migration of CDM state needed
4. No rollback complexity: removing CDM associations on downgrade is not required (the associations are per-app and will be orphaned but harmless)

## Open Questions

- Should `onDeviceAppeared` skip posting a notification if the device is already temporarily allowed (i.e., the user already allowed it via the app UI)? Likely yes — no action needed if it's already allowed.
- Does `CompanionDeviceService.onDeviceDisappeared` fire reliably on all OEM Android skins (Samsung One UI, MIUI, etc.)? May need investigation during implementation.
