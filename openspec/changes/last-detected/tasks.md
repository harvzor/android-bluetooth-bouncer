## 1. Extend DeviceUiModel

- [x] 1.1 Add `lastDetectedSecondsAgo: Int? = null` field to `DeviceUiModel` in `DeviceListViewModel.kt`

## 2. Add In-Memory Timestamp State to ViewModel

- [x] 2.1 Add `private val lastDetectedTimes: MutableMap<String, Long>` (address → `elapsedRealtime()`) as a private field in `DeviceListViewModel`
- [x] 2.2 Add `private var previousDetectedAddresses: Set<String> = emptySet()` to track the previous refresh's detected set
- [x] 2.3 Add `private var decayTickerJob: Job? = null` to hold the ticker coroutine reference

## 3. Transition Detection in refreshDeviceList()

- [x] 3.1 After computing `detectedAddresses`, diff against `previousDetectedAddresses` to find addresses that just left the detected set (`previousDetectedAddresses - detectedAddresses`)
- [x] 3.2 For each address in that "just left" set, stamp `lastDetectedTimes[address] = SystemClock.elapsedRealtime()`
- [x] 3.3 For each address in `detectedAddresses` that has an entry in `lastDetectedTimes`, remove it (device is live again)
- [x] 3.4 Update `previousDetectedAddresses = detectedAddresses` at the end of the diff step
- [x] 3.5 When computing each `DeviceUiModel`, calculate `lastDetectedSecondsAgo` from `lastDetectedTimes[address]` if present (using `((elapsedRealtime() - stamp) / 1000).toInt()`, capped at 29)
- [x] 3.6 Clear `lastDetectedTimes` and reset `previousDetectedAddresses` in the early-return path when Bluetooth is disabled

## 4. Implement Decay Ticker

- [x] 4.1 Add `startDecayTickerIfNeeded()` private function that returns early if `decayTickerJob?.isActive == true`
- [x] 4.2 Implement the ticker loop: `delay(1000)`, evict entries where `elapsedRealtime() - stamp >= 30_000`, remap `_uiState.value.devices` to recalculate `lastDetectedSecondsAgo` for each device, call `_uiState.update`, exit loop when `lastDetectedTimes.isEmpty()`
- [x] 4.3 Call `startDecayTickerIfNeeded()` from `refreshDeviceList()` after the device list is built, when `lastDetectedTimes.isNotEmpty()`

## 5. Update UI in DeviceListScreen

- [x] 5.1 In `DeviceRow`, add an `else if` branch after the existing `isDetected` branch: when `!device.isConnected && !device.isDetected && device.lastDetectedSecondsAgo != null`, show `"Detected ${device.lastDetectedSecondsAgo}s ago"` in `onSurfaceVariant` grey using `labelSmall` typography
