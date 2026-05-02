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

> "Which paired Bluetooth device should I use for block/unblock testing? (e.g. 'Magical Music Box')"

This becomes `$TARGET_DEVICE` — the device name as it appears in the app's list.

---

## UI Dump Guidance

### Finding the block/allow switch for a device

The card layout in the XML is ordered as: device name → MAC → status label → Connect/Disconnect button → **Alert toggle** → **Block toggle**. The switch nodes appear *after* the Connect button in the XML, not immediately after the device name.

To reliably find the block toggle for `$TARGET_DEVICE`:
1. Find the index of `text="$TARGET_DEVICE"` in the XML
2. Take a substring from that position (~4000 chars) — 2000 chars is not enough; the Alert and Block toggles can appear well past that offset from the device name node
3. Find all `checkable="true"` nodes in that substring — the **last** one is the block toggle; the one before it is the Alert toggle
4. The block toggle label is `text="Blocked"` (when on) or `text="Allowed"` (when off), appearing just after it in the XML

The attribute order in the dump is `checkable="..." checked="..." ... bounds="..."` — regex must match this order.

### Asserting "not in CONNECTED section"

When asserting a device is no longer connected, first check whether `text="CONNECTED"` exists at all. If the CONNECTED section header is absent from the XML, that fully satisfies the assertion — there are no connected devices. Only parse the CONNECTED section contents if the header is present.

### Device scrolling off-screen after section changes

When a device moves between sections, it can end up outside the visible viewport and disappear from the UI dump. This can happen in either direction — a newly blocked device may move below the current view, or a reconnected/temporarily connected device may appear at the top in CONNECTED while the viewport is still scrolled down. If `text="$TARGET_DEVICE"` is not found in the dump immediately after a section change, scroll to top (`adb shell input swipe 540 400 540 1600 300`) and re-dump, then scroll down (`adb shell input swipe 540 1600 540 400 300`) if still not found, before declaring FAIL.

### Section name vs. visual truth

`uiautomator dump` can sometimes report section names that differ from what is visually shown on screen. Specifically:
- A **blocked device** appears in **BLOCKED** when it is out of Bluetooth scan range
- A **blocked device** appears in **DETECTED** (with its Blocked toggle still `checked="true"`) when it is within scan range

Both are valid blocked states. When asserting a device is blocked, check the **toggle state** (`checked="true"`) rather than relying solely on the section header name. If a section assertion fails unexpectedly, take a screenshot to confirm the ground truth.

### Polling loops

All polling loops must include `adb shell input keyevent 224` (KEYCODE_WAKEUP) on each iteration to prevent the phone screen from sleeping. Example:

```powershell
$timeout = 15; $elapsed = 0; $found = $false
while ($elapsed -lt $timeout) {
    adb -s $DEVICE shell input keyevent 224
    adb -s $DEVICE shell uiautomator dump /sdcard/ui.xml 2>$null
    adb -s $DEVICE pull /sdcard/ui.xml .adb-test-tmp/ui.xml 2>$null
    $content = Get-Content .adb-test-tmp/ui.xml -Raw
    if ($content -match 'text="Connected"') { $found = $true; break }
    Start-Sleep -Seconds 1; $elapsed++
}
```

---

## Phase 0: Clean Slate *(full mode only)*

> Ensure the app is uninstalled and all blocked devices are restored before reinstalling.

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 0.1 | Launch the existing app (if installed): `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` | — |
| 0.2 | Dump UI. For every device showing `checked="true"` (Blocked switch), tap the switch to unblock it | All devices show "Allowed" |
| 0.3 | Uninstall: `adb shell pm uninstall net.harveywilliams.bluetoothbouncer` | Exit code 0 |
| 0.3a | Clear residual app data (required on MIUI — `pm uninstall` does not delete app data due to system backup): `adb shell pm clear net.harveywilliams.bluetoothbouncer` | — (ignore "Unknown package" error if already uninstalled) |
| 0.4 | Build and install: `.\gradlew.bat installDebug` | Build succeeds, APK installed |

> **MIUI note:** On Xiaomi/MIUI devices, `pm uninstall` does not remove app data. Skipping step 0.3a will cause a Room database schema mismatch crash on first launch because the old database file persists across reinstalls.

---

