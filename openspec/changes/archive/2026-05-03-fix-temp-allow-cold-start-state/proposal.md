## Why

When the app is force-closed and a user taps "Allow temporarily" from a notification, the app cold-starts to handle the action. Due to a profile proxy race and an overly broad ACL exclusion for blocked devices, the device incorrectly shows as "Detected" instead of "Temporarily connected" when the user then opens the app. This undermines trust in the UI — the user explicitly allowed the device and expects to see that reflected.

## What Changes

- **Trigger a device list refresh when profile proxies become available**: The `initProfileProxies()` callbacks (`onServiceConnected` for A2DP and Headset) currently set the proxy reference but never signal a refresh. On cold start the proxies arrive after the first `refreshDeviceList()` call, leaving stale state until an unrelated event triggers a refresh. Adding `refreshSignal.tryEmit(Unit)` in each `onServiceConnected` callback fixes this.
- **Exclude temporarily-allowed devices from the ACL blocked-address filter**: `connectedAddresses` is computed as `profileConnected + (aclConnected - blockedAddresses)`. This intentionally excludes blocked devices from ACL-based connection detection to avoid false positives from the Bluetooth stack maintaining an ACL link for policy enforcement. However, temporarily-allowed devices are legitimately connected and should not be filtered out. The filter is tightened to `blockedAddresses - temporarilyAllowedAddresses`, so temporarily-allowed devices are treated as connected even before profile proxies load.

## Capabilities

### New Capabilities

_(none — this is a bug fix to existing behaviour)_

### Modified Capabilities

- `connection-monitoring`: New requirements covering (1) refresh on profile proxy `onServiceConnected`, and (2) correct ACL-based connection detection for temporarily-allowed blocked devices.

## Impact

- `DeviceListViewModel.kt`: `initProfileProxies()` and `refreshDeviceList()` logic
- No UI changes, no Room schema changes, no new dependencies
