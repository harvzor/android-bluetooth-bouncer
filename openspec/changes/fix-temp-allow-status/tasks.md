## 1. ViewModel — Profile Broadcast Reactivity

- [ ] 1.1 In `DeviceListViewModel.registerBluetoothEventReceiver()`, add `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` and `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` to the `IntentFilter`

## 2. ViewModel — DeviceUiModel and Mapping

- [ ] 2.1 Add `val isTemporarilyAllowed: Boolean = false` field to `DeviceUiModel`
- [ ] 2.2 In `refreshDeviceList()`, build `temporarilyAllowedAddresses: Set<String>` from `blockedDevices.filter { it.isTemporarilyAllowed }.map { it.macAddress }.toSet()`
- [ ] 2.3 Pass `isTemporarilyAllowed = device.address in temporarilyAllowedAddresses` in each `DeviceUiModel(...)` constructor call

## 3. UI — "Temporarily connected" Label

- [ ] 3.1 In `DeviceRow`, add a branch before the existing `isConnected` check: when `device.isConnected && device.isTemporarilyAllowed`, show `Text("Temporarily connected")` with colour `Color(0xFFFF9800)` (amber)
- [ ] 3.2 Verify the existing `isConnected` branch is unchanged (still shows "Connected" in green for non-temporary connections)
