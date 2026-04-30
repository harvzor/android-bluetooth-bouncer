## Why

When a blocked device comes into range and attempts to connect, the UI gives no indication it is present — the device row is silent. Users have no way to know whether a blocked device is nearby or completely absent, reducing confidence that blocking is actually working.

## What Changes

- Add an `isDetected` state to the device model: `true` when a device has an active ACL link but no profile-level connection (covers both blocked devices that are nearby and paired devices that are present but not fully connected)
- Show a "Detected" label beneath the device name when `isDetected` is true and `isConnected` is false
- Sort the device list: Connected → Detected → Offline, then alphabetical within each group
- No change to the block/allow toggle or its labels

## Capabilities

### New Capabilities

- `device-presence`: Surfaces whether a paired device has an active ACL link, independently of whether it has an active profile connection

### Modified Capabilities

- `connection-monitoring`: The connection monitoring capability now tracks a second tier of presence (ACL-level detection) in addition to profile-level connection

## Impact

- `DeviceListViewModel.kt`: `DeviceUiModel` gains `isDetected: Boolean`; `refreshDeviceList()` computes `detectedAddresses`; sort order updated
- `DeviceListScreen.kt`: `DeviceRow` renders "Detected" label when `isDetected && !isConnected`
- No changes to Room DB, Shizuku, BroadcastReceivers, or any other layer
