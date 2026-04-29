## Context

This is a greenfield Android application. There is no existing codebase — we are building from scratch. The app ("Bluetooth Bouncer") gives users per-device control over Bluetooth auto-connection, a feature Android does not expose in its Settings UI.

The core OS mechanism (`setConnectionPolicy(CONNECTION_POLICY_FORBIDDEN)`) exists and works reliably, but is gated behind `BLUETOOTH_PRIVILEGED` — a signature-level permission unavailable to regular apps. Shizuku is a well-established Android tool that bridges this gap by running a process as the ADB shell user, which inherently holds this permission.

**Constraints:**
- Min SDK 31 (Android 12) — `BLUETOOTH_CONNECT` runtime permission model
- Target SDK 35
- Package: `net.harveywilliams.bluetoothbouncer`
- Shizuku is a hard requirement — the app cannot function without it

## Goals / Non-Goals

**Goals:**
- Allow users to block specific paired Bluetooth devices from auto-connecting
- Provide a clean, minimal UI for managing blocked devices
- Integrate with Shizuku for privileged Bluetooth API access
- Persist blocked-device preferences across app restarts
- Cover all relevant Bluetooth profiles (A2DP, HFP/Headset, HID Host)

**Non-Goals:**
- Bluetooth scanning or discovery
- Pairing/unpairing devices (users manage this in Android Settings)
- Functioning without Shizuku (no degraded/fallback mode)
- Background service or continuous monitoring — `setConnectionPolicy` is persistent at the OS level
- Play Store distribution (initially — may revisit later)
- Supporting Android versions below 12 (API 31)

## Decisions

### 1. Shizuku UserService over direct Binder wrapping

**Decision:** Use Shizuku's `UserService` API to run a service process as the shell UID, rather than wrapping system Binder interfaces directly.

**Rationale:** The UserService approach is simpler and more maintainable. We define an AIDL interface, implement it in a service class, and Shizuku manages the process lifecycle. The alternative (using `ShizukuBinderWrapper` to intercept IPC to `IBluetoothA2dp` etc.) requires reverse-engineering internal AIDL interfaces that change between Android versions.

**Alternative considered:** Direct `BluetoothAdapter.getProfileProxy()` calls from within the UserService. This is the approach we'll use *inside* the UserService — the UserService gets profile proxies and calls `setConnectionPolicy` using standard SDK APIs, but running as the shell UID so the permission check passes.

### 2. No foreground service needed

**Decision:** The app has no background service or foreground notification.

**Rationale:** `setConnectionPolicy(FORBIDDEN)` persists at the Bluetooth stack level across reboots. Once set, the OS enforces it without any app involvement. The app only needs to be open when the user wants to change a device's policy. This makes the app extremely lightweight.

**Edge case:** If a user unpairs and re-pairs a device, the policy resets. We handle this via a `BOOT_COMPLETED` + `BOND_STATE_CHANGED` receiver that re-applies policies, but only when Shizuku is running.

### 3. Room for persistence

**Decision:** Use Room database to store blocked device records (MAC address, device name, timestamp).

**Rationale:** Room provides type-safe queries, migration support, and Flow-based reactive queries that integrate cleanly with Compose. SharedPreferences/DataStore could work for a simple MAC-address list, but Room scales better if we add features (e.g., per-profile blocking, scheduling) and gives us proper relational structure.

**Alternative considered:** DataStore with a serialized list — simpler but less extensible.

### 4. Single-Activity Compose architecture

**Decision:** Single Activity with Jetpack Compose and Navigation Compose.

**Rationale:** Standard modern Android architecture. The app has only 2-3 screens (device list, Shizuku setup, possibly settings), so a single Activity with Compose navigation is the natural fit.

### 5. Bluetooth profiles to cover

**Decision:** Block across A2DP, Headset (HFP), and HID Host profiles.

**Rationale:** These are the profiles that cause unwanted auto-connections:
- **A2DP**: Audio streaming (speakers, headphones)
- **Headset/HFP**: Phone call audio (car kits, headsets)
- **HID Host**: Input devices (keyboards, mice)

Other profiles (PBAP, MAP, PAN) are less commonly problematic and can be added later.

## Risks / Trade-offs

- **Shizuku dependency** → The app is non-functional without Shizuku. Mitigation: Clear onboarding UI that guides setup, links to Shizuku app. Accept this as a deliberate trade-off for reliability.
- **Shizuku stops after reboot** → If Shizuku isn't set to auto-start, the boot receiver can't re-apply policies. Mitigation: The OS-level policies persist anyway — the boot receiver is only needed for the re-pair edge case. Document this clearly.
- **Android version fragmentation** → `setConnectionPolicy` behavior may vary across OEMs. Mitigation: Target API 31+ which has standardized Bluetooth permission model. Test on AOSP-based ROMs first.
- **Profile proxy availability** → Some devices may not support all profiles. Mitigation: Handle `onServiceDisconnected` and missing profiles gracefully — log and skip.
- **Shizuku API stability** → Shizuku is a third-party tool. Mitigation: It has been stable for years with a well-defined API. Pin dependency version.