## Phase 1: First Launch + Permissions *(full mode only)*

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 1.1 | Launch app: `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` | App opens |
| 1.2 | Wait 2s. Dump UI. Check for Bluetooth permission dialog | Permission dialog OR device list visible |
| 1.3 | Grant BLUETOOTH_CONNECT: `adb shell pm grant net.harveywilliams.bluetoothbouncer android.permission.BLUETOOTH_CONNECT` | — |
| 1.4 | Re-launch app: `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` (granting the permission may send the app to the background on some devices) | — |
| 1.5 | Wait 2s. Dump UI. Assert device list is visible (not stuck on permission screen) | `text="Bluetooth Bouncer"` visible, no `text="Bluetooth permission required"` |

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
| 2.7 | Wait 1s. Dump UI. Find and tap the "Allow all the time" button in the Shizuku permission dialog | Dialog dismissed |
| 2.8 | Wait 2s. Dump UI. Assert auto-navigated back to device list | `text="Bluetooth Bouncer"` visible |
| 2.9 | Assert Shizuku Ready | `text="Shizuku: Ready"` visible |

---

## Phase 3: Device List Smoke Tests

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 3.1 | Dump UI. Assert app title | `text="Bluetooth Bouncer"` present |
| 3.2 | Assert Shizuku Ready banner | `text="Shizuku: Ready"` present |
| 3.3 | Assert sections appear in correct order | If multiple sections present: CONNECTED before DETECTED before BLOCKED before ALLOWED (by character position in XML) |
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
| 4.1 | Dump UI. Find `$TARGET_DEVICE`. Assert it is in CONNECTED section | Device name visible between `text="CONNECTED"` and the next section header in XML, `text="Connected"` label present |
| 4.2 | Find and tap the block toggle for `$TARGET_DEVICE` (see UI Dump Guidance above) | — |
| 4.3 | Wait 2s. Take a screenshot to confirm. Dump UI. Assert device is blocked | Block toggle `checked="true"` for `$TARGET_DEVICE`; confirmed by screenshot showing orange Blocked toggle |
| 4.4 | Assert device is no longer in CONNECTED section | `text="Connected"` absent for `$TARGET_DEVICE` |
| 4.5 | Assert no error snackbar | No `text="Failed"` node present |
| 4.6 | Tap the block toggle for `$TARGET_DEVICE` to unblock | — |
| 4.7 | Wait 1s. Dump UI. Assert toggle shows Allowed | Block toggle `checked="false"` for `$TARGET_DEVICE` |
| 4.8 | Poll up to 15s (with KEYCODE_WAKEUP keepalive). Assert device reconnects | `text="Connected"` present for `$TARGET_DEVICE` in CONNECTED section |
| 4.9 | Assert no error snackbar | No `text="Failed"` node present |

> **Target device note:** Some Bluetooth devices (e.g. headphones) auto-power off after being disconnected for a period. If `$TARGET_DEVICE` powers off during the poll in step 4.8, turn it back on and re-poll.

---

## Phase 5: Persistence

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 5.1 | Tap the block toggle for `$TARGET_DEVICE` to block it | Block toggle `checked="true"`, device leaves CONNECTED section |
| 5.2 | Force-stop app: `adb shell am force-stop net.harveywilliams.bluetoothbouncer` | — |
| 5.3 | Relaunch: `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` | — |
| 5.4 | Wait 2s. Take a screenshot to confirm. Dump UI. Assert `$TARGET_DEVICE` is still blocked | Block toggle `checked="true"` for `$TARGET_DEVICE`; device is in BLOCKED or DETECTED section (both are valid — see UI Dump Guidance) |

> **Shizuku transient state after force-stop:** On some devices (notably MIUI), Shizuku may briefly show "Permission denied" immediately after force-stop + relaunch. This is transient — wait up to 5s and re-dump. If it resolves to "Shizuku: Ready" within that window, treat it as PASS. Only fail if it remains "Permission denied" after 5s.

---

## Phase 6: Connect/Disconnect (Blocked Device)

