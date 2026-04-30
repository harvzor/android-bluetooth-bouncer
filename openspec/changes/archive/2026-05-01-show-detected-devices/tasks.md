## 1. Data Model

- [x] 1.1 Add `isDetected: Boolean` field to `DeviceUiModel` in `DeviceListViewModel.kt`

## 2. State Computation

- [x] 2.1 In `refreshDeviceList()`, compute `detectedAddresses = aclConnected - connectedAddresses` after the existing `connectedAddresses` line
- [x] 2.2 Pass `isDetected = device.address in detectedAddresses` when constructing each `DeviceUiModel`
- [x] 2.3 Update the sort comparator to `compareByDescending { it.isConnected }.thenByDescending { it.isDetected }.thenBy { it.name }`

## 3. UI

- [x] 3.1 In `DeviceRow`, add an `else if (device.isDetected)` branch after the `isConnected` label block, rendering `"Detected"` in `MaterialTheme.colorScheme.onSurfaceVariant`

## 4. Verification

- [x] 4.1 Build and install on physical device; confirm "Detected" appears for a blocked device that is powered on and in range
- [x] 4.2 Confirm "Detected" disappears promptly when the blocked device powers off or goes out of range
- [x] 4.3 Confirm a connected (non-blocked) device shows only "Connected", not "Detected"
- [x] 4.4 Confirm an offline paired device shows neither label
- [x] 4.5 Confirm sort order: connected devices first, detected second, offline last
