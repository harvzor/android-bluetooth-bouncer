## Why

The device list UI uses an inconsistent and semantically confusing color scheme: a green dot badge overlays the Bluetooth icon for connected devices (redundant with the icon tint), "Connected" text is green (conflicting with the Detected state which should read as active/present), and colors are wallpaper-derived via dynamic theming rather than stable across devices.

## What Changes

- Remove the green `Badge` dot overlay from the Bluetooth icon on connected devices
- Tint the Bluetooth icon with the official Bluetooth brand blue (`#0082FC`) when a device is connected
- Change "Connected" and "Temporarily connected" status text from green/orange to Bluetooth blue (`#0082FC`)
- Change "Detected" and "Detected Xs ago" status text from `onSurfaceVariant` (wallpaper-derived gray) to a hardcoded salmon-orange (`#E8A06C`)
- All state colors become hardcoded constants — no longer derived from the dynamic theme

## Capabilities

### New Capabilities

- `device-list-visual-states`: Visual color treatment for device connection and detection states in the device list UI

### Modified Capabilities

<!-- No existing spec-level behavior is changing — this is a pure UI presentation change with no effect on connection logic, detection logic, or blocking behavior -->

## Impact

- `app/src/main/java/net/harveywilliams/bluetoothbouncer/ui/devices/DeviceListScreen.kt` — only file changed
- No logic, state, or API changes
- No new dependencies