`$TARGET_DEVICE` should be blocked (from Phase 5). Turn it on if it has powered off.

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 6.1 | Dump UI. Find and tap the "Connect" button for `$TARGET_DEVICE`. The Connect button is a clickable container — find by `clickable="true"` node whose child has `text="Connect"`, near `$TARGET_DEVICE`'s position in the XML | — |
| 6.2 | Wait 1s. Dump UI. Check for CDM association dialog (system UI, different package). If present, find and tap the "Allow" button | Dialog dismissed |
| 6.3 | Poll up to 15s (with KEYCODE_WAKEUP keepalive). Assert "Temporarily connected" | `text="Temporarily connected"` visible for `$TARGET_DEVICE` |
| 6.4 | Assert "Disconnect" button visible | Clickable container with child `text="Disconnect"` near `$TARGET_DEVICE` |
| 6.5 | Tap "Disconnect" | — |
| 6.6 | Wait 3s. Take a screenshot to confirm. Dump UI. Assert device re-blocked | Block toggle `checked="true"` for `$TARGET_DEVICE`; device not in CONNECTED section |
| 6.7 | Tap "Connect" again | — |
| 6.8 | Wait 1s. Dump UI. Assert NO CDM dialog this time (association cached from step 6.2) | No system dialog visible |
| 6.9 | Poll up to 15s (with KEYCODE_WAKEUP keepalive). Assert "Temporarily connected" again | `text="Temporarily connected"` visible |
| 6.10 | Tap "Disconnect" | — |
| 6.11 | Assert no error snackbar throughout | No `text="Failed"` node at any point |

> **CDM dialog:** Step 6.2 involves a system dialog (CompanionDeviceManager association). It is findable via `uiautomator dump`. The confirm button text may be "Allow" or "Associate" — match broadly. If not found after 2s, pause and ask the user to tap it manually, then continue.
>
> **Target device note:** If `$TARGET_DEVICE` auto-powers off between steps (e.g. during the blocked period before 6.1), turn it back on before tapping Connect.

---

## Phase 7: Alert

`$TARGET_DEVICE` should still be blocked (from Phase 6).

> **Note:** The Alert toggle is only visible on API ≥ 33 (Android 13+). If the device under test is API 32 or below, skip this phase.

### ⏸ Checkpoint Before Step 7.2

**Ask the user:**

> "Turn OFF `$TARGET_DEVICE` now, then confirm. We need it off before enabling Alert so that turning it back on triggers the 'device appeared' notification."

Wait for confirmation before proceeding.

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 7.1 | Dump UI. Assert Alert toggle visible and unchecked for `$TARGET_DEVICE` | `checkable="true" checked="false"` node near `text="Alert"` label, within `$TARGET_DEVICE`'s card |
| 7.2 | Tap Alert toggle | — |
| 7.3 | Wait 1s. Dump UI. Check for `POST_NOTIFICATIONS` permission dialog | If `text="Allow"` and `text="Don't allow"` are visible in a system dialog → proceed to 7.4. If the dialog is absent and the Alert toggle is already `checked="true"` → skip to 7.5 (permission was pre-granted from a prior run — PASS) |
| 7.4 | If dialog present: tap "Allow" to grant notification permission | Dialog dismissed |
| 7.5 | Wait 1s. Dump UI. Assert Alert toggle is now checked | `checked="true"` on the Alert toggle for `$TARGET_DEVICE` (no CDM dialog expected — association reused from Phase 6) |
| 7.6 | Assert success snackbar | Node containing `"notification when this device is nearby"` visible |

### ⏸ Checkpoint Before Step 7.7

**Ask the user:**

> "Turn ON `$TARGET_DEVICE` now, then confirm. The app should receive a notification within a few seconds."

Wait for confirmation before proceeding.

