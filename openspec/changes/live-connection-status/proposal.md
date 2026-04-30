## Why

The device list shows a "Connected" indicator per device, but this status is only read once when the list is built and never updated while the app is open. If a device connects or disconnects, the UI remains stale until the app is restarted. There are no BroadcastReceivers or callbacks listening for Bluetooth connection events, so the ViewModel has no way to know state changed.

## What Changes

- Register a lifecycle-aware BroadcastReceiver for `ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED` to detect real-time Bluetooth connection changes
- Register a lifecycle-aware BroadcastReceiver for `ACTION_STATE_CHANGED` to detect Bluetooth adapter on/off
- Trigger a device list refresh in `DeviceListViewModel` when any of these events fire
- Fix the coroutine leak in `refreshDevices()` where each call launches a new unbounded collector on the Room Flow
- Have `BondStateReceiver` trigger a device list refresh (in addition to re-applying block policy) so newly bonded devices appear immediately

## Capabilities

### New Capabilities
- `connection-monitoring`: Real-time observation of Bluetooth connection and adapter state changes, driving automatic UI refresh

### Modified Capabilities
- `device-blocking`: The "Show device connection status" requirement needs strengthening — the indicator must update in real time while the app is in the foreground, not only on list build

## Impact

- **ViewModel**: `DeviceListViewModel` — new event intake path for broadcast-driven refreshes; fix `refreshDevices()` coroutine leak
- **Receivers**: New lifecycle-aware receiver(s) for ACL and adapter state events; modification to `BondStateReceiver` to notify ViewModel
- **UI**: No visual changes — same "Connected" indicator, badge, and icon tint; they will simply update live
- **Dependencies**: No new libraries — uses standard Android `BroadcastReceiver` APIs
