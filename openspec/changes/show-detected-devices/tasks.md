## 1. Data Model

- [ ] 1.1 Add `isDetected: Boolean` field to `DeviceUiModel` in `DeviceListViewModel.kt`

## 2. State Computation

- [ ] 2.1 In `refreshDeviceList()`, compute `detectedAddresses = aclConnected - connectedAddresses` after the existing `connectedAddresses` line
- [ ] 2.2 Pass `isDetected = device.address in detectedAddresses` when constructing each `DeviceUiModel`
- [ ] 2.3 Update the sort comparator to `compareByDescending { it.isConnected }.thenByDescending { it.isDetected }.thenBy { it.name }`

## 3. UI

- [ ] 3.1 In `DeviceRow`, add an `else if (device.isDetected)` branch after the `isConnected` label block, rendering `"Detected"` in `MaterialTheme.colorScheme.onSurfaceVariant`

## 4. Verification

- [ ] 4.1 Build and install on physical device; confirm "Detected" appears for a blocked device that is powered on and in range
- [ ] 4.2 Confirm "Detected" disappears promptly when the blocked device powers off or goes out of range
- [ ] 4.3 Confirm a connected (non-blocked) device shows only "Connected", not "Detected"
- [ ] 4.4 Confirm an offline paired device shows neither label
- [ ] 4.5 Confirm sort order: connected devices first, detected second, offline last
