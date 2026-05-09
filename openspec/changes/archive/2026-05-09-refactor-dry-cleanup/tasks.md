## 1. Semantic Color Constants (AppColors)

- [x] 1.1 Create `app/src/main/java/net/harveywilliams/bluetoothbouncer/ui/theme/AppColors.kt` with `ShizukuReady`, `DeviceConnected`, and `DeviceDetected` color constants
- [x] 1.2 Replace all raw `Color(0xFF4CAF50)` usages in `DeviceListScreen.kt` and `ShizukuSetupScreen.kt` with `AppColors.ShizukuReady`
- [x] 1.3 Replace all raw `Color(0xFF87CEEB)` usages in `DeviceListScreen.kt` with `AppColors.DeviceConnected`
- [x] 1.4 Replace all raw `Color(0xFFE8A06C)` usages in `DeviceListScreen.kt` with `AppColors.DeviceDetected`

## 2. UserService `forEachProfile` Helper

- [x] 2.1 Extract the `PROXY_TIMEOUT_SEC = 8L` inline literal to a named constant in `BluetoothBouncerUserService`
- [x] 2.2 Add a `data class ProfileEntry(val name: String, val future: CompletableFuture<*>)` inside `BluetoothBouncerUserService`
- [x] 2.3 Implement `private fun forEachProfile(macAddress: String, action: (proxy: Any, device: BluetoothDevice) -> Int): IntArray` — handles reinit, MAC→device resolution, per-proxy dispatch, and IntArray construction
- [x] 2.4 Rewrite `setConnectionPolicy` to delegate to `forEachProfile { proxy, device -> callSetConnectionPolicy(proxy, device, policy) }`
- [x] 2.5 Rewrite `connectDevice` to delegate to `forEachProfile { proxy, device -> callProfileMethod(proxy, device, "connect") }`
- [x] 2.6 Rewrite `disconnectDevice` to delegate to `forEachProfile { proxy, device -> callProfileMethod(proxy, device, "disconnect") }`
- [x] 2.7 Verify the three AIDL methods each retain their original log line (method name + mac + results)

## 3. ShizukuHelper `callService` Helper

- [x] 3.1 Implement `private suspend fun callService(operationName: String, call: (IBluetoothBouncerUserService) -> IntArray): Result<IntArray>` — handles `awaitServiceIfNeeded`, null check, results validation, and exception wrapping
- [x] 3.2 Rewrite `setConnectionPolicy` to delegate to `callService("setConnectionPolicy") { it.setConnectionPolicy(macAddress, policy) }`
- [x] 3.3 Rewrite `connectDevice` to delegate to `callService("connectDevice") { it.connectDevice(macAddress) }`
- [x] 3.4 Rewrite `disconnectDevice` to delegate to `callService("disconnectDevice") { it.disconnectDevice(macAddress) }`

## 4. ACL Reflection Dedup (BluetoothAclHelper)

- [x] 4.1 Create `app/src/main/java/net/harveywilliams/bluetoothbouncer/util/BluetoothAclHelper.kt` with `isConnected(device: BluetoothDevice): Boolean` using the hidden API reflection pattern
- [x] 4.2 Add `getConnectedAddresses(adapter: BluetoothAdapter): Set<String>` to `BluetoothAclHelper`
- [x] 4.3 Replace `DeviceListViewModel.getAclConnectedAddresses()` body to delegate to `BluetoothAclHelper.getConnectedAddresses(btAdapter!!)`
- [x] 4.4 Replace `DeviceWatcherService.isDeviceAclConnected()` body to resolve the `BluetoothDevice` from the adapter and delegate to `BluetoothAclHelper.isConnected(device)`

## 5. Section Classification Dedup

- [x] 5.1 Add a private `fun computeSection(isConnected: Boolean, isDetected: Boolean, isBlocked: Boolean, hasRecentDetection: Boolean): DeviceSection` function to `DeviceListViewModel`
- [x] 5.2 Replace the `when` expression in `refreshDeviceList()` (device construction loop) with a call to `computeSection`
- [x] 5.3 Replace the `when` expression in the decay ticker's `_uiState.update` lambda with a call to `computeSection`

## 6. Receiver `launchAsync` Extension

- [x] 6.1 Create `app/src/main/java/net/harveywilliams/bluetoothbouncer/receivers/ReceiverExtensions.kt` with `fun BroadcastReceiver.launchAsync(context: Context, block: suspend (app: BluetoothBouncerApp) -> Unit)`
- [x] 6.2 Refactor `TemporaryAllowReceiver.onReceive` to use `launchAsync`, removing the manual `goAsync()` + `CoroutineScope` + `pendingResult.finish()` boilerplate
- [x] 6.3 Refactor `DisconnectReceiver.onReceive` to use `launchAsync`
- [x] 6.4 Refactor `BootReceiver.onReceive` to use `launchAsync`
- [x] 6.5 Refactor `BondStateReceiver.onReceive` to use `launchAsync`

## 7. NearbyDeviceTracker Extraction

- [x] 7.1 Create `app/src/main/java/net/harveywilliams/bluetoothbouncer/service/NearbyDeviceTracker.kt` with `nearbyDevices: MutableStateFlow<Set<String>>`, `pendingRemovals`, `addDevice(mac)`, `scheduleRemoval(mac, deviceName)`, and `cancelPendingRemoval(mac)`
- [x] 7.2 Add `val nearbyTracker: NearbyDeviceTracker by lazy { NearbyDeviceTracker(applicationScope) }` to `BluetoothBouncerApp` and remove the migrated fields/methods (`nearbyDevices`, `pendingRemovals`, `scheduleNearbyRemoval`, `cancelPendingRemoval`)
- [x] 7.3 Update `BluetoothBouncerApp.launchNotificationObserver()` to reference `nearbyTracker.nearbyDevices`
- [x] 7.4 Update `DeviceWatcherService.onDeviceAppeared` to call `app.nearbyTracker.cancelPendingRemoval()` and `app.nearbyTracker.nearbyDevices.update { ... }` (or `app.nearbyTracker.addDevice()`)
- [x] 7.5 Update `DeviceWatcherService.onDeviceDisappeared` to call `app.nearbyTracker.scheduleRemoval()` and `app.nearbyTracker.nearbyDevices.update { ... }`

## 8. Build Verification

- [x] 8.1 Run `gradlew assembleDebug` and confirm `BUILD SUCCESSFUL` with zero compilation errors
- [x] 8.2 Install to device and verify: block a device, unblock, enable Alert, use Connect/Disconnect, and confirm notifications appear correctly
