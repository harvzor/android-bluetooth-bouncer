## 1. Extract shared constant

- [x] 1.1 Add `DETECTION_GRACE_PERIOD_MS = 30_000L` to `BluetoothBouncerApp.Companion`

## 2. DeviceWatcherService — grace period on disappear

- [x] 2.1 Add `private val pendingRemovals: MutableMap<String, Job> = mutableMapOf()` field to `DeviceWatcherService`
- [x] 2.2 In `onDeviceAppeared()`, before adding the MAC to `nearbyDevices`, cancel and remove any entry in `pendingRemovals` for that MAC
- [x] 2.3 In `onDeviceDisappeared()`, replace the immediate `nearbyDevices.update { it - entity.macAddress }` for non-temp-allowed devices with a delayed coroutine: launch in `serviceScope`, delay for `DETECTION_GRACE_PERIOD_MS`, then remove from `nearbyDevices` and evict from `pendingRemovals`
- [x] 2.4 Store the launched job in `pendingRemovals[entity.macAddress]`, cancelling any previously stored job for the same MAC first

## 3. DeviceListViewModel — replace magic number

- [x] 3.1 Replace the `30_000L` literal in `startDecayTickerIfNeeded()` with `BluetoothBouncerApp.DETECTION_GRACE_PERIOD_MS`

## 4. Build and verify

- [x] 4.1 Build the project (`assembleDebug`) and confirm no compile errors
- [x] 4.2 Verify: notification appears when device is detected and persists through the "Detected recently" window
- [x] 4.3 Verify: notification is NOT cancelled when CDM fires `onDeviceDisappeared` for a non-temp-allowed device — it should remain for ~30s
- [x] 4.4 Verify: if device reappears within the grace period, the notification is never interrupted
- [x] 4.5 Verify: temp-allowed device departure still immediately cancels the notification (grace period does not apply)
