---
name: android-adb-testing
description: Drive end-to-end tests on a physical Android device via ADB shell commands, uiautomator UI hierarchy dumps, coordinate taps, and screenshots. Use this skill whenever the user wants to run tests, verify app behavior, check UI state, or do any kind of manual/agentic QA on a connected Android device -- even if they just say "let's test" or "run the tests" or "check if the app works". Also use it for any ADB-driven interaction like dumping the UI, tapping elements, granting permissions, or polling for state changes on a real device.
---

# Android ADB Testing Skill

You are driving a real Android app on a real physical device via ADB shell commands. No test framework, no mocks — just ADB, UI hierarchy dumps, taps, and screenshots.

---

## Setup

### Temp Directory

All temporary files (screenshots, UI dumps) MUST go in `.adb-test-tmp/` relative to the project root. Never write to the repo root.

```powershell
New-Item -ItemType Directory -Force -Path ".adb-test-tmp"
```

Clean up at the end of every test run:

```powershell
Remove-Item -Recurse -Force ".adb-test-tmp"
```

---

## Device Detection

### Auto-detect the physical device

```powershell
$devices = adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] }
$physical = $devices | Where-Object { $_ -notmatch "emulator" }
```

- If exactly one physical device → use it, announce: "Using device: `<serial>`"
- If zero physical devices → stop, tell the user to connect a device via USB
- If more than one physical device → ask the user which serial to use

Assign to `$DEVICE` and use `-s $DEVICE` on every subsequent ADB command.

---

## UI Hierarchy

### Dump and pull

```powershell
adb -s $DEVICE shell uiautomator dump /sdcard/ui.xml
adb -s $DEVICE pull /sdcard/ui.xml .adb-test-tmp/ui.xml
```

### Parse bounds to get tap coordinates

Bounds appear in the XML as `bounds="[x1,y1][x2,y2]"`. The tap target is the centre:

```
x = (x1 + x2) / 2
y = (y1 + y2) / 2
```

Use `Select-String` or `Grep` to find the relevant node. Example — find a Switch node near the text "Blocked":

```powershell
$content = Get-Content .adb-test-tmp/ui.xml -Raw
# Find the bounds of a checkable node after the "BlackShark" text node
```

Because the XML is one long line, search for the element by its text or neighbouring text, then extract the `bounds` attribute of the nearest `checkable="true"` node.

### Finding elements reliably

Prefer locating elements by **unique text content** first:
- `text="Bluetooth Bouncer"` — app title
- `text="Shizuku: Ready"` — status banner
- `text="BLOCKED"`, `text="CONNECTED"` etc. — section headers
- `text="BlackShark V3 X BT"` — device name (use the parameterised device name)

For interactive elements (switches, buttons) that share labels (e.g. multiple "Allowed" switches), find the **device name node first**, then find the nearest checkable/clickable node after it in the XML.

---

## Tapping

```powershell
adb -s $DEVICE shell input tap $x $y
```

After any tap that should trigger a state change, wait briefly before re-dumping:

```powershell
Start-Sleep -Seconds 1
```

---

## Screenshots

```powershell
adb -s $DEVICE shell screencap -p /sdcard/screen.png
adb -s $DEVICE pull /sdcard/screen.png .adb-test-tmp/screen.png
```

Use the Read tool to view the screenshot image. Take a screenshot after every significant state change to visually confirm the result.

---

## Polling for State Changes

Some state changes are async (e.g. a Bluetooth device reconnecting after unblock). Poll by re-dumping the UI hierarchy until the expected text/node appears, with a timeout:

```powershell
$timeout = 10  # seconds
$elapsed = 0
$found = $false

while ($elapsed -lt $timeout) {
    adb -s $DEVICE shell uiautomator dump /sdcard/ui.xml 2>$null
    adb -s $DEVICE pull /sdcard/ui.xml .adb-test-tmp/ui.xml 2>$null
    $content = Get-Content .adb-test-tmp/ui.xml -Raw
    if ($content -match "Connected") {
        $found = $true
        break
    }
    Start-Sleep -Seconds 1
    $elapsed++
}

if (-not $found) { Write-Host "FAIL: timed out waiting for Connected state" }
```

---

## App Lifecycle

### Launch app
```powershell
adb -s $DEVICE shell am start -n <package>/<activity>
Start-Sleep -Seconds 2  # allow app to render
```

### Force-stop app
```powershell
adb -s $DEVICE shell am force-stop <package>
```

### Uninstall app
```powershell
adb -s $DEVICE shell pm uninstall <package>
```

### Install APK
```powershell
# Build first
$env:ANDROID_HOME = "<sdk-path>"
.\gradlew.bat installDebug
# Or install a pre-built APK directly:
adb -s $DEVICE install -r path\to\app.apk
```

---

## Granting Permissions

Grant a runtime permission without a dialog:

```powershell
adb -s $DEVICE shell pm grant <package> android.permission.BLUETOOTH_CONNECT
```

Check if a permission is granted:

```powershell
adb -s $DEVICE shell dumpsys package <package> | Select-String "BLUETOOTH_CONNECT"
```

---

## Asserting State

Every assertion should:
1. Dump the UI hierarchy (or use the last dump if taken within 1s)
2. Check for presence/absence of expected text or node attributes
3. Report **PASS** or **FAIL** clearly

```powershell
$content = Get-Content .adb-test-tmp/ui.xml -Raw

# Assert text is present
if ($content -match 'text="Shizuku: Ready"') {
    Write-Host "PASS: Shizuku: Ready visible"
} else {
    Write-Host "FAIL: Shizuku: Ready not found"
}

# Assert text is absent
if ($content -notmatch 'text="Failed') {
    Write-Host "PASS: No error snackbar"
} else {
    Write-Host "FAIL: Error snackbar present"
}

# Assert a switch is checked (blocked)
if ($content -match 'checkable="true" checked="true"') {
    Write-Host "PASS: Switch is checked (Blocked)"
}
```

---

## Scrolling

If the target device is not visible in the current viewport, scroll down:

```powershell
adb -s $DEVICE shell input swipe 540 1800 540 600 300
```

Then re-dump the UI hierarchy.

---

## Cleanup

At the end of every test run (pass or fail):

```powershell
Remove-Item -Recurse -Force ".adb-test-tmp" -ErrorAction SilentlyContinue
adb -s $DEVICE shell rm -f /sdcard/ui.xml /sdcard/screen.png
```
