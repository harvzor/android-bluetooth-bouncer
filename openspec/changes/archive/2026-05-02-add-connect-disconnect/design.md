## Context

Bluetooth Bouncer currently operates exclusively at the **policy level**: it sets `CONNECTION_POLICY_FORBIDDEN` or `CONNECTION_POLICY_ALLOWED` on Bluetooth profiles via the Shizuku UserService. There is no mechanism to initiate or terminate an active connection directly.

Two user-facing gaps exist:
1. Temporarily connecting a blocked device requires enabling Alert, waiting for a proximity notification, then tapping "Allow temporarily" — three steps minimum, and the device must be in range when Alert is first enabled (triggering a CDM association dialog).
2. Disconnecting a temporarily-allowed device requires going into Android Settings or toggling Block off and on.

The fix is a contextual Connect/Disconnect text button on each device row. The complexity is in the revert path for blocked-device Connect: the policy change must be undone when the device leaves range, even if the app is force-closed.

## Goals / Non-Goals

**Goals:**
- One-tap Connect on a blocked device: temporarily allow + CDM-backed auto-revert
- One-tap Connect on an allowed device: active profile connection (no policy change)
- One-tap Disconnect on any connected device: kicks the device off; re-blocks if it was blocked or temp-allowed
- CDM association survives Alert toggle OFF so future Connect taps don't re-trigger the system dialog
- `isAlertEnabled` field decouples notification preference from CDM association existence

**Non-Goals:**
- Connect/Disconnect on API < 33 (consistent with Alert being API 33+ only)
- Controlling which Bluetooth profiles are connected/disconnected individually
- Guaranteed prevention of Android auto-reconnect after Disconnect on an allowed device (accepted behaviour)
- Any UI change to the allowed-device auto-reconnect behaviour (no warning, no UI affordance)

## Decisions

### D1: CDM as the revert mechanism for blocked-device Connect

**Decision**: When Connect is tapped on a blocked device, associate via CDM (system dialog on first use) and rely on `DeviceWatcherService.onDeviceDisappeared` to re-apply `POLICY_FORBIDDEN`.

**Alternatives considered**:
- ACL disconnect broadcast (`ACTION_ACL_DISCONNECTED`) in `DeviceListViewModel`: simple, but the receiver is dynamically registered and dies with the app process. Force-close leaves the device in `POLICY_ALLOWED` indefinitely.
- Foreground service to keep the ACL listener alive: contradicts the app's design philosophy (policies persist at OS level, no foreground service needed) and adds visible overhead.

**Rationale**: CDM is system-managed and wakes `DeviceWatcherService` even when the app process is dead. The one-time system confirmation dialog is an acceptable trade-off for force-close resilience.

---

### D2: Persist CDM association across Alert toggle OFF

**Decision**: Toggling Alert OFF sets `isAlertEnabled = false` and calls `stopObservingDevicePresence()`, but does **not** call `disassociate()` or clear `cdmAssociationId`. The association is only cleaned up when the device is unblocked entirely.

**Alternatives considered**:
- Fully disassociate on Alert OFF (current behaviour): clean, but means the next Connect tap requires the system dialog again.

**Rationale**: The CDM association is cheap to keep and directly improves Connect UX. The user already granted it; there is no user-visible downside to retaining it silently.

**Consequence**: `DeviceWatchManager.disableWatch` is split into two methods:
- `disableWatch(mac, assocId)` — stops observation + clears `isAlertEnabled` only (used by Alert toggle OFF)
- `disableWatchAndDisassociate(mac, assocId)` — full cleanup including `disassociate()` + null `cdmAssociationId` (used by unblock)

---

### D3: isAlertEnabled as an explicit field

**Decision**: Add `isAlertEnabled: Boolean` to `BlockedDeviceEntity`. `DeviceUiModel.isWatched` is derived from this field, not from `cdmAssociationId != null`.

**Rationale**: `cdmAssociationId != null` previously served as a proxy for "user wants Alert notifications." With D2, a device can have a CDM association without wanting notifications (it used Connect but never enabled Alert). A dedicated field is the only clean solution.

**Migration**: Room migration 2→3. New column `isAlertEnabled INTEGER NOT NULL DEFAULT 0`. Backfill: `UPDATE blocked_devices SET isAlertEnabled = 1 WHERE cdmAssociationId IS NOT NULL`.

---

### D4: Connect/Disconnect via new Shizuku AIDL methods

**Decision**: Add `connectDevice(macAddress: String): IntArray` and `disconnectDevice(macAddress: String): IntArray` to `IBluetoothBouncerUserService`. The UserService implements them via reflection on `BluetoothA2dp.connect(device)`, `BluetoothHeadset.connect(device)`, and their disconnect counterparts.

**Rationale**: All privileged Bluetooth operations already go through the UserService. Adding connect/disconnect here is consistent and keeps the reflection complexity in one place. The `IntArray` return convention (one entry per profile: 1=success, 0=failure, -1=unavailable) already exists for `setConnectionPolicy`.

---

### D5: UI — text button inline with existing toggles

**Decision**: A `TextButton` labelled "Connect" or "Disconnect" appears to the left of the Alert and Block switches. Only one of the two is shown at a time; neither is shown when neither action is applicable (temp-allowed + disconnected, allowed + disconnected has Connect).

**Alternatives considered**:
- Bottom sheet on row tap: cleaner row, but adds discoverability friction and tap-target complexity with existing switches.
- Icon-only button: saves space but sacrifices clarity.

**Rationale**: Text button is the most immediately readable option. Since Connect and Disconnect are mutually exclusive, a single slot keeps the row width predictable.

## Risks / Trade-offs

**[Auto-reconnect after Disconnect on allowed devices]** → Android may immediately reconnect a device after Disconnect because `POLICY_ALLOWED` remains. Accepted; no mitigation. Document in README.

**[CDM system dialog on first Connect]** → Users may be confused by the system confirmation dialog when tapping Connect on a blocked device for the first time. Mitigation: the dialog text is system-generated and references the device name and app name, which is sufficient context. The dialog does not recur for subsequent Connect taps on the same device.

**[onDeviceDisappeared timing]** → CDM presence events may fire with a delay after the ACL link drops, leaving the device in `POLICY_ALLOWED` briefly. This is pre-existing behaviour (same for Alert-based temp-allow) and is not worsened by this change.

**[Migration backfill]** → Devices that had Alert enabled before the migration will have `isAlertEnabled = 1` and `cdmAssociationId` set. Devices that had Alert disabled will have `isAlertEnabled = 0` and `cdmAssociationId = null`. This is the correct post-migration state. No data loss.

## Migration Plan

1. Room migration `2 → 3`: add `isAlertEnabled INTEGER NOT NULL DEFAULT 0`, backfill from `cdmAssociationId IS NOT NULL`.
2. No server-side or multi-device migration concerns — purely local database.
3. Rollback: not applicable (local app; users can reinstall to reset state).
