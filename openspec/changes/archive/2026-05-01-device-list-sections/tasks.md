## 1. ViewModel — Section assignment

- [x] 1.1 Add `DeviceSection` enum (`CONNECTED`, `DETECTED`, `BLOCKED`, `ALLOWED`) to `DeviceListViewModel.kt`
- [x] 1.2 Add `section: DeviceSection` field to `DeviceUiModel`
- [x] 1.3 Compute `section` in `refreshDeviceList()` using the priority rule: Connected > (isDetected || lastDetectedSecondsAgo != null) > Blocked > Allowed

## 2. ViewModel — Sort order

- [x] 2.1 Replace the existing `sortedWith` comparator with one that sorts by `section.ordinal` first, then `isDetected` descending (live before recently-detected), then name alphabetically
- [x] 2.2 Verify the decay ticker path also has the correct `section` value — since the ticker remaps existing `DeviceUiModel` entries via `device.copy(lastDetectedSecondsAgo = ...)`, confirm `section` is recomputed (or extracted to a helper) so a decaying device keeps `DETECTED` section assignment during ticks

## 3. UI — Section headers

- [x] 3.1 Add a `SectionHeader` composable in `DeviceListScreen.kt` that renders a section title (small uppercase label, `onSurfaceVariant` color, horizontal padding)
- [x] 3.2 Refactor `DeviceList` to iterate the sorted device list and emit a `SectionHeader` item (keyed `"header_${section.name}"`) before the first device in each new section
- [x] 3.3 Remove the `HorizontalDivider` between the last item of one section and the section header of the next — the header provides visual separation

## 4. UI — Label change

- [x] 4.1 In `DeviceRow`, replace `"Detected ${device.lastDetectedSecondsAgo}s ago"` with `"Detected recently"`

## 5. Verification

- [x] 5.1 Build the project and confirm no compile errors
- [ ] 5.2 On device: connect a Bluetooth device, confirm it appears under Connected section; disconnect, confirm it moves to Detected (if detected) or Allowed/Blocked
- [ ] 5.3 On device: trigger a detected→not-detected transition for a blocked device; confirm it shows "Detected recently" and stays in Detected section for ~30s, then moves to Blocked
- [ ] 5.4 Confirm sections with no devices are not rendered (no orphaned headers)
