# Bluetooth Bouncer — E2E Test Plan

This document is the test plan for the agentic E2E test runner. The agent follows this document top-to-bottom, pausing at checkpoints and reporting PASS/FAIL for each step.

---

## Prerequisites

Before starting any test run:

- Phone connected via USB with ADB authorized (`adb devices` shows the device)
- Shizuku installed on the phone
- Shizuku running (started via Wireless Debugging or ADB)
- At least one Bluetooth device paired to the phone
- `ANDROID_HOME` set if a build is needed
- JDK 17 on PATH if a build is needed

**App details:**
- Package: `net.harveywilliams.bluetoothbouncer`
- Main activity: `.MainActivity`
- Device serial: auto-detected (see below)

---

## Device Detection

Run `adb devices` and select the physical device:

- **One physical device + emulator(s)** → auto-select the physical device, announce: "Using device: `<serial>`"
- **One physical device, no emulator** → auto-select
- **Multiple physical devices** → ask the user which serial to use
- **No physical devices** → stop and tell the user to connect the phone via USB

---

## Full vs. Quick Mode

At the start of every test run, ask:

> "Full test (reinstall app, test first-launch flow — Phases 0–2 included) or quick test (skip to Phase 3, app already installed and configured)?"

- **Full** → run all phases 0–7
- **Quick** → skip Phases 0, 1, and 2, start at Phase 3

---

## Test Parameters

Ask the user (or confirm from context):

> "Which paired Bluetooth device should I use for block/unblock testing? (e.g. 'BlackShark V3 X BT')"

This becomes `$TARGET_DEVICE` — the device name as it appears in the app's list.

---

## Phase 0: Clean Slate *(full mode only)*

> Ensure the app is uninstalled and all blocked devices are restored before reinstalling.

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 0.1 | Launch the existing app (if installed): `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` | — |
| 0.2 | Dump UI. For every device showing `checked="true"` (Blocked switch), tap the switch to unblock it | All devices show "Allowed" |
| 0.3 | Uninstall: `adb shell pm uninstall net.harveywilliams.bluetoothbouncer` | Exit code 0 |
| 0.4 | Build and install: `.\gradlew.bat installDebug` | Build succeeds, APK installed |

---

## Phase 1: First Launch + Permissions *(full mode only)*

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 1.1 | Launch app: `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` | App opens |
| 1.2 | Wait 2s. Dump UI. Check for Bluetooth permission dialog | Permission dialog OR device list visible |
| 1.3 | Grant BLUETOOTH_CONNECT: `adb shell pm grant net.harveywilliams.bluetoothbouncer android.permission.BLUETOOTH_CONNECT` | — |
| 1.4 | Wait 1s. Dump UI. Assert device list is visible (not stuck on permission screen) | `text="Bluetooth Bouncer"` visible, no `text="Bluetooth permission required"` |

---

## Phase 2: Shizuku Setup *(full mode only)*

After a fresh install, Shizuku permission has not been granted to the new app instance.

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 2.1 | Dump UI. Assert Shizuku banner is NOT "Ready" | `text="Shizuku: Ready"` absent |
| 2.2 | Assert "Setup" button visible | `text="Setup"` present |
| 2.3 | Tap "Setup" button | — |
| 2.4 | Wait 1s. Dump UI. Assert Shizuku Setup screen | `text="Shizuku Setup"` visible |
| 2.5 | Assert "Permission Required" state card | `text="Shizuku: Permission Required"` visible |
| 2.6 | Tap "Grant Shizuku Permission" button | — |
| 2.7 | Wait for Shizuku permission dialog. Dump UI. Find and tap the "Allow" button in the dialog | Dialog dismissed |
| 2.8 | Wait 2s. Dump UI. Assert auto-navigated back to device list | `text="Bluetooth Bouncer"` visible |
| 2.9 | Assert Shizuku Ready | `text="Shizuku: Ready"` visible |

---

## Phase 3: Device List Smoke Tests

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 3.1 | Dump UI. Assert app title | `text="Bluetooth Bouncer"` present |
| 3.2 | Assert Shizuku Ready banner | `text="Shizuku: Ready"` present |
| 3.3 | Assert sections appear in correct order | If multiple sections present: CONNECTED before DETECTED before BLOCKED before ALLOWED (by vertical position in XML) |
| 3.4 | Assert at least one device with a MAC address | At least one node matching `[0-9A-F]{2}:[0-9A-F]{2}:` visible |
| 3.5 | Assert connected devices show correct labels | Any device in CONNECTED section has `text="Connected"` and a node with `text="Disconnect"` |

---

## ⏸ Checkpoint Before Phase 4

**Ask the user:**

> "Phase 4 requires `$TARGET_DEVICE` to be turned on and within Bluetooth range so it auto-connects when unblocked. Is it on and connected? Ready to continue?"

Wait for confirmation before proceeding.

---