| Step | Action | Pass Criteria |
|------|--------|---------------|
| 7.7 | Expand notification shade: `adb shell cmd statusbar expand-notifications` | — |
| 7.8 | Poll up to 15s (with KEYCODE_WAKEUP keepalive): dump UI, look for notification containing `$TARGET_DEVICE` name or `"Nearby"` | Notification node visible in shade |
| 7.9 | Find and tap the expand chevron on the notification (look for a clickable node with `content-desc` containing "expand" near the notification, or a chevron ImageButton at the notification's right edge) | Notification expanded |
| 7.10 | Dump UI. Assert "Allow temporarily" action button visible | `text="Allow temporarily"` present |
| 7.11 | Tap "Allow temporarily" | — |
| 7.12 | Collapse notification shade: `adb shell cmd statusbar collapse` | — |
| 7.13 | Bring app to foreground: `adb shell am start -n net.harveywilliams.bluetoothbouncer/.MainActivity` | — |
| 7.14 | Poll up to 10s (with KEYCODE_WAKEUP keepalive). Assert "Temporarily connected" | `text="Temporarily connected"` visible for `$TARGET_DEVICE` |
| 7.15 | Find and tap "Disconnect" for `$TARGET_DEVICE` | — |
| 7.16 | Wait 2s. Dump UI. Assert Alert toggle still checked (device re-blocked, Alert persists) | `checked="true"` on Alert toggle for `$TARGET_DEVICE` |
| 7.17 | Tap Alert toggle to disable | — |
| 7.18 | Wait 1s. Dump UI. Assert Alert toggle unchecked | `checked="false"` on Alert toggle for `$TARGET_DEVICE` |
| 7.19 | Assert no error snackbar throughout | No `text="Failed"` node at any point |

> **Notification expand chevron:** On standard Android the chevron has `content-desc="Expand"` or similar. Find it by searching for a clickable node at the right edge of the notification card (x-coordinate > 900 on a 1080px-wide display) within the notification's vertical bounds. If it cannot be found via dump, pause and ask the user to expand the notification manually, then continue from step 7.10.

---

## Phase 8: Cleanup

| Step | Action |
|------|--------|
| 8.1 | Restore `$TARGET_DEVICE` to its original state. Unblock it by tapping the block toggle (find via the BLOCKED section's raw XML — search between `text="BLOCKED"` and `text="ALLOWED"` for the `checked="true"` node with its bounds) |
| 8.2 | Delete `.adb-test-tmp/`: `Remove-Item -Recurse -Force .adb-test-tmp` |
| 8.3 | Clean up device: `adb shell rm -f /sdcard/ui.xml /sdcard/screen.png` |
| 8.4 | Report test summary — list each phase with PASS/FAIL and any notes |

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
| 7     | Alert                       | PASS   |
| 8     | Cleanup                     | PASS   |

All phases passed.
```

If any step fails, include the step number, what was expected, and what was actually found in the UI dump.

---

## Known Limitations

- **No CI support** — tests require a physical device with Shizuku running and a live Bluetooth device in range
- **Reconnection timing** — Bluetooth reconnection after unblock is async; polls up to 15s
- **CDM dialog** — Phase 6 step 6.2 involves system UI that may need manual intervention on first run
- **No testTag modifiers** — element finding relies on text content; if UI text changes, update this document
- **MIUI `pm uninstall` preserves app data** — always follow uninstall with `pm clear` (step 0.3a) to avoid Room schema mismatch crash
- **Blocked device section** — a blocked device appears in BLOCKED when out of scan range, DETECTED when in range; both are valid; assert on toggle state not section name
- **Device scrolls off-screen on section change** — when a device moves sections it may leave the visible viewport in either direction; scroll to top first, then down if needed, and re-dump before declaring FAIL
- **Toggle slice size** — always use a ~4000-char substring from the device name node when searching for checkable toggles; 2000 chars is too short and will miss the block toggle
- **CONNECTED section may be absent** — if no devices are connected the CONNECTED header is not rendered; a missing header satisfies any "not in CONNECTED section" assertion
- **Shizuku transient "Permission denied" after force-stop** — on MIUI, Shizuku briefly shows Permission denied after a force-stop/relaunch cycle; wait up to 5s for it to recover to Ready before failing
- **Target device auto-off** — headphones and similar devices power off after being blocked/disconnected for a period; turn them back on if they sleep during a test phase
- **Alert — API 33+ only** — Phase 7 is skipped on API 32 and below; the Alert toggle is not rendered
- **Alert — POST_NOTIFICATIONS pre-granted** — in quick mode the notification permission is likely already granted; if no dialog appears at step 7.3 and the toggle is already checked, skip 7.3–7.4
- **Alert — notification chevron** — the "Allow temporarily" action button is only visible after expanding the notification; tap the expand chevron before asserting the action button
- **Bumble** — future automated Bluetooth device simulation; see `TESTING_WITH_BUMBLE.md`
