## Why

After tapping "Allow temporarily" on the notification, the device list shows the device as "Detected" rather than reflecting its connected state — even though the device has fully connected. Restarting the app shows "Connected" correctly, confirming this is a stale-UI issue combined with a missing UI state. Two problems: the app doesn't listen for A2DP/HFP profile connection events (so the UI never updates when a temporarily-allowed headset connects), and there is no "Temporarily connected" label to communicate that the connection is transient.

## What Changes

- Add `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` and `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` to the Bluetooth broadcast receiver's `IntentFilter` so profile-level connection events trigger a UI refresh.
- Add `isTemporarilyAllowed: Boolean` to `DeviceUiModel` and populate it from `BlockedDeviceEntity` in `refreshDeviceList()`.
- In `DeviceRow`, show a **"Temporarily connected"** label (amber colour) when `isConnected && isTemporarilyAllowed`, in place of the plain "Connected" label.

## Capabilities

### New Capabilities

- `temp-allow-status`: UI state and label for temporarily-allowed devices, covering the `DeviceUiModel` field, ViewModel mapping, and the "Temporarily connected" label in `DeviceRow`.

### Modified Capabilities

- `connection-monitoring`: The requirement to observe Bluetooth connection events must be extended to include A2DP and HFP profile-level state changes, not just ACL events, so the device list updates when profile connections establish over an existing ACL link.

## Impact

- `DeviceListViewModel.kt`: `DeviceUiModel` gains a field; `refreshDeviceList()` gains a temporarily-allowed address set; the `IntentFilter` gains two profile broadcast actions.
- `DeviceListScreen.kt`: `DeviceRow` status label cascade gains a "Temporarily connected" branch.
- No new dependencies. No API or database schema changes.