## Phase 4: Block/Unblock

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 4.1 | Dump UI. Find `$TARGET_DEVICE`. Assert it is in CONNECTED section | Device name visible, `text="Connected"` label present |
| 4.2 | Find and tap the "Allowed" switch for `$TARGET_DEVICE` | — |
| 4.3 | Wait 2s. Dump UI. Assert switch is now Blocked | `checked="true"` on the switch node for `$TARGET_DEVICE` |
| 4.4 | Assert device is no longer in CONNECTED section | `text="Connected"` absent for `$TARGET_DEVICE` |
| 4.5 | Assert no error snackbar | No `text="Failed"` node present |
| 4.6 | Tap the "Blocked" switch for `$TARGET_DEVICE` to unblock | — |
| 4.7 | Wait 1s. Dump UI. Assert switch shows Allowed | `checked="false"` on the switch node for `$TARGET_DEVICE` |
| 4.8 | Poll up to 10s. Assert device reconnects | `text="Connected"` present for `$TARGET_DEVICE` |
| 4.9 | Assert no error snackbar | No `text="Failed"` node present |

---

## Phase 5: Persistence

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 5.1 | Tap the "Allowed" switch for `$TARGET_DEVICE` to block it | `checked="true"` on switch, device leaves CONNECTED |
| 5.2 | Force-stop app: `adb shell am force-stop net.harveywilliams.bluetoothbouncer` | — |
| 5.3 | Relaunch: `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` | — |
| 5.4 | Wait 2s. Dump UI. Assert `$TARGET_DEVICE` is still in BLOCKED section | Device name visible under `text="BLOCKED"` header, switch `checked="true"` |

---

## Phase 6: Connect/Disconnect (Blocked Device)

`$TARGET_DEVICE` should be blocked (from Phase 5).

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 6.1 | Dump UI. Find and tap "Connect" button for `$TARGET_DEVICE` | — |
| 6.2 | Wait 1s. Dump UI. Check for CDM association dialog (system UI, different package). If present, find and tap the confirm/associate button | Dialog dismissed |
| 6.3 | Poll up to 10s. Assert "Temporarily connected" | `text="Temporarily connected"` visible for `$TARGET_DEVICE` |
| 6.4 | Assert "Disconnect" button visible | `text="Disconnect"` present for `$TARGET_DEVICE` |
| 6.5 | Tap "Disconnect" | — |
| 6.6 | Wait 3s. Dump UI. Assert device re-blocked | Switch `checked="true"`, device no longer in CONNECTED |
| 6.7 | Tap "Connect" again | — |
| 6.8 | Wait 1s. Dump UI. Assert NO CDM dialog this time (association cached from step 6.2) | No system dialog visible |
| 6.9 | Poll up to 10s. Assert "Temporarily connected" again | `text="Temporarily connected"` visible |
| 6.10 | Tap "Disconnect" | — |
| 6.11 | Assert no error snackbar throughout | No `text="Failed"` node at any point |

> **Known limitation:** Step 6.2 involves a system dialog (CompanionDeviceManager association). It should be findable via `uiautomator dump` as it is standard Android UI, but this has not been fully validated. If the dialog cannot be found/tapped, pause and ask the user to tap it manually, then continue.

---

## Phase 7: Cleanup

| Step | Action |
|------|--------|
| 7.1 | Restore `$TARGET_DEVICE` to its original state (blocked if it was blocked before Phase 0, allowed otherwise) |
| 7.2 | Delete `.adb-test-tmp/`: `Remove-Item -Recurse -Force .adb-test-tmp` |
| 7.3 | Clean up device: `adb shell rm -f /sdcard/ui.xml /sdcard/screen.png` |
| 7.4 | Report test summary — list each phase with PASS/FAIL and any notes |

---

## Test Summary Format

At the end of each run, report:

```
## Test Run Summary

Device:   <serial> (<model>)
Mode:     Full / Quick
Target:   <TARGET_DEVICE>

| Phase | Name                        | Result |
|-------|-----------------------------|--------|
| 0     | Clean Slate                 | PASS   |
| 1     | First Launch + Permissions  | PASS   |
| 2     | Shizuku Setup               | PASS   |
| 3     | Device List Smoke Tests     | PASS   |
| 4     | Block/Unblock               | PASS   |
| 5     | Persistence                 | PASS   |
| 6     | Connect/Disconnect          | PASS   |
| 7     | Cleanup                     | PASS   |

All phases passed.
```

If any step fails, include the step number, what was expected, and what was actually found in the UI dump.

---

## Known Limitations

- **No CI support** — tests require a physical device with Shizuku running and a live Bluetooth device in range
- **Reconnection timing** — Bluetooth reconnection after unblock is async; polls up to 10s
- **CDM dialog** — Phase 6 step 6.2 involves system UI that may need manual intervention on first run
- **No testTag modifiers** — element finding relies on text content; if UI text changes, update this document
- **Bumble** — future automated Bluetooth device simulation; see `TESTING_WITH_BUMBLE.md`
