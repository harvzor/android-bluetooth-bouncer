## 1. Emit refresh signal when profile proxies connect

- [x] 1.1 In `DeviceListViewModel.initProfileProxies()`, add `getApplication<BluetoothBouncerApp>().refreshSignal.tryEmit(Unit)` at the end of the A2DP `onServiceConnected` callback, after `a2dpProxy = proxy as? BluetoothA2dp`
- [x] 1.2 In `DeviceListViewModel.initProfileProxies()`, add `getApplication<BluetoothBouncerApp>().refreshSignal.tryEmit(Unit)` at the end of the Headset `onServiceConnected` callback, after `headsetProxy = proxy as? BluetoothHeadset`

## 2. Exclude temporarily-allowed devices from ACL blocked filter

- [x] 2.1 In `DeviceListViewModel.refreshDeviceList()`, change the `connectedAddresses` computation from `profileConnected + (aclConnected - blockedAddresses)` to `profileConnected + (aclConnected - (blockedAddresses - temporarilyAllowedAddresses))`

## 3. Verify

- [x] 3.1 Build the project with `gradlew.bat assembleDebug` and confirm zero compilation errors
- [x] 3.2 On a physical device with Shizuku running: enable Alert for a blocked device, force-close the app, power on the Bluetooth device, tap "Allow temporarily" on the notification, then open the app — confirm the device appears as "Temporarily connected" (not "Detected")
- [x] 3.3 Confirm that a blocked (non-temporarily-allowed) device with an ACL link still appears as "Detected" (not "Connected"), verifying no regression in the false-positive protection
